package com.netsense.netpulse.analytics

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.netsense.netpulse.data.DiagnosticLogEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiagnosticExporter {

    suspend fun exportLogsToCsv(context: Context, logs: List<DiagnosticLogEntity>): File =
        withContext(Dispatchers.IO) {
            val file = File(context.cacheDir, "netpulse_diagnostics_${System.currentTimeMillis()}.csv")
            file.bufferedWriter().use { writer ->
                // CSV Header
                writer.write("Timestamp,Date_Time,Score,Rating,Classification,IsZombie,PrimaryDiagnosis,RootCause,Transport,Carrier,NetworkType,SignalLevel,SignalDbm,DnsMs,DnsSuccess,TcpMs,TcpSuccess,TcpJitterMs,HttpMs,HttpSuccess,HttpStatusCode,PacketLossPct,DurationMs,ProbeMode,Notes\n")

                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                logs.forEach { log ->
                    val line = listOf(
                        log.timestamp.toString(),
                        "\"${sdf.format(Date(log.timestamp))}\"",
                        log.score.toString(),
                        "\"${log.rating}\"",
                        "\"${log.classification}\"",
                        log.isZombieConnection.toString(),
                        "\"${log.primaryDiagnosis.replace("\"", "\"\"")}\"",
                        "\"${log.rootCauseSummary.replace("\"", "\"\"")}\"",
                        "\"${log.transport}\"",
                        "\"${log.carrierName ?: "N/A"}\"",
                        "\"${log.cellularNetworkType ?: "N/A"}\"",
                        log.signalLevel?.toString() ?: "",
                        log.signalDbm?.toString() ?: "",
                        log.dnsLatencyMs?.toString() ?: "",
                        log.dnsSuccess.toString(),
                        log.tcpHandshakeMs?.toString() ?: "",
                        log.tcpSuccess.toString(),
                        log.tcpJitterMs?.toString() ?: "",
                        log.httpLatencyMs?.toString() ?: "",
                        log.httpSuccess.toString(),
                        log.httpStatusCode?.toString() ?: "",
                        log.packetLossPct.toString(),
                        log.durationMs.toString(),
                        "\"${log.probeMode}\"",
                        "\"${log.notes ?: ""}\""
                    ).joinToString(",")

                    writer.write(line + "\n")
                }
            }
            file
        }

    fun shareCsvIntent(context: Context, csvFile: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            csvFile
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "NetPulse Diagnostic Logs Export")
            putExtra(Intent.EXTRA_TEXT, "Attached is the NetPulse network usability diagnostic log export.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
