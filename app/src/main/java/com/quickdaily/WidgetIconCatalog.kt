package com.quickdaily

import androidx.annotation.DrawableRes

/**
 * The single source of truth for widget entry icons.
 *
 * These are local copies of the selected Material Symbols: Sticky Note,
 * List Alt Add and Add Box. Keeping the resource mapping here prevents the
 * onboarding and settings screens from drifting apart.
 */
internal object WidgetIconCatalog {
    @DrawableRes
    val note: Int = R.drawable.ic_widget_sticky_note

    @DrawableRes
    val task: Int = R.drawable.ic_widget_list_alt_add

    @DrawableRes
    val quickEntry: Int = R.drawable.ic_widget_add_box
}
