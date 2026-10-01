package com.quickdaily

/** Pure page-selection rules for the one-time floating-coach toolbar demo. */
internal object FloatingCoachToolbarDemoPolicy {
    fun adjacentPage(currentPage: Int, pageCount: Int): Int? {
        if (pageCount <= 1) return null
        val current = currentPage.coerceIn(0, pageCount - 1)
        return if (current < pageCount - 1) current + 1 else current - 1
    }
}
