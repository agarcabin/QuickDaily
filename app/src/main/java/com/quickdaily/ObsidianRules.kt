package com.quickdaily

import kotlinx.serialization.json.*
import java.net.URLDecoder
import java.net.URLEncoder

/** Paths here are vault-relative logical paths, never filesystem or SAF identities. */
data class ObsidianRules(
    val diaryFolder: String = "",
    val dateFormat: String = "YYYY-MM-DD",
    val templatePath: String = "",
    val attachmentFolder: String = "/",
    val markdownLinks: Boolean = false,
    val linkPathFormat: String = "shortest",
)

internal data class ObsidianRulesRead(val rules: ObsidianRules, val invalidFields: List<String> = emptyList())

internal object ObsidianSyncPolicy {
    fun merge(previous: ObsidianSyncState, dailyRaw: String?, appRaw: String?,
        dailySource: String, appSource: String, dailyFailure: String = "unavailable", appFailure: String = "unavailable"): ObsidianSyncState {
        var result = previous
        result = if (dailyRaw != null) runCatching { ObsidianRulesParser.daily(dailyRaw, result.rules) }.fold(
            { result.copy(rules = it.rules, dailyLoaded = true, dailyStatus = if (it.invalidFields.isEmpty()) "ok" else "invalid_fields", dailySource = dailySource) },
            { result.copy(dailyStatus = "invalid_json", dailySource = dailySource) })
        else result.copy(dailyStatus = dailyFailure, dailySource = dailySource)
        result = if (appRaw != null) runCatching { ObsidianRulesParser.app(appRaw, result.rules) }.fold(
            { result.copy(rules = it.rules, appLoaded = true, appStatus = if (it.invalidFields.isEmpty()) "ok" else "invalid_fields", appSource = appSource) },
            { result.copy(appStatus = "invalid_json", appSource = appSource) })
        else result.copy(appStatus = appFailure, appSource = appSource)
        return result
    }
}

