package com.example

import com.example.engine.FaultSimulator
import com.example.engine.IssueCategory
import com.example.engine.IssueSeverity
import com.example.engine.SimulatedFaultScenario
import com.example.engine.TroubleshootEngine
import com.example.engine.UsabilityEngine
import com.example.model.NetworkSnapshot
import com.example.model.NetworkTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase4TroubleshootTest {

    @Test
    fun testTroubleshootEngine_detectsZombieConnectionRootCause() {
        val baseSnapshot = NetworkSnapshot(
            isConnected = true,
            primaryTransport = NetworkTransport.CELLULAR,
            carrierName = "Airtel Zambia",
            cellularDataNetworkType = "LTE",
            signalLevel = 4,
            signalDbm = -72
        )

        val simSnapshot = FaultSimulator.generateSimulatedSnapshot(SimulatedFaultScenario.ZOMBIE_RADIO, baseSnapshot)
        val simProbe = FaultSimulator.generateSimulatedProbe(SimulatedFaultScenario.ZOMBIE_RADIO, null)
        val score = UsabilityEngine.calculateScore(simSnapshot, simProbe)

        val findings = TroubleshootEngine.analyze(simSnapshot, simProbe, score)

        assertTrue("Should have findings for zombie link", findings.isNotEmpty())
        val zombieFinding = findings.firstOrNull { it.category == IssueCategory.ZOMBIE_LINK }
        assertTrue("Zombie link finding must be present", zombieFinding != null)
        assertEquals(IssueSeverity.CRITICAL, zombieFinding?.severity)
        assertTrue("Must include remediation steps", (zombieFinding?.steps?.size ?: 0) >= 2)
    }

    @Test
    fun testTroubleshootEngine_detectsDnsBlackhole() {
        val baseSnapshot = NetworkSnapshot(
            isConnected = true,
            primaryTransport = NetworkTransport.WIFI,
            signalLevel = 4
        )

        val simSnapshot = FaultSimulator.generateSimulatedSnapshot(SimulatedFaultScenario.DNS_BLACKHOLE, baseSnapshot)
        val simProbe = FaultSimulator.generateSimulatedProbe(SimulatedFaultScenario.DNS_BLACKHOLE, null)
        val score = UsabilityEngine.calculateScore(simSnapshot, simProbe)

        val findings = TroubleshootEngine.analyze(simSnapshot, simProbe, score)

        val dnsFinding = findings.firstOrNull { it.category == IssueCategory.DNS_RESOLUTION }
        assertTrue("DNS resolution finding must be present", dnsFinding != null)
        assertEquals(IssueSeverity.HIGH, dnsFinding?.severity)
    }

    @Test
    fun testTroubleshootEngine_detectsBufferbloat() {
        val baseSnapshot = NetworkSnapshot(
            isConnected = true,
            primaryTransport = NetworkTransport.WIFI,
            signalLevel = 4
        )

        val simSnapshot = FaultSimulator.generateSimulatedSnapshot(SimulatedFaultScenario.HIGH_BUFFERBLOAT, baseSnapshot)
        val simProbe = FaultSimulator.generateSimulatedProbe(SimulatedFaultScenario.HIGH_BUFFERBLOAT, null)
        val score = UsabilityEngine.calculateScore(simSnapshot, simProbe)

        val findings = TroubleshootEngine.analyze(simSnapshot, simProbe, score)

        val bufferbloatFinding = findings.firstOrNull { it.category == IssueCategory.BUFFERBLOAT }
        assertTrue("Bufferbloat finding must be present", bufferbloatFinding != null)
        assertEquals(IssueSeverity.MODERATE, bufferbloatFinding?.severity)
    }
}
