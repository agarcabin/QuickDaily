package com.quickdaily

import android.content.Context

internal enum class FloatingCoachState(val key: String) {
    PENDING("pending"),
    COMPLETED("completed"),
    SKIPPED("skipped");

    companion object {
        fun fromKey(key: String?): FloatingCoachState = entries.firstOrNull { it.key == key } ?: PENDING
    }
}

internal object OnboardingPolicy {
    const val CURRENT_VERSION = 1
    const val PAGE_COUNT = 4
    const val FLOATING_COACH_STEP_COUNT = 5

    fun shouldShowOnFirstState(hasLegacyConfig: Boolean): Boolean = !hasLegacyConfig

    fun canAdvance(
        page: Int,
        vaultConfigured: Boolean,
        allFilesAccessGranted: Boolean,
    ): Boolean = when (clampPage(page)) {
        1 -> vaultConfigured
        2 -> allFilesAccessGranted
        else -> true
    }

    fun clampPage(page: Int): Int = page.coerceIn(0, PAGE_COUNT - 1)

    fun clampCoachStep(step: Int): Int = step.coerceIn(0, FLOATING_COACH_STEP_COUNT - 1)

    fun nextCoachStep(step: Int): Int? = when (clampCoachStep(step)) {
        0 -> 1
        1 -> 2
        2 -> 3
        3 -> 4
        else -> null
    }

    fun coachStepAfterEnteringFullscreen(step: Int): Int =
        if (clampCoachStep(step) == FLOATING_COACH_STEP_COUNT - 2) {
            FLOATING_COACH_STEP_COUNT - 1
        } else {
            clampCoachStep(step)
        }

    fun previousCoachStep(step: Int): Int? = when (clampCoachStep(step)) {
        0 -> null
        else -> clampCoachStep(step) - 1
    }

    fun migrateCoachStepFromLegacy(
        step: Int,
        state: FloatingCoachState,
        storedVersion: Int = 1,
    ): Int =
        if (state == FloatingCoachState.PENDING && storedVersion < 2) {
            when (step) {
                1 -> 2
                2 -> 3
                else -> clampCoachStep(step)
            }
        } else {
            clampCoachStep(step)
        }

    fun shouldShowFloatingCoach(
        onboardingCompleted: Boolean,
        coachState: FloatingCoachState,
    ): Boolean = onboardingCompleted && coachState == FloatingCoachState.PENDING
}

/** Persistent state shared by the first-run flow and the contextual overlay tutorial. */
internal object OnboardingStore {
    private const val PREFS = "QuickDaily"
    private const val KEY_VERSION = "onboarding_version"
    private const val KEY_COMPLETED = "onboarding_completed"
    private const val KEY_SKIPPED = "onboarding_skipped"
    private const val KEY_PAGE = "onboarding_page"
    private const val KEY_COACH_STATE = "floating_coach_state"
    private const val KEY_COACH_STEP = "floating_coach_step"
    private const val KEY_COACH_VERSION = "floating_coach_version"
    private const val CURRENT_COACH_VERSION = 3

