package com.quickdaily

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingPolicyTest {
    @Test
    fun cleanInstallShowsOnboarding() {
        assertTrue(OnboardingPolicy.shouldShowOnFirstState(false))
    }

    @Test
    fun updateOrExistingConfigurationSkipsAutomaticOnboarding() {
        assertTrue(OnboardingPolicy.shouldShowOnFirstState(false))
        assertFalse(OnboardingPolicy.shouldShowOnFirstState(true))
    }

    @Test
    fun onlyVaultAndAllFilesAccessBlockForwardNavigation() {
        assertTrue(OnboardingPolicy.canAdvance(0, vaultConfigured = false, allFilesAccessGranted = false))
        assertFalse(OnboardingPolicy.canAdvance(1, vaultConfigured = false, allFilesAccessGranted = true))
        assertTrue(OnboardingPolicy.canAdvance(1, vaultConfigured = true, allFilesAccessGranted = false))
        assertFalse(OnboardingPolicy.canAdvance(2, vaultConfigured = true, allFilesAccessGranted = false))
        assertTrue(OnboardingPolicy.canAdvance(2, vaultConfigured = true, allFilesAccessGranted = true))
        assertTrue(OnboardingPolicy.canAdvance(3, vaultConfigured = false, allFilesAccessGranted = false))
    }

    @Test
    fun pageAndCoachProgressAreBounded() {
        assertEquals(0, OnboardingPolicy.clampPage(-1))
        assertEquals(3, OnboardingPolicy.clampPage(9))
        assertEquals(0, OnboardingPolicy.clampCoachStep(-1))
        assertEquals(4, OnboardingPolicy.clampCoachStep(9))
        assertEquals(1, OnboardingPolicy.nextCoachStep(0))
        assertEquals(2, OnboardingPolicy.nextCoachStep(1))
        assertEquals(3, OnboardingPolicy.nextCoachStep(2))
        assertEquals(4, OnboardingPolicy.nextCoachStep(3))
        assertNull(OnboardingPolicy.nextCoachStep(4))
        assertNull(OnboardingPolicy.previousCoachStep(0))
        assertEquals(3, OnboardingPolicy.previousCoachStep(4))
    }

    @Test
    fun enteringFullscreenFromTheFourthStepContinuesWithTheFinalStep() {
        assertEquals(4, OnboardingPolicy.coachStepAfterEnteringFullscreen(3))
        assertEquals(4, OnboardingPolicy.coachStepAfterEnteringFullscreen(4))
        assertEquals(2, OnboardingPolicy.coachStepAfterEnteringFullscreen(2))
    }

    @Test
    fun legacyFloatingCoachStepsMoveOnlyPendingUsersToTheInsertedStep() {
        assertEquals(0, OnboardingPolicy.migrateCoachStepFromLegacy(0, FloatingCoachState.PENDING))
        assertEquals(2, OnboardingPolicy.migrateCoachStepFromLegacy(1, FloatingCoachState.PENDING))
        assertEquals(3, OnboardingPolicy.migrateCoachStepFromLegacy(2, FloatingCoachState.PENDING))
        assertEquals(3, OnboardingPolicy.migrateCoachStepFromLegacy(3, FloatingCoachState.PENDING))
        assertEquals(2, OnboardingPolicy.migrateCoachStepFromLegacy(2, FloatingCoachState.COMPLETED))
    }

    @Test
    fun fourStepUsersKeepTheirPendingStepWhenTheFifthStepIsAppended() {
        assertEquals(
            1,
            OnboardingPolicy.migrateCoachStepFromLegacy(
                step = 1,
                state = FloatingCoachState.PENDING,
                storedVersion = 2,
            ),
        )
        assertEquals(
            2,
            OnboardingPolicy.migrateCoachStepFromLegacy(
                step = 2,
                state = FloatingCoachState.PENDING,
                storedVersion = 2,
            ),
        )
        assertEquals(
            3,
            OnboardingPolicy.migrateCoachStepFromLegacy(
                step = 3,
                state = FloatingCoachState.PENDING,
                storedVersion = 2,
            ),
        )
    }

    @Test
    fun toolbarDemoUsesAdjacentPageAndSkipsSinglePageToolbars() {
        assertNull(FloatingCoachToolbarDemoPolicy.adjacentPage(currentPage = 0, pageCount = 1))
        assertEquals(1, FloatingCoachToolbarDemoPolicy.adjacentPage(currentPage = 0, pageCount = 3))
        assertEquals(1, FloatingCoachToolbarDemoPolicy.adjacentPage(currentPage = 2, pageCount = 3))
        assertEquals(1, FloatingCoachToolbarDemoPolicy.adjacentPage(currentPage = 9, pageCount = 3))
    }

    @Test
    fun floatingCoachRequiresCompletedMainFlowAndPendingState() {
        assertTrue(
            OnboardingPolicy.shouldShowFloatingCoach(
                onboardingCompleted = true,
                coachState = FloatingCoachState.PENDING,
            ),
        )
        assertFalse(
            OnboardingPolicy.shouldShowFloatingCoach(
                onboardingCompleted = false,
                coachState = FloatingCoachState.PENDING,
            ),
        )
        assertFalse(
            OnboardingPolicy.shouldShowFloatingCoach(
                onboardingCompleted = true,
                coachState = FloatingCoachState.COMPLETED,
            ),
        )
        assertFalse(
            OnboardingPolicy.shouldShowFloatingCoach(
                onboardingCompleted = true,
                coachState = FloatingCoachState.SKIPPED,
            ),
        )
    }
}
