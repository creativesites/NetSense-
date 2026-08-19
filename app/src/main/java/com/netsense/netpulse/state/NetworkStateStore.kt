package com.netsense.netpulse.state

import com.netsense.netpulse.model.ConnectionPresentation
import com.netsense.netpulse.model.NetworkClassification
import com.netsense.netpulse.model.NetworkSnapshot
import com.netsense.netpulse.model.ProductStatus
import com.netsense.netpulse.model.UsabilityScoreResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Which subsystem produced an [AuthoritativeNetworkState] - kept only for debugging/analytics,
 *  never surfaced to the user and never used to prefer one number over another (freshness,
 *  via [AuthoritativeNetworkState.timestampMs], is the only thing that decides that). */
enum class StateSource { HOME_LIVE, SENTINEL_PROBE }

/**
 * The one fact NetPulse currently knows about "is the Internet working right now, and how
 * well" - a real, timestamped measurement, never a fabricated or interpolated one. Every
 * surface that tells the user about their current connection (Home's hero card, the Sentinel
 * notification, Fix It availability) renders from this same record rather than an
 * independently recomputed copy, so two surfaces can never disagree about the same instant.
 */
data class AuthoritativeNetworkState(
    val scoreResult: UsabilityScoreResult,
    val classification: NetworkClassification,
    val status: ProductStatus,
    val snapshot: NetworkSnapshot,
    val presentation: ConnectionPresentation,
    val isRecoveryPending: Boolean,
    val timestampMs: Long,
    val source: StateSource
)

/**
 * Process-wide single source of truth for [AuthoritativeNetworkState] (Trust pass, Section 1:
 * "Home and Sentinel must never disagree"). NetPulseViewModel's live foreground telemetry loop
 * and NetPulseSentinelService's periodic background probe both publish into this instead of
 * keeping a private copy of "the current score" - and both render their score/status/diagnosis
 * from whatever [state] currently holds, not from the value they just locally computed. Since
 * the Activity and the started Service run in the same process (this app declares no
 * android:process for NetPulseSentinelService), a plain in-memory singleton is sufficient: both
 * ends already share this JVM's heap.
 *
 * [publish] only accepts an update that is at least as new as what's already stored, so a
 * probe that started earlier but happens to complete later (e.g. a slow Sentinel micro-probe
 * racing a fast Home tick) can never clobber a genuinely fresher reading with a stale one.
 */
object NetworkStateStore {

    private val _state = MutableStateFlow<AuthoritativeNetworkState?>(null)
    val state: StateFlow<AuthoritativeNetworkState?> = _state.asStateFlow()

    fun publish(newState: AuthoritativeNetworkState) {
        val current = _state.value
        if (current == null || newState.timestampMs >= current.timestampMs) {
            _state.value = newState
        }
    }

    /** Test-only: clears the singleton between test cases so one test's published state can't
     *  leak into the next (this store is process-global by design, which real Home/Sentinel
     *  code relies on, but that same global-ness needs an explicit reset for isolated tests). */
    fun resetForTest() {
        _state.value = null
    }
}
