package com.quickdaily

import android.content.Context
import android.net.Uri

object WikilinkRecentStore {
    private const val PREFS = "QuickDaily"
    private const val KEY = "wikilink_recent_candidates_v1"
    private const val MAX_SIZE = 10
    private const val FIELD_SEPARATOR = "\t"
    private const val ITEM_SEPARATOR = "\n"

    fun load(context: Context): List<WikilinkCandidate> {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
            .orEmpty()
            .split(ITEM_SEPARATOR)
            .filter { it.isNotBlank() }
            .mapNotNull(::decode)
            .distinctBy { it.stableKey }
            .take(MAX_SIZE)
    }

    fun record(context: Context, candidate: WikilinkCandidate) {
        val next = buildList {
            add(candidate)
            addAll(load(context).filterNot { it.stableKey == candidate.stableKey })
        }.take(MAX_SIZE)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, next.joinToString(ITEM_SEPARATOR, transform = ::encode))
            .apply()
    }

    private fun encode(candidate: WikilinkCandidate): String = listOf(
        if (candidate.alias == null) "page" else "alias",
        Uri.encode(candidate.targetPath),
        Uri.encode(candidate.alias.orEmpty()),
    ).joinToString(FIELD_SEPARATOR)

    private fun decode(raw: String): WikilinkCandidate? {
        val fields = raw.split(FIELD_SEPARATOR)
        if (fields.size != 3) return null
        val target = Uri.decode(fields[1]).trim()
        if (target.isEmpty()) return null
        val alias = Uri.decode(fields[2]).trim().takeIf { it.isNotEmpty() }
        return if (fields[0] == "page") WikilinkCandidate(target) else if (fields[0] == "alias" && alias != null) {
            WikilinkCandidate(target, alias)
        } else {
            null
        }
    }
}
