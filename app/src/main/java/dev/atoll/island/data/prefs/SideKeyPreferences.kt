package dev.atoll.island.data.prefs

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.atoll.island.data.model.SideKeyConfig
import dev.atoll.island.domain.sidekey.SideKeyCodec

internal object SideKeyPreferences {
    private val enabled = booleanPreferencesKey("sidekey_enabled")
    private val setupDismissed = booleanPreferencesKey("sidekey_setup_dismissed")
    private val wizardStep = intPreferencesKey("sidekey_wizard_step")
    private val wizardLanguageSwitched = booleanPreferencesKey("sidekey_wizard_lang_switched")
    private val bindings = stringPreferencesKey("sidekey_bindings_json")
    private val longPressTimeoutMs = longPreferencesKey("sidekey_long_press_ms")
    private val multiTapTimeoutMs = longPreferencesKey("sidekey_multi_tap_ms")
    private val workWithScreenOff = booleanPreferencesKey("sidekey_screen_off")
    private val hapticFeedback = booleanPreferencesKey("sidekey_haptic")
    private val filterPattern = stringPreferencesKey("sidekey_filter_pattern")

    fun read(prefs: Preferences): SideKeyConfig {
        val defaults = SideKeyConfig()
        val savedStep = prefs[wizardStep] ?: defaults.wizardStep
        return SideKeyConfig(
            enabled = prefs[enabled] ?: defaults.enabled,
            setupDismissed = prefs[setupDismissed] ?: defaults.setupDismissed,
            wizardStep = if (savedStep in 0..SideKeyConfig.WIZARD_MAX_STEP) savedStep else SideKeyConfig.WIZARD_NONE,
            wizardLanguageSwitched = prefs[wizardLanguageSwitched] ?: defaults.wizardLanguageSwitched,
            bindings = SideKeyCodec.decodeBindings(prefs[bindings]),
            longPressTimeoutMs = (prefs[longPressTimeoutMs] ?: defaults.longPressTimeoutMs)
                .coerceIn(SideKeyConfig.MIN_LONG_PRESS_MS, SideKeyConfig.MAX_LONG_PRESS_MS),
            multiTapTimeoutMs = (prefs[multiTapTimeoutMs] ?: defaults.multiTapTimeoutMs)
                .coerceIn(SideKeyConfig.MIN_MULTI_TAP_MS, SideKeyConfig.MAX_MULTI_TAP_MS),
            workWithScreenOff = prefs[workWithScreenOff] ?: defaults.workWithScreenOff,
            hapticFeedback = prefs[hapticFeedback] ?: defaults.hapticFeedback,
            filterPattern = (prefs[filterPattern] ?: defaults.filterPattern)
                .ifBlank { SideKeyConfig.DEFAULT_FILTER_PATTERN }
        )
    }

    fun write(prefs: MutablePreferences, config: SideKeyConfig) {
        prefs[enabled] = config.enabled
        prefs[setupDismissed] = config.setupDismissed
        prefs[wizardStep] = if (config.wizardStep in 0..SideKeyConfig.WIZARD_MAX_STEP) {
            config.wizardStep
        } else {
            SideKeyConfig.WIZARD_NONE
        }
        prefs[wizardLanguageSwitched] = config.wizardLanguageSwitched
        prefs[bindings] = SideKeyCodec.encodeBindings(config.bindings)
        prefs[longPressTimeoutMs] = config.longPressTimeoutMs
            .coerceIn(SideKeyConfig.MIN_LONG_PRESS_MS, SideKeyConfig.MAX_LONG_PRESS_MS)
        prefs[multiTapTimeoutMs] = config.multiTapTimeoutMs
            .coerceIn(SideKeyConfig.MIN_MULTI_TAP_MS, SideKeyConfig.MAX_MULTI_TAP_MS)
        prefs[workWithScreenOff] = config.workWithScreenOff
        prefs[hapticFeedback] = config.hapticFeedback
        prefs[filterPattern] = config.filterPattern.trim().ifEmpty { SideKeyConfig.DEFAULT_FILTER_PATTERN }
    }
}
