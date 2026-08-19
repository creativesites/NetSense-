package com.netsense.netpulse

import com.netsense.netpulse.model.CellularRfSnapshot
import com.netsense.netpulse.model.HealerActionItem
import com.netsense.netpulse.model.HealerActionType
import com.netsense.netpulse.model.NetworkClassification
import com.netsense.netpulse.model.NetworkSnapshot
import com.netsense.netpulse.model.NetworkTransport
import com.netsense.netpulse.model.ProductStatus
import com.netsense.netpulse.model.UsabilityRating
import com.netsense.netpulse.model.UsabilityScoreResult
import com.netsense.netpulse.model.WifiRadarSnapshot
import com.netsense.netpulse.policy.PulsePolicyEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PulsePolicyEngineTest {

    private fun action(type: HealerActionType, impact: String, id: String = type.name) = HealerActionItem(
        id = id,
        title = "$type action",
        description = "desc",
        impactLevel = impact,
        actionType = type
    )

    private fun score(rating: UsabilityRating = UsabilityRating.OPTIMAL, isZombie: Boolean = false) = UsabilityScoreResult(
        score = 90,
        rating = rating,
        primaryDiagnosis = "test",
        rootCauseSummary = "test",
        explanatoryReasons = emptyList(),
        isZombieConnection = isZombie,
        scoreBreakdown = emptyMap()
    )

    private fun snapshot(connected: Boolean = true, captivePortal: Boolean = false) = NetworkSnapshot(
        isConnected = connected,
        primaryTransport = NetworkTransport.CELLULAR,
        activeTransports = setOf(NetworkTransport.CELLULAR),
        isCaptivePortal = captivePortal
    )

    @Test
    fun `predictionAvailable is always false - no trained model exists`() {
        val decision = PulsePolicyEngine.decide(
            snapshot = snapshot(),
            scoreResult = score(),
            classification = NetworkClassification.INTERNET_OPTIMAL,
            wifiRadar = WifiRadarSnapshot(),
            cellularRf = CellularRfSnapshot(),
            healerActions = emptyList()
        )
        assertFalse(decision.predictionAvailable)
    }

    @Test
    fun `healthy state recommends no action`() {
        val decision = PulsePolicyEngine.decide(
            snapshot = snapshot(),
            scoreResult = score(),
            classification = NetworkClassification.INTERNET_OPTIMAL,
            wifiRadar = WifiRadarSnapshot(),
            cellularRf = CellularRfSnapshot(),
            healerActions = listOf(action(HealerActionType.FLUSH_SOCKET_CACHE, "Preventative"))
        )
        assertEquals(ProductStatus.ONLINE, decision.state)
        assertNull(decision.recommendedAction)
    }

    @Test
    fun `zombie state picks the airplane cycle action over an unrelated preventative one`() {
        val airplane = action(HealerActionType.AIRPLANE_CYCLE, "High Impact")
        val flush = action(HealerActionType.FLUSH_SOCKET_CACHE, "Preventative")

        val decision = PulsePolicyEngine.decide(
            snapshot = snapshot(),
            scoreResult = score(isZombie = true),
            classification = NetworkClassification.RADIO_ONLY_NO_INTERNET,
            wifiRadar = WifiRadarSnapshot(),
            cellularRf = CellularRfSnapshot(),
            healerActions = listOf(flush, airplane)
        )

        assertEquals(ProductStatus.NO_INTERNET, decision.state)
        assertEquals(airplane.id, decision.recommendedAction?.id)
    }

    @Test
    fun `degraded state prefers the least disruptive candidate action`() {
        // Two candidates both relevant to DEGRADED - Preventative should win over Medium Impact.
        val preventative = action(HealerActionType.FLUSH_SOCKET_CACHE, "Preventative")
        val medium = action(HealerActionType.OPTIMIZE_DNS, "Medium Impact")

        val decision = PulsePolicyEngine.decide(
            snapshot = snapshot(),
            scoreResult = score(rating = UsabilityRating.DEGRADED),
            classification = NetworkClassification.INTERNET_DEGRADED,
            wifiRadar = WifiRadarSnapshot(),
            cellularRf = CellularRfSnapshot(),
            healerActions = listOf(medium, preventative)
        )

        assertEquals(ProductStatus.DEGRADED, decision.state)
        assertEquals(preventative.id, decision.recommendedAction?.id)
    }

    @Test
    fun `captive portal picks the captive portal action specifically`() {
        val portal = action(HealerActionType.OPEN_CAPTIVE_PORTAL, "High Impact")
        val flush = action(HealerActionType.FLUSH_SOCKET_CACHE, "Preventative")

        val decision = PulsePolicyEngine.decide(
            snapshot = snapshot(captivePortal = true),
            scoreResult = score(rating = UsabilityRating.DEGRADED),
            classification = NetworkClassification.INTERNET_UNVALIDATED,
            wifiRadar = WifiRadarSnapshot(),
            cellularRf = CellularRfSnapshot(),
            healerActions = listOf(flush, portal)
        )

        assertEquals(ProductStatus.CAPTIVE_PORTAL, decision.state)
        assertEquals(portal.id, decision.recommendedAction?.id)
    }

    @Test
    fun `no relevant action available leaves recommendedAction null rather than guessing`() {
        val decision = PulsePolicyEngine.decide(
            snapshot = snapshot(),
            scoreResult = score(isZombie = true),
            classification = NetworkClassification.RADIO_ONLY_NO_INTERNET,
            wifiRadar = WifiRadarSnapshot(),
            cellularRf = CellularRfSnapshot(),
            healerActions = listOf(action(HealerActionType.FLUSH_SOCKET_CACHE, "Preventative"))
        )

        assertEquals(ProductStatus.NO_INTERNET, decision.state)
        assertNull(decision.recommendedAction)
    }

    // decideForKnownStatus is what NetPulseViewModel now uses to fold a NetworkStateStore
    // reading (already carrying an authoritative ProductStatus computed at publish time) back
    // into Home's PolicyDecision/Fix It wiring, without re-deriving status from a possibly
    // older generation of radar data - see NetworkStateStoreTest for the sharing mechanism
    // itself.

    @Test
    fun `decideForKnownStatus never re-derives status - it trusts the status it was given`() {
        val airplane = action(HealerActionType.AIRPLANE_CYCLE, "High Impact")
        val decision = PulsePolicyEngine.decideForKnownStatus(
            status = ProductStatus.NO_INTERNET,
            healerActions = listOf(airplane)
        )
        assertEquals(ProductStatus.NO_INTERNET, decision.state)
        assertEquals(airplane.id, decision.recommendedAction?.id)
    }

    @Test
    fun `decideForKnownStatus picks the same least-disruptive action as decide for the same status`() {
        val preventative = action(HealerActionType.FLUSH_SOCKET_CACHE, "Preventative")
        val medium = action(HealerActionType.OPTIMIZE_DNS, "Medium Impact")

        val viaDecide = PulsePolicyEngine.decide(
            snapshot = snapshot(),
            scoreResult = score(rating = UsabilityRating.DEGRADED),
            classification = NetworkClassification.INTERNET_DEGRADED,
            wifiRadar = WifiRadarSnapshot(),
            cellularRf = CellularRfSnapshot(),
            healerActions = listOf(medium, preventative)
        )
        val viaKnownStatus = PulsePolicyEngine.decideForKnownStatus(
            status = ProductStatus.DEGRADED,
            healerActions = listOf(medium, preventative)
        )

        assertEquals(viaDecide.recommendedAction?.id, viaKnownStatus.recommendedAction?.id)
    }

    @Test
    fun `decideForKnownStatus on a healthy status recommends no action`() {
        val decision = PulsePolicyEngine.decideForKnownStatus(
            status = ProductStatus.ONLINE,
            healerActions = listOf(action(HealerActionType.FLUSH_SOCKET_CACHE, "Preventative"))
        )
        assertEquals(ProductStatus.ONLINE, decision.state)
        assertNull(decision.recommendedAction)
        assertFalse(decision.predictionAvailable)
    }
}
