package com.quickdaily

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.StringRes
import java.util.Locale

/**
 * Owns the app-level language override without changing the process default
 * locale or the user's system configuration.
 */
object LocaleController {
    const val SYSTEM = "system"
    private const val PREFS = "QuickDaily"
    private const val PREF_LANGUAGE = "language_override"

    data class SupportedLanguage(
        val tag: String,
        @StringRes val displayNameRes: Int,
        /** Android/system locale tags that this resource bundle represents. */
        val systemLocaleTags: Set<String>,
    )

    /** Add a resource directory and one row here when a new language is added. */
    val supportedLanguages: List<SupportedLanguage> = listOf(
        SupportedLanguage(SYSTEM, R.string.qd_language_system, setOf(SYSTEM)),
        SupportedLanguage(
            tag = "zh",
            displayNameRes = R.string.qd_language_zh,
            systemLocaleTags = setOf("zh", "zh-CN", "zh-Hans", "zh-Hans-CN", "zh-TW", "zh-Hant"),
        ),
        SupportedLanguage(
            tag = "en",
            displayNameRes = R.string.qd_language_en,
            systemLocaleTags = setOf("en", "en-US", "en-GB", "en-AU", "en-CA"),
        ),
    )

    private val supportedLocaleTags: Map<String, String>
        get() = supportedLanguages
            .flatMap { language -> language.systemLocaleTags.map { it to language.tag } }
            .toMap()

    fun selection(context: Context): String {
        val stored = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(PREF_LANGUAGE, SYSTEM)
            .orEmpty()
        return normalizeSelection(stored)
    }

    fun setSelection(context: Context, requestedTag: String): String {
        val normalized = normalizeSelection(requestedTag)
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Locale refreshes can race a service/widget read. commit() makes the
        // canonical tag visible before any refresh broadcast is sent.
        check(prefs.edit().putString(PREF_LANGUAGE, normalized).commit()) {
            "Unable to persist language selection"
        }
        LocaleRefreshCoordinator.dispatch(context.applicationContext, normalized)
        return normalized
    }

    fun normalizeSelection(requestedTag: String?): String {
        val tag = requestedTag?.trim().orEmpty()
        if (tag.isEmpty() || tag == SYSTEM) return SYSTEM
        val canonical = runCatching { Locale.forLanguageTag(tag).toLanguageTag() }.getOrNull()
        return canonical?.let { supportedLocaleTags[it] } ?: SYSTEM
    }

    /**
     * Applies an explicit app language to one component context. The system
     * selection deliberately returns the original context so Android remains
     * the source of the system locale.
     */
    fun localizedContext(context: Context): Context {
        val tag = selection(context)
        if (tag == SYSTEM) return context
        val locale = Locale.forLanguageTag(tag)
        val configuration = Configuration(context.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.setLocales(LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            configuration.locale = locale
        }
        return context.createConfigurationContext(configuration)
    }

    fun displayName(context: Context, tag: String): String {
        val entry = supportedLanguages.firstOrNull { it.tag == normalizeSelection(tag) }
            ?: supportedLanguages.first()
        return localizedContext(context).getString(entry.displayNameRes)
    }
}
