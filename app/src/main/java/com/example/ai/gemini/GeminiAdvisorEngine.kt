package com.example.ai.gemini

import android.util.Log
import com.example.BuildConfig
import com.example.ai.mind.PulseMindExplanation
import com.example.ai.mind.RuleBasedPulseMind
import com.example.ai.predictor.PulsePrediction
import com.example.model.CellularRfSnapshot
import com.example.model.DiagnosticProbeResult
import com.example.model.NetworkSnapshot
import com.example.model.UsabilityScoreResult
import com.example.model.WifiRadarSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class GeminiAiConsultation(
    val query: String,
    val response: String,
    val timestamp: Long = System.currentTimeMillis(),
    val source: String = "Gemini 3.5 Flash",
    val isLoading: Boolean = false,
    val suggestedFixes: List<String> = emptyList()
)

class GeminiAdvisorEngine {

    private val fallbackMind = RuleBasedPulseMind()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun consult(
        prompt: String,
        snapshot: NetworkSnapshot,
        scoreResult: UsabilityScoreResult?,
        probeResult: DiagnosticProbeResult?,
        prediction: PulsePrediction?,
        rfSnapshot: CellularRfSnapshot?,
        wifiSnapshot: WifiRadarSnapshot?
    ): GeminiAiConsultation = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Throwable) {
            ""
        }

        // Build rich telemetry context
        val telemetryContext = buildString {
            appendLine("--- CURRENT NETWORK TELEMETRY ---")
            appendLine("Connected: ${snapshot.isConnected}, Validated: ${snapshot.isValidated}")
            appendLine("Transport: ${snapshot.primaryTransport.name}")
            appendLine("Interface: ${snapshot.interfaceName ?: "Unknown"}")
            appendLine("Usability Score: ${scoreResult?.score ?: 0}/100 (${scoreResult?.rating?.name ?: "UNKNOWN"})")
            appendLine("Zombie Connection: ${scoreResult?.isZombieConnection ?: false}")
            appendLine("Primary Diagnosis: ${scoreResult?.primaryDiagnosis ?: "No diagnosis"}")
            if (probeResult != null) {
                appendLine("DNS Latency: ${probeResult.averageDnsMs} ms (Success: ${probeResult.overallDnsSuccess})")
                appendLine("TCP RTT: ${probeResult.averageTcpMs} ms (Jitter: ${probeResult.tcpJitterMs} ms, Success: ${probeResult.overallTcpSuccess})")
                appendLine("HTTP TTFB: ${probeResult.averageHttpMs} ms (Success: ${probeResult.overallHttpSuccess})")
                appendLine("Packet Loss: ${probeResult.packetLossPct}%")
            }
            if (rfSnapshot != null) {
                appendLine("Cellular RSRP: ${rfSnapshot.rsrpDbm ?: "N/A"} dBm, SINR: ${rfSnapshot.sinrDb ?: "N/A"} dB, Network: ${rfSnapshot.dataNetworkType}")
            }
            if (wifiSnapshot != null) {
                appendLine("Wi-Fi SSID: ${wifiSnapshot.ssid ?: "Unknown"}, RSSI: ${wifiSnapshot.rssiDbm} dBm, Freq: ${wifiSnapshot.frequencyMhz} MHz, LinkSpeed: ${wifiSnapshot.linkSpeedMbps} Mbps")
            }
            if (prediction != null && prediction.state.name == "VALID_PREDICTION") {
                appendLine("ML 30s Dropout Risk: ${(prediction.dropoutProbability30s * 100).toInt()}%")
                appendLine("ML 15s Degradation Risk: ${(prediction.degradationProbability15s * 100).toInt()}%")
                appendLine("ML Likely Cause: ${prediction.likelyCause.label}")
            }
        }

        // If no API key or placeholder, provide high-grade local reasoning fallback
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY" || apiKey == "YOUR_GEMINI_API_KEY") {
            val localExplanation = fallbackMind.explain(snapshot, scoreResult, probeResult, prediction, rfSnapshot, wifiSnapshot)
            val fallbackResponse = generateLocalConsultation(prompt, localExplanation, scoreResult, probeResult, snapshot)
            return@withContext GeminiAiConsultation(
                query = prompt,
                response = fallbackResponse,
                source = "PulseMind Local Reasoning Engine",
                suggestedFixes = localExplanation.keySignals
            )
        }

        try {
            val systemInstruction = """
                You are NetPulse AI Network Diagnostics Assistant, an expert mobile and wireless network engineer.
                Analyze the provided real-time Android network telemetry and answer the user's inquiry concisely, objectively, and with actionable steps.
                Avoid generic fluff. Provide root-cause clarity and specific practical remedies (e.g., DNS switches, channel interference mitigation, captive portal bypass, Wi-Fi roaming threshold adjustments).
            """.trimIndent()

            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "$systemInstruction\n\n$telemetryContext\n\nUser Question: $prompt")
                            })
                        })
                    })
                })
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = requestJson.toString().toRequestBody(mediaType)
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.w("GeminiAdvisor", "API Error ${response.code}: $responseBody")
                val localExplanation = fallbackMind.explain(snapshot, scoreResult, probeResult, prediction, rfSnapshot, wifiSnapshot)
                return@withContext GeminiAiConsultation(
                    query = prompt,
                    response = generateLocalConsultation(prompt, localExplanation, scoreResult, probeResult, snapshot),
                    source = "PulseMind Local Fallback",
                    suggestedFixes = localExplanation.keySignals
                )
            }

            val jsonObject = JSONObject(responseBody)
            val candidates = jsonObject.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val text = parts?.optJSONObject(0)?.optString("text") ?: "Analysis completed."

            GeminiAiConsultation(
                query = prompt,
                response = text.trim(),
                source = "Gemini 3.5 Flash",
                suggestedFixes = listOf("Check Layer 3 latency", "Verify captive login", "Test DNS reachability")
            )
        } catch (e: Exception) {
            Log.e("GeminiAdvisor", "Exception during Gemini consultation", e)
            val localExplanation = fallbackMind.explain(snapshot, scoreResult, probeResult, prediction, rfSnapshot, wifiSnapshot)
            GeminiAiConsultation(
                query = prompt,
                response = generateLocalConsultation(prompt, localExplanation, scoreResult, probeResult, snapshot),
                source = "PulseMind Offline Engine",
                suggestedFixes = localExplanation.keySignals
            )
        }
    }

    private fun generateLocalConsultation(
        prompt: String,
        explanation: PulseMindExplanation,
        scoreResult: UsabilityScoreResult?,
        probeResult: DiagnosticProbeResult?,
        snapshot: NetworkSnapshot
    ): String {
        val lower = prompt.lowercase()
        return buildString {
            if (lower.contains("ping") || lower.contains("latency") || lower.contains("jitter")) {
                appendLine("⚡ **Latency & RTT Analysis**:")
                val tcp = probeResult?.averageTcpMs ?: 0
                val jitter = probeResult?.tcpJitterMs ?: 0
                if (tcp > 150 || jitter > 30) {
                    appendLine("Your connection exhibits elevated round-trip time ($tcp ms) with high packet jitter ($jitter ms). This is likely caused by uplink congestion or bufferbloat on the local gateway.")
                    appendLine("• Recommendation: Switch DNS to 1.1.1.1 or 8.8.8.8, and prioritize QoS on router.")
                } else {
                    appendLine("Round-trip latency is currently stable at $tcp ms with minimal jitter ($jitter ms). Interactive and gaming streams will perform optimally.")
                }
            } else if (lower.contains("signal") || lower.contains("rssi") || lower.contains("rf") || lower.contains("drop")) {
                appendLine("📡 **Physical Layer & Signal Assessment**:")
                appendLine(explanation.narrative)
                appendLine("• Suggested Fix: ${explanation.recoveryRecommendation}")
            } else if (lower.contains("captive") || lower.contains("zombie") || lower.contains("login")) {
                appendLine("🛡️ **Captive Portal & Ghost Link Inspection**:")
                if (scoreResult?.isZombieConnection == true) {
                    appendLine("ALERT: Zombie connection detected! Your interface reports active association, but Layer 4 TCP handshakes and HTTP probes are failing 100%.")
                    appendLine("• Recommendation: Open captive portal sign-in page or toggle Wi-Fi.")
                } else {
                    appendLine("No captive portal interception detected. WAN transit routes are verified open.")
                }
            } else {
                appendLine("🧠 **AI Diagnostic Assessment**:")
                appendLine(explanation.headline)
                appendLine()
                appendLine(explanation.narrative)
                appendLine()
                appendLine("💡 **Actionable Remedy**: ${explanation.recoveryRecommendation}")
            }
        }
    }
}
