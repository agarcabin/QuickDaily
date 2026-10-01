package com.quickdaily

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/**
 * A typed boundary between app-owned copy and user/external text.
 *
 * Resource and plural entries are safe to translate. Raw is intentionally
 * reserved for Vault content, Markdown, file paths, and third-party text.
 */
sealed interface UiText {
    fun resolve(context: Context): CharSequence

    data class Resource(
        @StringRes val resId: Int,
        val args: List<Any> = emptyList(),
    ) : UiText {
        override fun resolve(context: Context): String =
            context.getString(resId, *args.resolve(context))
    }

    data class Plural(
        @PluralsRes val resId: Int,
        val quantity: Int,
        val args: List<Any> = emptyList(),
    ) : UiText {
        override fun resolve(context: Context): String {
            // Android uses quantity to select the branch but does not inject
            // it into format arguments, so it is deliberately first here.
            val formatArgs = arrayOf<Any>(quantity, *args.resolve(context))
            return context.resources.getQuantityString(resId, quantity, *formatArgs)
        }
    }

    data class Raw(val value: CharSequence) : UiText {
        override fun resolve(context: Context): CharSequence = value
    }
}

private fun List<Any>.resolve(context: Context): Array<Any> =
    map { argument ->
        if (argument is UiText) argument.resolve(context).toString() else argument
    }.toTypedArray()

fun Context.resolveUiText(text: UiText): CharSequence =
    text.resolve(LocaleController.localizedContext(this))
