package com.netsense.netpulse

import com.netsense.netpulse.data.TelemetryObservationEntity
import com.netsense.netpulse.dataset.LabelResolutionStatus
import com.netsense.netpulse.dataset.LabelResolver
import com.netsense.netpulse.dataset.LabelSemantics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelResolverTest {

    private fun obs(
        id: Long,
        timestampMs: Long,
        sessionId: String = "session-1",
        score: Int = 90,
        isZombie: Boolean = false,
        isValidated: Boolean = true,
        dnsSuccess: Boolean = true,
        tcpSuccess: Boolean = true,
        httpSuccess: Boolean = true
    ) = TelemetryObservationEntity(
        id = id,
        timestamp = timestampMs,
        sessionId = sessionId,
        transport = "WIFI",
        dnsSuccess = dnsSuccess,
        tcpSuccess = tcpSuccess,
        httpSuccess = httpSuccess,
        packetLossPct = 0f,
        consecutiveProbeFailures = 0,
        usabilityScore = score,
        isValidated = isValidated,
        isZombie = isZombie,
        primaryDiagnosis = "test",
        observationQuality = "VALID"
    )

    // 1. Healthy observation followed by no degradation -> degradation15s = false once fully observed.
    @Test
    fun `healthy observation with no future degradation resolves false`() {
        val rows = listOf(
            obs(1, 0),
            obs(2, 5_000),
            obs(3, 10_000),
            obs(4, 15_000) // covers the full 15s window with no gap
        )
        val nowMs = 15_000L + LabelResolver.SESSION_STALE_MS + 1 // session closed, doesn't matter, already fully covered
        val resolved = LabelResolver.resolveSession(rows, nowMs)[1]!!

        assertEquals(LabelResolutionStatus.RESOLVED, resolved.degradation15s.status)
        assertEquals(false, resolved.degradation15s.value)
    }

    // 2. Healthy observation followed by degradation at +10s -> degradation15s = true.
    @Test
    fun `degradation inside the 15s window resolves true`() {
        val rows = listOf(
            obs(1, 0, score = 90),
            obs(2, 10_000, score = 40) // crosses below the GOOD-band threshold
        )
        val resolved = LabelResolver.resolveSession(rows, nowMs = 10_000L)[1]!!

        assertEquals(LabelResolutionStatus.RESOLVED, resolved.degradation15s.status)
        assertEquals(true, resolved.degradation15s.value)
    }

    // 3. Healthy observation followed by dropout at +20s -> dropout30s = true.
    @Test
    fun `dropout inside the 30s window resolves true`() {
        val rows = listOf(
            obs(1, 0),
            obs(2, 20_000, isZombie = true, isValidated = true)
        )
        val resolved = LabelResolver.resolveSession(rows, nowMs = 20_000L)[1]!!

        assertEquals(LabelResolutionStatus.RESOLVED, resolved.dropout30s.status)
        assertEquals(true, resolved.dropout30s.value)
        // A zombie transition is unambiguous enough to attribute SOME evidence-based cause or remain UNLABELED - either is fine,
        // but the resolver must never crash or attribute a cause when isDropoutState was reached only via isZombie with no
        // other distinguishing signal captured on this fixture.
    }

    // 4. Dropout occurring at +35s (outside the 30s window, with the window itself fully, gaplessly observed) -> dropout30s = false.
    @Test
    fun `dropout outside the 30s window resolves false`() {
        val rows = listOf(
            obs(1, 0),
            obs(2, 10_000),
            obs(3, 20_000),
            obs(4, 30_000), // fully covers [0, 30000] with no event and no gap > MAX_TRUSTED_INTRA_WINDOW_GAP_MS
            obs(5, 35_000, isZombie = true) // the actual dropout, outside the window
        )
        val resolved = LabelResolver.resolveSession(rows, nowMs = 35_000L)[1]!!

        assertEquals(LabelResolutionStatus.RESOLVED, resolved.dropout30s.status)
        assertEquals(false, resolved.dropout30s.value)
    }

    // 5. Latest observation without 30s of future data, session still active -> UNRESOLVED (not false).
    @Test
    fun `insufficient future data with an open session stays unresolved`() {
        val rows = listOf(obs(1, 0))
        val nowMs = 1_000L // well within SESSION_STALE_MS of the last row - session is still "live"

        val resolved = LabelResolver.resolveSession(rows, nowMs)[1]!!

        assertEquals(LabelResolutionStatus.UNRESOLVED, resolved.dropout30s.status)
        assertNull("must not guess a boolean value while unresolved", resolved.dropout30s.value)
    }

    // 6. Application stopped collecting (session gone stale) before the window completed -> INSUFFICIENT_DATA, never false.
    @Test
    fun `insufficient future data with a stale closed session is insufficient data, not false`() {
        val rows = listOf(obs(1, 0))
        val nowMs = LabelResolver.SESSION_STALE_MS + 60_000L // long past staleness - session will never receive more rows

        val resolved = LabelResolver.resolveSession(rows, nowMs)[1]!!

        assertEquals(LabelResolutionStatus.INSUFFICIENT_DATA, resolved.dropout30s.status)
        assertNull("INSUFFICIENT_DATA must never carry a false value pretending to be a measurement", resolved.dropout30s.value)
    }

    // 13. Duplicate/identical timestamps must be handled deterministically (no crash, repeatable output).
    @Test
    fun `duplicate timestamps resolve deterministically`() {
        val rows = listOf(
            obs(1, 1_000),
            obs(2, 1_000), // exact duplicate timestamp
            obs(3, 20_000)
        )
        val first = LabelResolver.resolveSession(rows, nowMs = 20_000L)
        val second = LabelResolver.resolveSession(rows, nowMs = 20_000L)

        assertEquals(first.keys, second.keys)
        first.keys.forEach { id ->
            assertEquals(first[id]!!.dropout30s, second[id]!!.dropout30s)
            assertEquals(first[id]!!.degradation15s, second[id]!!.degradation15s)
        }
    }

    // 14. A large intra-window gap must not silently masquerade as "nothing happened".
    @Test
    fun `large intra-window gap prevents a false resolution`() {
        val rows = listOf(
            obs(1, 0),
            // next row is 25s later, inside the 30s dropout window and no event, but the gap itself
            // (> MAX_TRUSTED_INTRA_WINDOW_GAP_MS) means we cannot trust that nothing happened in between
            obs(2, 25_000)
        )
        val nowMs = 25_000L // window nominally "covered" up to 25s but not fully to 30s either
        val resolved = LabelResolver.resolveSession(rows, nowMs)[1]!!

        // Not RESOLVED(false) - either still waiting or permanently unknown, never a confident false.
        assertTrue(resolved.dropout30s.status != LabelResolutionStatus.RESOLVED || resolved.dropout30s.value != false)
    }

    // 15. The resolved schema version must always be persisted alongside the label.
    @Test
    fun `resolved labels carry the current label schema version`() {
        val rows = listOf(obs(1, 0), obs(2, 20_000, isZombie = true))
        val resolved = LabelResolver.resolveSession(rows, nowMs = 20_000L)[1]!!

        assertEquals(LabelSemantics.LABEL_SCHEMA_VERSION, resolved.schemaVersion)
    }

    @Test
    fun `an observation already in a dropout state does not get re-flagged just for staying down`() {
        // Part 6/8: only the transition is an event, not every subsequent row while still broken.
        // Row 1 is already broken with full, gapless coverage of its own 30s window during which
        // the network simply stays broken (no NEW transition) - its own forward label must be
        // false, not true, since nothing NEW happened after it.
        val rows = listOf(
            obs(1, 0, isZombie = true),
            obs(2, 5_000, isZombie = true),
            obs(3, 10_000, isZombie = true),
            obs(4, 15_000, isZombie = true),
            obs(5, 20_000, isZombie = true),
            obs(6, 25_000, isZombie = true),
            obs(7, 30_000, isZombie = true)
        )
        val resolved = LabelResolver.resolveSession(rows, nowMs = 30_000L)[1]!!

        assertEquals(LabelResolutionStatus.RESOLVED, resolved.dropout30s.status)
        assertEquals(false, resolved.dropout30s.value)
    }
}
