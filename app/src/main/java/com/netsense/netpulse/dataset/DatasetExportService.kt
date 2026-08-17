package com.netsense.netpulse.dataset

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.netsense.netpulse.ai.predictor.PulsePredictorConfig
import com.netsense.netpulse.data.TelemetryObservationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

enum class DatasetExportFormat(val fileExtension: String, val mimeType: String) {
    CSV("csv", "text/csv"),
    JSONL("jsonl", "application/jsonl")
}

/**
 * Exports the PRODUCTION (non-synthetic) telemetry dataset for offline PulsePredictor
 * training/experimentation (Part 13). Runs entirely on-device and writes only to
 * app-private cache storage; the caller decides whether/how to share the resulting file -
 * nothing is uploaded automatically (Part 21: telemetry stays local by default).
 *
 * The row-building functions are pure (no Android APIs) so they can be unit tested
 * directly. Only [export]/[shareIntent] touch Context/File I/O.
 *
 * Privacy: never includes secrets/API keys or raw SSID/BSSID (those were never stored on
 * the entity to begin with). `networkId` (interface name or carrier name) is one-way
 * hashed to a short, stable, non-reversible token so sessions can still be
 * cross-referenced without exposing the underlying identity in the exported file.
 */
object DatasetExportService {

    const val CSV_HEADER = "sessionId,timestamp,transport,networkIdHash,rsrpDbm,rsrqDb,sinrDb,cqi,wifiRssiDbm," +
        "dnsLatencyMs,dnsSuccess,tcpRttMs,tcpSuccess,tcpJitterMs,httpTtfbMs,httpSuccess,isCaptivePortal,httpStatusCode," +
        "packetLossPct,consecutiveProbeFailures,usabilityScore,isValidated,isZombie,observationQuality,isSynthetic," +
        "labelDegradation15s,labelDegradation15sStatus,labelDropout30s,labelDropout30sStatus," +
        "labelLikelyCause,labelLikelyCauseStatus,labelSchemaVersion,featureSchemaVersion"

