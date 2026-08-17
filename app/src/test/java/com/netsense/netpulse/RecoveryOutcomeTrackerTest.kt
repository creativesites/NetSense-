package com.netsense.netpulse

import com.netsense.netpulse.data.RecoveryOutcomeDao
import com.netsense.netpulse.data.RecoveryOutcomeEntity
import com.netsense.netpulse.data.RecoveryOutcomeStatus
import com.netsense.netpulse.dataset.RecoveryOutcomeTracker
import com.netsense.netpulse.model.NetworkClassification
import com.netsense.netpulse.model.NetworkSnapshot
import com.netsense.netpulse.model.NetworkTransport
import com.netsense.netpulse.model.UsabilityRating
import com.netsense.netpulse.model.UsabilityScoreResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** In-memory fake so RecoveryOutcomeTracker can be tested as pure Kotlin logic, without Room/Robolectric. */
private class FakeRecoveryOutcomeDao : RecoveryOutcomeDao {
    private var nextId = 1L
    val rows = LinkedHashMap<Long, RecoveryOutcomeEntity>()
    private val flow = MutableStateFlow<List<RecoveryOutcomeEntity>>(emptyList())

    override suspend fun insert(outcome: RecoveryOutcomeEntity): Long {
        val id = nextId++
        rows[id] = outcome.copy(id = id)
        flow.value = rows.values.toList()
        return id
    }

    override suspend fun getPending(): List<RecoveryOutcomeEntity> =
        rows.values.filter { it.status == RecoveryOutcomeStatus.PENDING.name }

    override fun getAllFlow(): Flow<List<RecoveryOutcomeEntity>> = flow

    override suspend fun resolveOutcome(
        id: Long,
        status: String,
        transportAfter: String?,
        postUsabilityScore: Int?,
        postDiagnosis: String?,
        resolvedAtTimestamp: Long?,
        timeToRecoveryMs: Long?,
        internetActuallyReturned: Boolean?
    ) {
        val existing = rows[id] ?: return
        rows[id] = existing.copy(
            status = status,
            transportAfter = transportAfter,
            postUsabilityScore = postUsabilityScore,
            postDiagnosis = postDiagnosis,
            resolvedAtTimestamp = resolvedAtTimestamp,
            timeToRecoveryMs = timeToRecoveryMs,
            internetActuallyReturned = internetActuallyReturned
        )
        flow.value = rows.values.toList()
    }
}

class RecoveryOutcomeTrackerTest {

    private fun snapshot(validated: Boolean, transport: NetworkTransport = NetworkTransport.WIFI) = NetworkSnapshot(
        isConnected = true,
        isValidated = validated,
        primaryTransport = transport,
        classification = if (validated) NetworkClassification.INTERNET_VALIDATED else NetworkClassification.RADIO_ONLY_NO_INTERNET
    )

    private fun score(value: Int, zombie: Boolean = false) = UsabilityScoreResult(
        score = value,
        rating = UsabilityRating.fromScore(value),
        primaryDiagnosis = "test",
        rootCauseSummary = "test",
        explanatoryReasons = emptyList(),
        isZombieConnection = zombie,
        scoreBreakdown = emptyMap()
    )

    // 11. Recovery action completes but Internet remains unavailable -> FAILED.
    @Test
    fun `recovery times out without real validation and is marked failed`() = runBlocking {
        val dao = FakeRecoveryOutcomeDao()
        val tracker = RecoveryOutcomeTracker(dao)

        val id = tracker.recordAttempt("session-1", "AIRPLANE_CYCLE", snapshot(validated = false), score(10, zombie = true), nowMs = 0L)

        // Still broken, well past the timeout window.
        tracker.resolvePending(
            snapshot(validated = false),
            score(12, zombie = true),
            nowMs = RecoveryOutcomeTracker.RECOVERY_TIMEOUT_MS + 1_000
        )

        val result = dao.rows.getValue(id)
        assertEquals(RecoveryOutcomeStatus.FAILED.name, result.status)
        assertEquals(false, result.internetActuallyReturned)
    }

    // 12. Recovery action followed by validated Internet restoration -> SUCCEEDED.
    @Test
    fun `recovery followed by real validation is marked succeeded`() = runBlocking {
        val dao = FakeRecoveryOutcomeDao()
        val tracker = RecoveryOutcomeTracker(dao)

        val id = tracker.recordAttempt("session-1", "AIRPLANE_CYCLE", snapshot(validated = false), score(10, zombie = true), nowMs = 0L)

        tracker.resolvePending(
            snapshot(validated = true),
            score(80, zombie = false),
            nowMs = RecoveryOutcomeTracker.MIN_SETTLE_MS + 1_000
        )

        val result = dao.rows.getValue(id)
        assertEquals(RecoveryOutcomeStatus.SUCCEEDED.name, result.status)
        assertEquals(true, result.internetActuallyReturned)
    }

    @Test
    fun `an action completing does not by itself count as success`() {
        // This is really an architectural assertion: RecoveryOutcomeTracker.recordAttempt only
        // ever writes a PENDING row (see FakeRecoveryOutcomeDao.insert flow above); nothing in
        // this class ever marks SUCCEEDED at attempt-recording time - only resolvePending, using
        // real post-attempt snapshot/score, can.
        runBlocking {
            val dao = FakeRecoveryOutcomeDao()
            val tracker = RecoveryOutcomeTracker(dao)
            val id = tracker.recordAttempt("session-1", "OPEN_CAPTIVE_PORTAL", snapshot(validated = false), score(20))
            assertEquals(RecoveryOutcomeStatus.PENDING.name, dao.rows.getValue(id).status)
        }
    }

    @Test
    fun `pending attempts are not judged before the settle grace period`() = runBlocking {
        val dao = FakeRecoveryOutcomeDao()
        val tracker = RecoveryOutcomeTracker(dao)
        val id = tracker.recordAttempt("session-1", "FLUSH_SOCKET_CACHE", snapshot(validated = false), score(20), nowMs = 0L)

        // Even though validation looks recovered, it's too soon to trust it settled.
        tracker.resolvePending(snapshot(validated = true), score(90), nowMs = RecoveryOutcomeTracker.MIN_SETTLE_MS - 500)

        assertEquals(RecoveryOutcomeStatus.PENDING.name, dao.rows.getValue(id).status)
        assertNull(dao.rows.getValue(id).resolvedAtTimestamp)
    }
}
