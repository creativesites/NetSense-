package com.netsense.netpulse.dataset

import com.netsense.netpulse.model.DiagnosticProbeResult
import com.netsense.netpulse.model.NetworkSnapshot
import com.netsense.netpulse.model.NetworkTransport

/**
 * Classifies a single telemetry observation's [ObservationQuality] at write time, based on
 * what was actually available this cycle - never assumes a field is good just because a
 * default was filled in.
 */
object ObservationQualityClassifier {

    fun classify(
        snapshot: NetworkSnapshot,
        probe: DiagnosticProbeResult?,
        isRfSignalMeasured: Boolean,
        isSynthetic: Boolean
    ): ObservationQuality {
        if (isSynthetic) return ObservationQuality.SYNTHETIC
        if (snapshot.timestamp <= 0L) return ObservationQuality.INVALID

        val hasActiveProbeLayer = probe != null && !probe.isRunning &&
            (probe.averageDnsMs != null || probe.averageTcpMs != null || probe.averageHttpMs != null)

        val expectsRfSignal = snapshot.isConnected &&
            (snapshot.primaryTransport == NetworkTransport.CELLULAR || snapshot.primaryTransport == NetworkTransport.WIFI)
        val rfExpectedButMissing = expectsRfSignal && !isRfSignalMeasured

        return if (hasActiveProbeLayer && !rfExpectedButMissing) {
            ObservationQuality.VALID
        } else {
            ObservationQuality.PARTIAL
        }
    }
}
