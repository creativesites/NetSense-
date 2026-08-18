package com.netsense.netpulse.policy

import com.netsense.netpulse.model.CellularRfSnapshot
import com.netsense.netpulse.model.HealerActionItem
import com.netsense.netpulse.model.HealerActionType
import com.netsense.netpulse.model.NetworkClassification
import com.netsense.netpulse.model.NetworkSnapshot
import com.netsense.netpulse.model.ProductStatus
import com.netsense.netpulse.model.ProductStatusMapper
import com.netsense.netpulse.model.UsabilityScoreResult
import com.netsense.netpulse.model.WifiRadarSnapshot

/**
 * PulsePolicy's decision for the current tick: what the product status is, and - if
 * anything - the single least-disruptive healing action worth surfacing right now.
 *
 * [predictionAvailable] is always false. PulsePolicy decides purely from PulseCore's already
 * verified deterministic state (UsabilityEngine / RadarEngine output); there is no trained
 * pulse_predictor_v1.tflite model yet, so nothing here is allowed to lean on a prediction
 * that doesn't actually exist. When a trained model ships, this becomes the one place that
 * needs to change to let a verified prediction influence the decision - callers should never
 * infer "prediction available" from anything except this field.
 */
data class PolicyDecision(
    val state: ProductStatus,
    val recommendedAction: HealerActionItem?,
    val reason: String,
    val predictionAvailable: Boolean = false
)

/**
 * Deterministic policy layer sitting between PulseCore's diagnostics (UsabilityEngine,
 * RadarEngine, NetworkHealerEngine) and anything that acts on them (the Healer UI,
 * RecoveryOutcomeTracker). It does not compute connectivity truth itself - it only reads
 * what those engines already decided and picks the least-disruptive relevant fix, so the
 * app has one place that answers "what should we do about this" instead of that logic being
 * re-derived ad hoc at each call site.
 *
 * "Least disruptive" is expressed via [HealerActionItem.impactLevel], which
 * NetworkHealerEngine already assigns per action ("Preventative" < "Medium Impact" <
 * "High Impact") - PulsePolicy reuses that existing metadata rather than inventing a second
 * ranking scheme.
 */
object PulsePolicyEngine {

    private val impactRank = mapOf(
        "Preventative" to 0,
        "Medium Impact" to 1,
        "High Impact" to 2
    )

    fun decide(
        snapshot: NetworkSnapshot,
        scoreResult: UsabilityScoreResult,
        classification: NetworkClassification,
        wifiRadar: WifiRadarSnapshot,
        cellularRf: CellularRfSnapshot,
        healerActions: List<HealerActionItem>,
        isProbing: Boolean = false,
        isRecoveryPending: Boolean = false,
        justRecovered: Boolean = false
    ): PolicyDecision {
        val status = ProductStatusMapper.map(
            snapshot = snapshot,
            scoreResult = scoreResult,
            classification = classification,
            wifiRadar = wifiRadar,
            cellularRf = cellularRf,
            isProbing = isProbing,
            isRecoveryPending = isRecoveryPending,
            justRecovered = justRecovered
        )

        val action = pickLeastDisruptiveAction(status, healerActions)
        val reason = reasonFor(status, action)

        return PolicyDecision(
            state = status,
            recommendedAction = action,
            reason = reason,
            predictionAvailable = false
        )
    }

    private fun pickLeastDisruptiveAction(
        status: ProductStatus,
        actions: List<HealerActionItem>
    ): HealerActionItem? {
        if (actions.isEmpty()) return null

        val relevantTypes: Set<HealerActionType> = when (status) {
            ProductStatus.NO_INTERNET -> setOf(HealerActionType.AIRPLANE_CYCLE, HealerActionType.RESET_RADIO_INTERFACE)
            ProductStatus.CAPTIVE_PORTAL -> setOf(HealerActionType.OPEN_CAPTIVE_PORTAL)
            ProductStatus.WEAK_SIGNAL -> setOf(HealerActionType.SWITCH_NETWORK)
            ProductStatus.DEGRADED -> setOf(
                HealerActionType.OPTIMIZE_DNS,
                HealerActionType.FLUSH_SOCKET_CACHE,
                HealerActionType.OPTIMIZE_BUFFERBLOAT,
                HealerActionType.RESET_RADIO_INTERFACE
            )
            else -> emptySet()
        }

        if (relevantTypes.isEmpty()) return null

        val candidates = actions.filter { it.actionType in relevantTypes }
        if (candidates.isEmpty()) return null

        return candidates.minByOrNull { impactRank[it.impactLevel] ?: 1 }
    }

    private fun reasonFor(status: ProductStatus, action: HealerActionItem?): String = when {
        action != null -> "Diagnosed as ${status.label}. Least-disruptive available fix: ${action.title}."
        status == ProductStatus.ONLINE || status == ProductStatus.RECOVERED -> "Connection is healthy - no action needed."
        status == ProductStatus.RECOVERING -> "A recovery attempt is already in progress; waiting to confirm the outcome before recommending another action."
        status == ProductStatus.CHECKING || status == ProductStatus.CONNECTING -> "Still verifying connection state."
        else -> "Diagnosed as ${status.label}, but no matching healer action is currently available."
    }
}