internal object ObsidianRulesParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun daily(raw: String, previous: ObsidianRules): ObsidianRulesRead = parse(raw, previous, true)
    fun app(raw: String, previous: ObsidianRules): ObsidianRulesRead = parse(raw, previous, false)

    private fun parse(raw: String, previous: ObsidianRules, daily: Boolean): ObsidianRulesRead {
        val obj = json.parseToJsonElement(raw) as? JsonObject ?: error("config_not_object")
        val invalid = mutableListOf<String>()
        fun text(key: String, default: String, old: String, valid: (String) -> Boolean = { true }): String {
            if (!obj.containsKey(key)) return default
            val value = (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
            if (value != null && valid(value)) return value
            invalid += key
            return old
        }
        val rules = if (daily) {
            previous.copy(
                diaryFolder = text("folder", "", previous.diaryFolder) { ObsidianLinkPolicy.normalize(it.trim('/')) != null },
                dateFormat = text("format", "YYYY-MM-DD", previous.dateFormat) { it.isNotBlank() },
                templatePath = text("template", "", previous.templatePath) { ObsidianLinkPolicy.normalize(it) != null }
                    .let { if (it.isNotEmpty() && !it.endsWith(".md", true)) "$it.md" else it },
            )
        } else {
            val markdown = if (!obj.containsKey("useMarkdownLinks")) false else {
                (obj["useMarkdownLinks"] as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull
                    ?: previous.markdownLinks.also { invalid += "useMarkdownLinks" }
            }
            previous.copy(
                attachmentFolder = text("attachmentFolderPath", "/", previous.attachmentFolder) {
                    it == "." || it == "/" || ObsidianLinkPolicy.normalize(it.removePrefix("./").trim('/')) != null
                },
                markdownLinks = markdown,
                linkPathFormat = text("newLinkFormat", "shortest", previous.linkPathFormat) { it in setOf("shortest", "relative", "absolute") },
            )
        }
        return ObsidianRulesRead(rules, invalid)
    }
}

object ObsidianLinkPolicy {
    fun noteTarget(candidatePath: String, knownPaths: Collection<String>?): String =
        knownPaths?.singleOrNull { it.endsWith(".md", true) && it.dropLast(3) == candidatePath } ?: "$candidatePath.md"

    fun normalize(path: String): String? {
        if (path.startsWith('/') || path.startsWith('\\') || path.contains(':') || path.contains('\u0000')) return null
        val parts = mutableListOf<String>()
        for (part in path.replace('\\', '/').split('/')) when (part) {
            "", "." -> Unit
            ".." -> if (parts.isEmpty()) return null else parts.removeAt(parts.lastIndex)
            else -> parts += part
        }
        return parts.joinToString("/")
    }

    fun attachmentDirectory(setting: String, sourceNote: String): String? {
        val note = normalize(sourceNote) ?: return null
        val parent = note.substringBeforeLast('/', "")
        return when {
            setting == "/" || setting.isBlank() -> ""
            setting == "." -> parent
            setting.startsWith("./") -> normalize(listOf(parent, setting.removePrefix("./")).filter { it.isNotEmpty() }.joinToString("/"))
            else -> normalize(setting.trim('/'))
        }
    }

    fun relative(sourceNote: String, target: String): String {
        val source = requireNotNull(normalize(sourceNote)).substringBeforeLast('/', "").split('/').filter(String::isNotEmpty)
        val destination = requireNotNull(normalize(target)).split('/')
        val common = source.zip(destination).takeWhile { it.first == it.second }.size
        return (List(source.size - common) { ".." } + destination.drop(common)).joinToString("/")
    }

    fun path(sourceNote: String, target: String, format: String, knownPaths: Collection<String>? = null): String {
        val clean = requireNotNull(normalize(target)).also { require(it.isNotBlank()) }
        return when (format) {
            "relative" -> relative(sourceNote, clean)
            "shortest" -> {
                // Until a complete index is available, retain the full, unambiguous vault path.
                if (knownPaths == null) return clean
                val paths = (knownPaths + clean).distinct()
                val segments = clean.split('/')
                (1..segments.size).map { segments.takeLast(it).joinToString("/") }
                    .firstOrNull { suffix -> paths.count { it.equals(suffix, true) || it.endsWith("/$suffix", true) } == 1 } ?: clean
            }
            else -> clean
        }
    }

    fun link(sourceNote: String, target: String, rules: ObsidianRules, embed: Boolean = true,
             label: String = target.substringAfterLast('/').substringBeforeLast('.'), knownPaths: Collection<String>? = null): String {
        var destination = path(sourceNote, target, rules.linkPathFormat, knownPaths)
        val prefix = if (embed) "!" else ""
        return if (rules.markdownLinks) {
            val safeLabel = label.replace("\\", "\\\\").replace("[", "\\[").replace("]", "\\]")
            "$prefix[$safeLabel](${encode(destination)})"
        } else {
            require(destination.none { it in "#|[]^" }) { "unsupported_wikilink_filename" }
            if (!embed && destination.endsWith(".md", true)) destination = destination.dropLast(3)
            val leaf = target.substringAfterLast('/')
            val defaultLabel = if (leaf.endsWith(".md", true)) leaf.dropLast(3) else leaf
            val alias = if (!embed && label != defaultLabel) "|${label.replace("|", "").replace("]", "")}" else ""
            "$prefix[[$destination$alias]]"
        }
    }

    fun encode(path: String): String = path.split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
    fun decode(path: String): String? = runCatching { URLDecoder.decode(path.replace("+", "%2B"), "UTF-8") }.getOrNull()

    /** Note-relative Markdown first, then old vault paths; Wikilinks prefer vault paths. */
    fun candidates(path: String, sourceNote: String, wiki: Boolean, knownPaths: Collection<String> = emptyList(), attachmentFolder: String = "/"): List<String> {
        val decoded = if (wiki) path else decode(path) ?: return emptyList()
        if (decoded.startsWith('/') || decoded.contains(':')) return emptyList()
        val parent = normalize(sourceNote)?.substringBeforeLast('/', "") ?: return emptyList()
        val fromNote = normalize(listOf(parent, decoded).filter(String::isNotEmpty).joinToString("/"))
        val fromRoot = normalize(decoded)
        if (decoded.startsWith("./") || decoded.startsWith("../")) return listOfNotNull(fromNote)
        val indexed = knownPaths.filter { it == decoded || it.endsWith("/$decoded") }.singleOrNull()
        val legacyFolder = attachmentDirectory(attachmentFolder, sourceNote)?.let {
            normalize(listOf(it, decoded).filter(String::isNotEmpty).joinToString("/"))
        }
        return (if (wiki) listOf(fromRoot, indexed, fromNote, legacyFolder) else listOf(fromNote, fromRoot, indexed, legacyFolder))
            .filterNotNull().filter(String::isNotBlank).distinct()
    }
}