    /** Reads the production dataset from Room and writes it to a shareable file in [format]. */
    suspend fun export(
        context: Context,
        rows: List<TelemetryObservationEntity>,
        format: DatasetExportFormat
    ): File = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "netpulse_ml_dataset_${System.currentTimeMillis()}.${format.fileExtension}")
        val content = when (format) {
            DatasetExportFormat.CSV -> buildCsv(rows)
            DatasetExportFormat.JSONL -> buildJsonl(rows)
        }
        file.writeText(content)
        file
    }

    fun shareIntent(context: Context, file: File, mimeType: String): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "NetPulse ML Telemetry Dataset Export")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    // --- Pure, unit-testable row building ------------------------------------------------

    fun buildCsv(rows: List<TelemetryObservationEntity>): String = buildString {
        append(CSV_HEADER).append('\n')
        rows.forEach { append(toCsvRow(it)).append('\n') }
    }

    fun buildJsonl(rows: List<TelemetryObservationEntity>): String = buildString {
        rows.forEach { append(toJsonLine(it)).append('\n') }
    }

    internal fun toCsvRow(o: TelemetryObservationEntity): String = listOf(
        csvString(o.sessionId), o.timestamp.toString(), csvString(o.transport), csvString(hashNetworkId(o.networkId)),
        o.rsrpDbm?.toString() ?: "", o.rsrqDb?.toString() ?: "", o.sinrDb?.toString() ?: "",
        o.cqi?.toString() ?: "", o.wifiRssiDbm?.toString() ?: "",
        o.dnsLatencyMs?.toString() ?: "", o.dnsSuccess.toString(), o.tcpRttMs?.toString() ?: "",
        o.tcpSuccess.toString(), o.tcpJitterMs?.toString() ?: "",
        o.httpTtfbMs?.toString() ?: "", o.httpSuccess.toString(), o.isCaptivePortal.toString(),
        o.httpStatusCode?.toString() ?: "",
        o.packetLossPct.toString(), o.consecutiveProbeFailures.toString(), o.usabilityScore.toString(),
        o.isValidated.toString(), o.isZombie.toString(),
        csvString(o.observationQuality), o.isSynthetic.toString(),
        o.labelDegradation15s?.toString() ?: "", csvString(o.labelDegradation15sStatus),
        o.labelDropout30s?.toString() ?: "", csvString(o.labelDropout30sStatus),
        csvString(o.labelLikelyCause ?: ""), csvString(o.labelLikelyCauseStatus),
        o.labelSchemaVersion.toString(), PulsePredictorConfig.FEATURE_NORMALIZATION_VERSION.toString()
    ).joinToString(",")

    internal fun toJsonLine(o: TelemetryObservationEntity): String = buildString {
        append('{')
        jsonField("sessionId", o.sessionId); append(',')
        jsonField("timestamp", o.timestamp); append(',')
        jsonField("transport", o.transport); append(',')
        jsonField("networkIdHash", hashNetworkId(o.networkId)); append(',')
        jsonField("rsrpDbm", o.rsrpDbm); append(',')
        jsonField("rsrqDb", o.rsrqDb); append(',')
        jsonField("sinrDb", o.sinrDb); append(',')
        jsonField("cqi", o.cqi); append(',')
        jsonField("wifiRssiDbm", o.wifiRssiDbm); append(',')
        jsonField("dnsLatencyMs", o.dnsLatencyMs); append(',')
        jsonField("dnsSuccess", o.dnsSuccess); append(',')
        jsonField("tcpRttMs", o.tcpRttMs); append(',')
        jsonField("tcpSuccess", o.tcpSuccess); append(',')
        jsonField("tcpJitterMs", o.tcpJitterMs); append(',')
        jsonField("httpTtfbMs", o.httpTtfbMs); append(',')
        jsonField("httpSuccess", o.httpSuccess); append(',')
        jsonField("isCaptivePortal", o.isCaptivePortal); append(',')
        jsonField("httpStatusCode", o.httpStatusCode); append(',')
        jsonField("packetLossPct", o.packetLossPct); append(',')
        jsonField("consecutiveProbeFailures", o.consecutiveProbeFailures); append(',')
        jsonField("usabilityScore", o.usabilityScore); append(',')
        jsonField("isValidated", o.isValidated); append(',')
        jsonField("isZombie", o.isZombie); append(',')
        jsonField("observationQuality", o.observationQuality); append(',')
        jsonField("isSynthetic", o.isSynthetic); append(',')
        append("\"labels\":{")
        jsonField("degradation15s", o.labelDegradation15s); append(',')
        jsonField("degradation15sStatus", o.labelDegradation15sStatus); append(',')
        jsonField("dropout30s", o.labelDropout30s); append(',')
        jsonField("dropout30sStatus", o.labelDropout30sStatus); append(',')
        jsonField("likelyCause", o.labelLikelyCause); append(',')
        jsonField("likelyCauseStatus", o.labelLikelyCauseStatus); append(',')
        jsonField("schemaVersion", o.labelSchemaVersion)
        append("},")
        jsonField("featureSchemaVersion", PulsePredictorConfig.FEATURE_NORMALIZATION_VERSION, last = true)
        append('}')
    }

    private fun StringBuilder.jsonField(name: String, value: Any?, last: Boolean = false) {
        append('"').append(name).append("\":")
        append(
            when (value) {
                null -> "null"
                is String -> "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
                is Boolean, is Int, is Long, is Float, is Double -> value.toString()
                else -> "\"$value\""
            }
        )
    }

    private fun csvString(value: String): String = "\"${value.replace("\"", "\"\"")}\""

    /** One-way, non-reversible token - never the original network identity. */
    internal fun hashNetworkId(networkId: String?): String {
        if (networkId.isNullOrBlank()) return ""
        val digest = MessageDigest.getInstance("SHA-256").digest(networkId.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(12)
    }
}
