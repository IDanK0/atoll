package dev.atoll.island.domain

import dev.atoll.island.data.model.OnboardingStep
import dev.atoll.island.data.model.PromotionDiagnostics

object OnboardingCoordinator {

    fun stepProgressFraction(step: OnboardingStep): Float =
        (step.index + 1).toFloat() / OnboardingStep.entries.size.toFloat()

    // null se i permessi sono a posto
    fun resolveMissingPermissionStep(diagnostics: PromotionDiagnostics): OnboardingStep? {
        if (diagnostics.apiLevel <= 0) return null
        if (!diagnostics.notificationsEnabled) {
            return OnboardingStep.NOTIFICATIONS
        }
        if (diagnostics.isAndroid16Plus && !diagnostics.canPostPromotedNotifications) {
            return OnboardingStep.LIVE_ALERTS
        }
        return null
    }

    fun shouldShowPermissionFixBanner(diagnostics: PromotionDiagnostics): Boolean =
        resolveMissingPermissionStep(diagnostics) != null
}
