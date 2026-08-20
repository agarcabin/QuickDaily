package com.quickdaily

import android.content.Context
import androidx.annotation.DrawableRes

internal data class SponsorEntry(
    val id: String,
    val nickname: String,
    val message: String,
    @DrawableRes val avatarRes: Int,
)

internal interface SponsorEntrySource {
    fun entries(context: Context): List<SponsorEntry>
}

internal val defaultSponsorEntries = listOf(
    SponsorEntry(
        id = "sponsor-o",
        nickname = "*o",
        message = "\u6682\u65e0\u7559\u8a00",
        avatarRes = R.drawable.sponsor_avatar_o,
    ),
    SponsorEntry(
        id = "sponsor-wei",
        nickname = "*\u5c09",
        message = "QuickDaily\u548c\u8baf\u98de\u8f93\u5165\u6cd5\u5f88\u597d\u7528\uff0c\u8bf7\u4f60\u559d\u53ef\u4e50\uff01",
        avatarRes = R.drawable.sponsor_avatar_wei,
    ),
)

internal object DefaultSponsorEntrySource : SponsorEntrySource {
    override fun entries(context: Context): List<SponsorEntry> = defaultSponsorEntries
}

internal object SponsorEntryRegistry {
    var source: SponsorEntrySource = DefaultSponsorEntrySource

    fun entries(context: Context): List<SponsorEntry> = source.entries(context)
}

internal object SponsorReadState {
    private const val PREFERENCES_NAME = "QuickDaily"
    private const val READ_KEY_PREFIX = "sponsor_message_read_"

    fun isRead(context: Context, sponsorId: String): Boolean =
        preferences(context).getBoolean(readKey(sponsorId), false)

    fun markRead(context: Context, sponsorId: String) {
        preferences(context).edit().putBoolean(readKey(sponsorId), true).apply()
    }

    fun resetAll(context: Context) {
        val prefs = preferences(context)
        prefs.edit().apply {
            prefs.all.keys
                .filter { it.startsWith(READ_KEY_PREFIX) }
                .forEach(::remove)
        }.apply()
    }

    internal fun readKey(sponsorId: String): String = READ_KEY_PREFIX + sponsorId

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
}
