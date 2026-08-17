package com.netsense.netpulse.model

enum class HealerActionType {
    OPTIMIZE_DNS,
    SWITCH_NETWORK,
    RESET_RADIO_INTERFACE,
    OPEN_CAPTIVE_PORTAL,
    FLUSH_SOCKET_CACHE,
    OPTIMIZE_BUFFERBLOAT,
    AIRPLANE_CYCLE
}

data class HealerActionItem(
    val id: String,
    val title: String,
    val description: String,
    val impactLevel: String, // "High Impact", "Medium Impact", "Preventative"
    val actionType: HealerActionType,
    val isAutoFixable: Boolean = true,
    val executionNote: String? = null,
    val recommendedValue: String? = null
)

data class IncidentReport(
    val reportId: String,
    val timestampFormatted: String,
    val generatedEpochMs: Long = System.currentTimeMillis(),
    val overallScore: Int,
    val ratingLabel: String,
    val primaryIssue: String,
    val transportUsed: String,
    val carrierOrSsid: String,
    val markdownContent: String,
    val plainTextContent: String
)
