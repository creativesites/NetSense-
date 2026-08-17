package com.netsense.netpulse

import com.netsense.netpulse.data.TelemetryObservationEntity
import com.netsense.netpulse.dataset.DatasetExportService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DatasetExportServiceTest {

    private fun row(networkId: String? = "wlan0", isSynthetic: Boolean = false) = TelemetryObservationEntity(
        id = 1,
        timestamp = 1_700_000_000_000L,
        sessionId = "session-1",
        transport = "WIFI",
        networkId = networkId,
        dnsSuccess = true,
        tcpSuccess = true,
        httpSuccess = true,
        packetLossPct = 0f,
        consecutiveProbeFailures = 0,
        usabilityScore = 88,
        isValidated = true,
        isZombie = false,
        primaryDiagnosis = "Optimal",
        observationQuality = "VALID",
        isSynthetic = isSynthetic,
        labelDegradation15s = false,
        labelDegradation15sStatus = "RESOLVED",
        labelDropout30s = null,
        labelDropout30sStatus = "UNRESOLVED",
        labelSchemaVersion = 1
    )

    @Test
    fun `csv export includes a header and one row per observation`() {
        val csv = DatasetExportService.buildCsv(listOf(row(), row()))
        val lines = csv.trim().lines()

        assertEquals(DatasetExportService.CSV_HEADER, lines[0])
        assertEquals(3, lines.size) // header + 2 rows
    }

    @Test
    fun `csv export never contains the raw network identity`() {
        val csv = DatasetExportService.buildCsv(listOf(row(networkId = "MyHomeWifi-5G")))
        assertFalse("raw network identity must never appear in the export", csv.contains("MyHomeWifi-5G"))
    }

    @Test
    fun `jsonl export produces one parseable-looking json object per line with a labels block`() {
        val jsonl = DatasetExportService.buildJsonl(listOf(row(), row()))
        val lines = jsonl.trim().lines()

        assertEquals(2, lines.size)
        lines.forEach { line ->
            assertTrue(line.startsWith("{") && line.endsWith("}"))
            assertTrue(line.contains("\"labels\":{"))
            assertTrue(line.contains("\"sessionId\":\"session-1\""))
            assertFalse(line.contains("wlan0"))
        }
    }

    @Test
    fun `network id hashing is deterministic and non-reversible`() {
        val hashA1 = DatasetExportService.hashNetworkId("Carrier A")
        val hashA2 = DatasetExportService.hashNetworkId("Carrier A")
        val hashB = DatasetExportService.hashNetworkId("Carrier B")

        assertEquals("same input must always hash the same way", hashA1, hashA2)
        assertTrue("different inputs should (almost certainly) hash differently", hashA1 != hashB)
        assertFalse(hashA1.contains("Carrier"))
    }

    @Test
    fun `blank or missing network id hashes to an empty token`() {
        assertEquals("", DatasetExportService.hashNetworkId(null))
        assertEquals("", DatasetExportService.hashNetworkId(""))
    }

    @Test
    fun `csv row preserves label resolution status distinctly from label value`() {
        val csv = DatasetExportService.toCsvRow(row())
        // labelDropout30s is null/UNRESOLVED - the row must carry the status, not a fabricated false.
        assertTrue(csv.contains("\"UNRESOLVED\""))
    }
}
