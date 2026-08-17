package com.netsense.netpulse

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.netsense.netpulse.data.NetPulseDatabase
import com.netsense.netpulse.data.TelemetryObservationEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies the synthetic-data isolation guarantee (Part 12) at the ACTUAL persistence
 * boundary, not just in application logic: an in-memory Room database, real SQL queries.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TelemetryObservationDaoTest {

    private lateinit var db: NetPulseDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NetPulseDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun row(id: Long, sessionId: String, isSynthetic: Boolean) = TelemetryObservationEntity(
        id = id,
        timestamp = id * 1_000L,
        sessionId = sessionId,
        transport = "WIFI",
        dnsSuccess = true,
        tcpSuccess = true,
        httpSuccess = true,
        packetLossPct = 0f,
        consecutiveProbeFailures = 0,
        usabilityScore = 80,
        isValidated = true,
        isZombie = false,
        primaryDiagnosis = "test",
        observationQuality = if (isSynthetic) "SYNTHETIC" else "VALID",
        isSynthetic = isSynthetic
    )

    // 8. Synthetic observations must never appear in the production dataset query path.
    @Test
    fun `production queries exclude synthetic rows`() = runBlocking {
        val dao = db.telemetryObservationDao()
        dao.insert(row(1, "session-1", isSynthetic = false))
        dao.insert(row(2, "session-1", isSynthetic = true))
        dao.insert(row(3, "session-1", isSynthetic = false))

        val production = dao.getProductionObservations()

        assertEquals(2, production.size)
        assertTrue(production.all { !it.isSynthetic })
    }

    @Test
    fun `session ids with unresolved labels default to the freshly inserted rows`() = runBlocking {
        val dao = db.telemetryObservationDao()
        dao.insert(row(1, "session-1", isSynthetic = false))
        dao.insert(row(2, "session-2", isSynthetic = false))

        val pending = dao.getSessionIdsWithUnresolvedLabels()

        assertEquals(setOf("session-1", "session-2"), pending.toSet())
    }

    // 15. Resolving labels must persist the schema version, and distinguish status from value.
    @Test
    fun `updateResolvedLabels persists status and schema version, not just the boolean`() = runBlocking {
        val dao = db.telemetryObservationDao()
        val id = dao.insert(row(1, "session-1", isSynthetic = false))

        dao.updateResolvedLabels(
            id = id,
            degradation = null,
            degradationStatus = "INSUFFICIENT_DATA",
            dropout = true,
            dropoutStatus = "RESOLVED",
            cause = "RF_FADING",
            causeStatus = "RESOLVED",
            schemaVersion = 1
        )

        val updated = dao.getObservationsForSession("session-1").first()
        assertNull("INSUFFICIENT_DATA must not carry a fabricated boolean", updated.labelDegradation15s)
        assertEquals("INSUFFICIENT_DATA", updated.labelDegradation15sStatus)
        assertEquals(true, updated.labelDropout30s)
        assertEquals("RESOLVED", updated.labelDropout30sStatus)
        assertEquals(1, updated.labelSchemaVersion)

        // No longer has an unresolved dropout label, but degradation is still pending resolution
        // via a different call - getSessionIdsWithUnresolvedLabels should still surface it because
        // labelDegradation15sStatus is INSUFFICIENT_DATA, which is a terminal (not UNRESOLVED) state.
        val stillPending = dao.getSessionIdsWithUnresolvedLabels()
        assertTrue("INSUFFICIENT_DATA is terminal - it must not keep being rescanned", stillPending.isEmpty())
    }
}