    private val legacyConfigKeys = setOf(
        "vault_path",
        "diary_folder",
        "date_format",
        "timestamp_format",
        "anchor_text",
        "home_entry_mode",
        EditorToolbarPolicy.PREF_ORDER,
        TaskCompletionTimestampPolicy.PREF_KEY,
    )

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_VERSION)) {
            val hasLegacyConfig = legacyConfigKeys.any(prefs::contains)
            val shouldShow = OnboardingPolicy.shouldShowOnFirstState(hasLegacyConfig)
            prefs.edit()
                .putInt(KEY_VERSION, OnboardingPolicy.CURRENT_VERSION)
                .putBoolean(KEY_COMPLETED, !shouldShow)
                .putBoolean(KEY_SKIPPED, false)
                .putInt(KEY_PAGE, 0)
                .putString(
                    KEY_COACH_STATE,
                    // The main flow and the floating-window coach are separate. Even a
                    // legacy install that skips the main flow should see the coach on its
                    // first real floating-window launch.
                    FloatingCoachState.PENDING.key,
                )
                .putInt(KEY_COACH_STEP, 0)
                .putInt(KEY_COACH_VERSION, CURRENT_COACH_VERSION)
                .apply()
        }
        migrateFloatingCoachState(prefs)
    }

    fun shouldShow(context: Context): Boolean {
        initialize(context)
        return !prefs(context).getBoolean(KEY_COMPLETED, false)
    }

    fun page(context: Context): Int = OnboardingPolicy.clampPage(prefs(context).getInt(KEY_PAGE, 0))

    fun setPage(context: Context, page: Int) {
        prefs(context).edit().putInt(KEY_PAGE, OnboardingPolicy.clampPage(page)).apply()
    }

    fun complete(context: Context) {
        initialize(context)
        prefs(context).edit()
            .putInt(KEY_VERSION, OnboardingPolicy.CURRENT_VERSION)
            .putBoolean(KEY_COMPLETED, true)
            .putBoolean(KEY_SKIPPED, false)
            .putInt(KEY_PAGE, OnboardingPolicy.PAGE_COUNT - 1)
            .putString(KEY_COACH_STATE, FloatingCoachState.PENDING.key)
            .putInt(KEY_COACH_STEP, 0)
            .apply()
    }

    fun skip(context: Context) {
        initialize(context)
        prefs(context).edit()
            .putInt(KEY_VERSION, OnboardingPolicy.CURRENT_VERSION)
            .putBoolean(KEY_COMPLETED, true)
            .putBoolean(KEY_SKIPPED, true)
            .putString(KEY_COACH_STATE, FloatingCoachState.PENDING.key)
            .putInt(KEY_COACH_STEP, 0)
            .apply()
    }

    fun restart(context: Context) {
        initialize(context)
        prefs(context).edit()
            .putInt(KEY_VERSION, OnboardingPolicy.CURRENT_VERSION)
            .putBoolean(KEY_COMPLETED, false)
            .putBoolean(KEY_SKIPPED, false)
            .putInt(KEY_PAGE, 0)
            .putString(KEY_COACH_STATE, FloatingCoachState.PENDING.key)
            .putInt(KEY_COACH_STEP, 0)
            .apply()
    }

    fun shouldShowFloatingCoach(context: Context): Boolean {
        initialize(context)
        return OnboardingPolicy.shouldShowFloatingCoach(
            onboardingCompleted = prefs(context).getBoolean(KEY_COMPLETED, false),
            coachState = FloatingCoachState.fromKey(prefs(context).getString(KEY_COACH_STATE, null)),
        )
    }

    fun floatingCoachStep(context: Context): Int {
        initialize(context)
        return OnboardingPolicy.clampCoachStep(prefs(context).getInt(KEY_COACH_STEP, 0))
    }

    fun advanceFloatingCoach(context: Context): Int? {
        initialize(context)
        val next = OnboardingPolicy.nextCoachStep(floatingCoachStep(context))
        if (next == null) {
            finishFloatingCoach(context)
        } else {
            prefs(context).edit().putInt(KEY_COACH_STEP, next).apply()
        }
        return next
    }

    fun previousFloatingCoach(context: Context): Int {
        initialize(context)
        val current = floatingCoachStep(context)
        val previous = OnboardingPolicy.previousCoachStep(current) ?: current
        if (previous != current) {
            prefs(context).edit().putInt(KEY_COACH_STEP, previous).apply()
        }
        return previous
    }

    fun finishFloatingCoach(context: Context) {
        initialize(context)
        prefs(context).edit()
            .putString(KEY_COACH_STATE, FloatingCoachState.COMPLETED.key)
            .putInt(KEY_COACH_STEP, OnboardingPolicy.FLOATING_COACH_STEP_COUNT - 1)
            .apply()
    }

    fun skipFloatingCoach(context: Context) {
        initialize(context)
        prefs(context).edit()
            .putString(KEY_COACH_STATE, FloatingCoachState.SKIPPED.key)
            .apply()
    }

    private fun migrateFloatingCoachState(prefs: android.content.SharedPreferences) {
        if (prefs.getInt(KEY_COACH_VERSION, 0) >= CURRENT_COACH_VERSION) return

        val state = FloatingCoachState.fromKey(prefs.getString(KEY_COACH_STATE, null))
        val storedVersion = prefs.getInt(KEY_COACH_VERSION, 0)
        val oldStep = prefs.getInt(KEY_COACH_STEP, 0)
        val migratedStep = OnboardingPolicy.migrateCoachStepFromLegacy(oldStep, state, storedVersion)
        prefs.edit()
            .putInt(KEY_COACH_STEP, migratedStep)
            .putInt(KEY_COACH_VERSION, CURRENT_COACH_VERSION)
            .apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
