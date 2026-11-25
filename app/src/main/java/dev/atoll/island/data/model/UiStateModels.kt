package dev.atoll.island.data.model

// separate dalla config, così cambiare modalità non ricrea la notifica
data class AppUiPreferences(
    val hasCompletedOnboarding: Boolean = false,
    val isAdvancedMode: Boolean = false
)

enum class OnboardingStep(val index: Int) {
    WELCOME(0),
    NOTIFICATIONS(1),
    LIVE_ALERTS(2),
    KEEP_ALIVE(3),
    ALL_SET(4);

    companion object {
        fun fromIndex(index: Int): OnboardingStep =
            entries.firstOrNull { it.index == index } ?: WELCOME
    }
}

data class WizardLaunchRequest(
    val initialStep: OnboardingStep = OnboardingStep.WELCOME,
    val isRerun: Boolean = false
)
