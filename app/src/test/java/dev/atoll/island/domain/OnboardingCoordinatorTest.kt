package dev.atoll.island.domain

import dev.atoll.island.data.model.AppUiPreferences
import dev.atoll.island.data.model.CapsulePreset
import dev.atoll.island.data.model.OnboardingStep
import dev.atoll.island.data.model.PromotionDiagnostics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingCoordinatorTest {

    @Test
    fun `default AppUiPreferences always starts onboarding in Simple Mode`() {
        val defaults = AppUiPreferences()
        assertFalse(defaults.hasCompletedOnboarding)
        assertFalse(defaults.isAdvancedMode)
    }

    @Test
    fun `resolveMissingPermissionStep prioritizes notifications first`() {
        val missingNotif = PromotionDiagnostics(
            apiLevel = 37,
            isAndroid16Plus = true,
            notificationsEnabled = false,
            canPostPromotedNotifications = false
        )
        assertEquals(OnboardingStep.NOTIFICATIONS, OnboardingCoordinator.resolveMissingPermissionStep(missingNotif))
        assertTrue(OnboardingCoordinator.shouldShowPermissionFixBanner(missingNotif))
    }

    @Test
    fun `resolveMissingPermissionStep targets Live Alerts when only promoted is revoked on API 36+`() {
        val missingLiveAlerts = PromotionDiagnostics(
            apiLevel = 37,
            isAndroid16Plus = true,
            notificationsEnabled = true,
            canPostPromotedNotifications = false
        )
        assertEquals(OnboardingStep.LIVE_ALERTS, OnboardingCoordinator.resolveMissingPermissionStep(missingLiveAlerts))
        assertTrue(OnboardingCoordinator.shouldShowPermissionFixBanner(missingLiveAlerts))
    }

    @Test
    fun `resolveMissingPermissionStep returns null on healthy device and skips Live Alerts check below API 36`() {
        val healthyApi37 = PromotionDiagnostics(
            apiLevel = 37,
            isAndroid16Plus = true,
            notificationsEnabled = true,
            canPostPromotedNotifications = true
        )
        assertNull(OnboardingCoordinator.resolveMissingPermissionStep(healthyApi37))
        assertFalse(OnboardingCoordinator.shouldShowPermissionFixBanner(healthyApi37))

        val healthyApi34 = PromotionDiagnostics(
            apiLevel = 34,
            isAndroid16Plus = false,
            notificationsEnabled = true,
            canPostPromotedNotifications = false
        )
        assertNull(OnboardingCoordinator.resolveMissingPermissionStep(healthyApi34))
        assertFalse(OnboardingCoordinator.shouldShowPermissionFixBanner(healthyApi34))

        val uninitialized = PromotionDiagnostics(apiLevel = 0)
        assertNull(OnboardingCoordinator.resolveMissingPermissionStep(uninitialized))
    }

    @Test
    fun `stepProgressFraction scales monotonically from 0_2 to 1_0`() {
        assertEquals(0.2f, OnboardingCoordinator.stepProgressFraction(OnboardingStep.WELCOME), 0.001f)
        assertEquals(0.4f, OnboardingCoordinator.stepProgressFraction(OnboardingStep.NOTIFICATIONS), 0.001f)
        assertEquals(0.6f, OnboardingCoordinator.stepProgressFraction(OnboardingStep.LIVE_ALERTS), 0.001f)
        assertEquals(0.8f, OnboardingCoordinator.stepProgressFraction(OnboardingStep.KEEP_ALIVE), 0.001f)
        assertEquals(1.0f, OnboardingCoordinator.stepProgressFraction(OnboardingStep.ALL_SET), 0.001f)
    }
}
