package com.quickdaily

import android.content.SharedPreferences
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

internal object HiddenTextConfig {
    const val PREF_KEY = "hidden_display_texts"

    private val json = Json { ignoreUnknownKeys = false }

    fun read(prefs: SharedPreferences): List<String> {
        val raw = prefs.getString(PREF_KEY, null)?.takeIf { it.isNotBlank() } ?: return emptyList()
        val element = runCatching { json.parseToJsonElement(raw) }.getOrNull() ?: return emptyList()
        val array = element as? JsonArray ?: return emptyList()
        val values = mutableListOf<String>()
        array.forEach { elementValue ->
            val primitive = elementValue as? JsonPrimitive ?: return emptyList()
            if (!primitive.isString) return emptyList()
            values += primitive.content
        }
        return HiddenTextPolicy.normalizeRules(values)
    }

    fun encode(values: List<String>): String = JsonArray(
        HiddenTextPolicy.normalizeRules(values).map(::JsonPrimitive),
    ).toString()
}
