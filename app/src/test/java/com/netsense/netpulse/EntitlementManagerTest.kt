package com.netsense.netpulse

import com.netsense.netpulse.monetization.AdResult
import com.netsense.netpulse.monetization.AdSlot
import com.netsense.netpulse.monetization.FeatureTier
import com.netsense.netpulse.monetization.FreeTierEntitlementManager
import com.netsense.netpulse.monetization.NoOpAdProvider
import com.netsense.netpulse.monetization.PremiumFeature
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the "never fabricate a premium unlock" invariant - there is no billing integration
 * yet, so every feature must report FREE for every user until a real implementation exists.
 */
class EntitlementManagerTest {

    @Test
    fun `every premium feature is currently FREE - there is no fake unlock`() {
        val manager = FreeTierEntitlementManager()
        for (feature in PremiumFeature.values()) {
            assertEquals(FeatureTier.FREE, manager.tierFor(feature))
            assertTrue(manager.isUnlocked(feature))
        }
    }

    @Test
    fun `the no-op ad provider never fabricates an ad`() = runBlocking {
        val provider = NoOpAdProvider()
        for (slot in AdSlot.values()) {
            assertEquals(AdResult.NoAdAvailable, provider.requestAd(slot))
        }
    }
}
