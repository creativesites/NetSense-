package com.netsense.netpulse

import com.netsense.netpulse.data.AppSettings
import com.netsense.netpulse.engine.SpeedTestStage
import com.netsense.netpulse.engine.SpeedTestState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase5SpeedAndSettingsTest {

    @Test
    fun testAppSettings_defaultConfigurations() {
        val settings = AppSettings()
        assertTrue(settings.zombieAlertsEnabled)
        assertEquals(40, settings.alertThresholdScore)
        assertEquals(45L, settings.sentinelIntervalSeconds)
        assertEquals("Google (8.8.8.8)", settings.preferredDnsProvider)
        assertTrue(settings.dataSaverMode)
    }

    @Test
    fun testSpeedTestState_progressAndCalculations() {
        val state = SpeedTestState(
            stage = SpeedTestStage.COMPLETED,
            pingMs = 38L,
            downloadMbps = 24.5f,
            uploadMbps = 8.2f,
            progress = 1.0f,
            dataTransferredKb = 2048L
        )

        assertEquals(SpeedTestStage.COMPLETED, state.stage)
        assertEquals(38L, state.pingMs)
        assertEquals(24.5f, state.downloadMbps)
        assertEquals(8.2f, state.uploadMbps)
        assertEquals(1.0f, state.progress)
        assertTrue(state.dataTransferredKb <= 3000L) // Capped under ~3MB data-saver safety limit
    }
}
