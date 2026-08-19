package com.netsense.netpulse.ai.gemini

import android.util.Log
import com.netsense.netpulse.BuildConfig
import com.netsense.netpulse.ai.mind.PulseMindExplanation
import com.netsense.netpulse.ai.mind.RuleBasedPulseMind
import com.netsense.netpulse.ai.predictor.PulsePrediction
import com.netsense.netpulse.model.CellularRfSnapshot
import com.netsense.netpulse.model.DiagnosticProbeResult
import com.netsense.netpulse.model.NetworkSnapshot
import com.netsense.netpulse.model.UsabilityScoreResult
import com.netsense.netpulse.model.WifiRadarSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * A response broken into the three questions a person actually has when their Internet is
 * acting up, instead of one undifferentiated paragraph: what's going on, why it's happening,
 * and what to do about it. Only populated for the local PulseMind fallback (the only path
 * actually reachable today, since no real Gemini key is configured) - the live Gemini path
 * below is left returning free-form text rather than guessing at a 3-way split of a response
 * this app has never actually received.
 */
data class StructuredAdvice(
    val whatsHappening: String,
    val why: String,
    val recommendation: String
)

data class GeminiAiConsultation(
    val query: String,
    val response: String,
    val timestamp: Long = System.currentTimeMillis(),
    val source: String = "Gemini 3.5 Flash",
    val isLoading: Boolean = false,
    val suggestedFixes: List<String> = emptyList(),
    val structured: StructuredAdvice? = null
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
            val structured = generateLocalStructuredConsultation(prompt, localExplanation, scoreResult, probeResult, snapshot)
            return@withContext GeminiAiConsultation(
                query = prompt,
                response = structured.flatten(),
                source = "PulseMind Local Reasoning Engine",
                suggestedFixes = localExplanation.keySignals,
                structured = structured
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
                val structured = generateLocalStructuredConsultation(prompt, localExplanation, scoreResult, probeResult, snapshot)
                return@withContext GeminiAiConsultation(
                    query = prompt,
                    response = structured.flatten(),
                    source = "PulseMind Local Fallback",
                    suggestedFixes = localExplanation.keySignals,
                    structured = structured
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
            val structured = generateLocalStructuredConsultation(prompt, localExplanation, scoreResult, probeResult, snapshot)
            GeminiAiConsultation(
                query = prompt,
                response = structured.flatten(),
                source = "PulseMind Offline Engine",
                suggestedFixes = localExplanation.keySignals,
                structured = structured
            )
        }
    }

    /**
     * Same underlying diagnosis RuleBasedPulseMind already computed (deterministically, from
     * real telemetry - nothing here re-derives or fabricates a verdict), split into the three
     * questions a non-technical user actually has: what's going on, why, and what to do. Each
     * prompt category below already separated a description from a recommendation with a
     * "Recommendation:" marker; this just gives that split a name instead of one run-on
     * paragraph.
     */
    private fun generateLocalStructuredConsultation(
        prompt: String,
        explanation: PulseMindExplanation,
        scoreResult: UsabilityScoreResult?,
        probeResult: DiagnosticProbeResult?,
        snapshot: NetworkSnapshot
    ): StructuredAdvice {
        val lower = prompt.lowercase()
        return when {
            lower.contains("ping") || lower.contains("latency") || lower.contains("jitter") -> {
                val tcp = probeResult?.averageTcpMs ?: 0
                val jitter = probeResult?.tcpJitterMs ?: 0
                if (tcp > 150 || jitter > 30) {
                    StructuredAdvice(
                        whatsHappening = "Your connection has elevated round-trip time ($tcp ms) with high packet jitter ($jitter ms).",
                        why = "This is most often caused by uplink congestion or bufferbloat on the local gateway - the link is up, but packets are queuing before they leave.",
                        recommendation = "Switch your DNS to 1.1.1.1 or 8.8.8.8, and enable QoS/traffic prioritization on your router if it supports it."
                    )
                } else {
                    StructuredAdvice(
                        whatsHappening = "Round-trip latency is currently stable at $tcp ms with minimal jitter ($jitter ms).",
                        why = "Nothing in your current telemetry points to congestion or an unstable path.",
                        recommendation = "No action needed - interactive and real-time traffic should perform well right now."
                    )
                }
            }
            lower.contains("signal") || lower.contains("rssi") || lower.contains("rf") || lower.contains("drop") -> StructuredAdvice(
                whatsHappening = explanation.headline,
                why = explanation.narrative,
                recommendation = explanation.recoveryRecommendation
            )
            lower.contains("captive") || lower.contains("zombie") || lower.contains("login") -> {
                if (scoreResult?.isZombieConnection == true) {
                    StructuredAdvice(
                        whatsHappening = "A zombie connection: your radio reports an active link, but every TCP handshake and HTTP probe is failing.",
                        why = "The radio-level connection came up, but the carrier or Wi-Fi router never actually finished routing you to the Internet - a stale or incomplete session on their side.",
                        recommendation = "Open the captive portal sign-in page if one appears, or toggle Wi-Fi/Airplane Mode to force a fresh session."
                    )
                } else {
                    StructuredAdvice(
                        whatsHappening = "No captive portal or zombie connection detected right now.",
                        why = "Your last probe confirmed the route to the Internet is actually open, not just radio-connected.",
                        recommendation = "No action needed."
                    )
                }
            }
            else -> StructuredAdvice(
                whatsHappening = explanation.headline,
                why = explanation.narrative,
                recommendation = explanation.recoveryRecommendation
            )
        }
    }
}

private fun StructuredAdvice.flatten(): String = buildString {
    appendLine(whatsHappening)
    appendLine()
    appendLine(why)
    appendLine()
    append(recommendation)
}
