package dev.atoll.island.data.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CalibrationOverlayPreferencesTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun rulerOffsetSurvivesReopenAndGeometryUpdates() = runTest {
        val file = temporaryFolder.root.resolve("calibration.preferences_pb")
        val writer = SupervisorJob()
        try {
            val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + writer)) { file }
            assertEquals(CalibrationOverlayPreferences(), CalibrationOverlayPreferences.read(store.data.first()))
            store.edit {
                CalibrationOverlayPreferences.writeYOffset(it, -6)
                CalibrationOverlayPreferences.writeGeometry(it, 53, 126)
            }
        } finally {
            writer.cancelAndJoin()
        }

        val reader = SupervisorJob()
        try {
            val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + reader)) { file }
            assertEquals(CalibrationOverlayPreferences(53, 126, -6), CalibrationOverlayPreferences.read(store.data.first()))
        } finally {
            reader.cancelAndJoin()
        }
    }

    @Test
    fun clampsOffsetWithoutChangingStoredGeometry() {
        val prefs = emptyPreferences().toMutablePreferences()
        CalibrationOverlayPreferences.writeGeometry(prefs, 53, 126)
        CalibrationOverlayPreferences.writeYOffset(prefs, 200)
        assertEquals(CalibrationOverlayPreferences(53, 126, 96), CalibrationOverlayPreferences.read(prefs))
    }
}
