package com.netsense.netpulse.model

/** Which hero-visual treatment [ui.HealthRing] should render for a given [ConnectionPresentation]. */
enum class ConnectionVisual {
    CHECK,
    PULSE_WARNING,
    ALERT,
    RECOVERING,
    RECOVERED,
    OFFLINE,
    SEARCHING
}

/**
 * Consumer-facing copy for a [ProductStatus] - the single place this app decides what to say
 * to a non-technical user about their connection. Both the Home screen and the persistent
 * notification (NetPulseSentinelService) read from this same mapping, so the two surfaces can
 * never disagree with each other about what's happening.
 *
 * [statusLine] is the short, single-line form used in the notification and compact contexts;
 * [headline]/[supportingText] are the fuller Home-screen copy.
 */
data class ConnectionPresentation(
    val headline: String,
    val supportingText: String,
    val statusLine: String,
    val showFixAction: Boolean,
    val fixActionLabel: String,
    val visual: ConnectionVisual
)

object ConnectionPresentationMapper {

    fun map(
        status: ProductStatus,
        snapshot: NetworkSnapshot,
        scoreResult: UsabilityScoreResult
    ): ConnectionPresentation {
        val hasStrongSignal = (snapshot.signalLevel ?: 0) >= 3
        val networkLabel = snapshot.cellularDataNetworkType?.takeIf { it.isNotBlank() }

        return when (status) {
            ProductStatus.ONLINE -> ConnectionPresentation(
                headline = "Everything looks good",
                supportingText = "Your Internet is working normally.",
                statusLine = "Internet is good",
                showFixAction = false,
                fixActionLabel = "",
                visual = ConnectionVisual.CHECK
            )
            ProductStatus.DEGRADED -> ConnectionPresentation(
                headline = "Internet is struggling",
                supportingText = "Your connection is working, but performance is poor.",
                statusLine = "Internet is struggling",
                showFixAction = true,
                fixActionLabel = "Fix It",
                visual = ConnectionVisual.PULSE_WARNING
            )
            ProductStatus.NO_INTERNET -> ConnectionPresentation(
                headline = "No Internet",
                supportingText = when {
                    snapshot.primaryTransport == NetworkTransport.WIFI ->
                        "Wi-Fi is connected, but Internet access isn't working."
                    hasStrongSignal ->
                        "You're connected to the network, but there's no Internet - even though your phone has a strong signal."
                    else ->
                        "You're connected to the network, but there's no Internet."
                },
                statusLine = "No Internet connection",
                showFixAction = true,
                fixActionLabel = "Fix It",
                visual = ConnectionVisual.ALERT
            )
            ProductStatus.WEAK_SIGNAL -> ConnectionPresentation(
                headline = "Weak Signal",
                supportingText = "A weak ${networkLabel ?: "cellular"} signal is affecting your connection quality.",
                statusLine = "Weak signal",
                showFixAction = true,
                fixActionLabel = "Fix It",
                visual = ConnectionVisual.PULSE_WARNING
            )
            ProductStatus.CAPTIVE_PORTAL -> ConnectionPresentation(
                headline = "Sign-In Required",
                supportingText = "Sign-in may be required before this network will let you use the Internet.",
                statusLine = "Sign-in required",
                showFixAction = true,
                fixActionLabel = "Sign In",
                visual = ConnectionVisual.ALERT
            )
            ProductStatus.CONNECTING -> ConnectionPresentation(
                headline = "Connecting...",
                supportingText = "Establishing a network connection.",
                statusLine = "Connecting...",
                showFixAction = false,
                fixActionLabel = "",
                visual = ConnectionVisual.SEARCHING
            )
            ProductStatus.CHECKING -> ConnectionPresentation(
                headline = "Checking your connection…",
                supportingText = "Verifying your Internet connection.",
                statusLine = "Checking your connection…",
                showFixAction = false,
                fixActionLabel = "",
                visual = ConnectionVisual.SEARCHING
            )
            ProductStatus.RECOVERING -> ConnectionPresentation(
                headline = "Fixing your connection...",
                supportingText = "NetPulse is working on it.",
                statusLine = "Fixing your connection...",
                showFixAction = false,
                fixActionLabel = "",
                visual = ConnectionVisual.RECOVERING
            )
            ProductStatus.RECOVERED -> ConnectionPresentation(
                headline = "You're back online",
                supportingText = "Connection recovered successfully.",
                statusLine = "Connection restored",
                showFixAction = false,
                fixActionLabel = "",
                visual = ConnectionVisual.RECOVERED
            )
            ProductStatus.UNAVAILABLE -> unavailablePresentation(snapshot.noConnectivityReason)
        }
    }

    /**
     * ProductStatus.UNAVAILABLE covers every "no transport at all" case, but the *reason* the
     * user is offline determines what they should actually go do about it - so this branches
     * on [NoConnectivityReason] rather than showing one generic "no connection" message for
     * airplane mode, both radios off, one radio off, and no-signal alike.
     */
    private fun unavailablePresentation(reason: NoConnectivityReason?): ConnectionPresentation =
        when (reason) {
            NoConnectivityReason.AIRPLANE_MODE -> ConnectionPresentation(
                headline = "Airplane Mode Is On",
                supportingText = "Airplane mode is on, so Wi-Fi and mobile data are both switched off.",
                statusLine = "Airplane mode is on",
                showFixAction = false,
                fixActionLabel = "",
                visual = ConnectionVisual.OFFLINE
            )
            NoConnectivityReason.WIFI_AND_DATA_OFF -> ConnectionPresentation(
                headline = "No Internet Connection",
                supportingText = "Wi-Fi and mobile data are both turned off.",
                statusLine = "Wi-Fi and mobile data are off",
                showFixAction = false,
                fixActionLabel = "",
                visual = ConnectionVisual.OFFLINE
            )
            NoConnectivityReason.WIFI_OFF -> ConnectionPresentation(
                headline = "Wi-Fi Is Off",
                supportingText = "Wi-Fi is turned off, and mobile data isn't reaching the Internet right now.",
                statusLine = "Wi-Fi is turned off",
                showFixAction = false,
                fixActionLabel = "",
                visual = ConnectionVisual.OFFLINE
            )
            NoConnectivityReason.CELLULAR_DATA_OFF -> ConnectionPresentation(
                headline = "Mobile Data Is Off",
                supportingText = "Mobile data is turned off, and Wi-Fi isn't reaching the Internet right now.",
                statusLine = "Mobile data is turned off",
                showFixAction = false,
                fixActionLabel = "",
                visual = ConnectionVisual.OFFLINE
            )
            NoConnectivityReason.NO_SIGNAL, null -> ConnectionPresentation(
                headline = "No Network Detected",
                supportingText = "Your device isn't connected to Wi-Fi or cellular data.",
                statusLine = "No network detected",
                showFixAction = false,
                fixActionLabel = "",
                visual = ConnectionVisual.OFFLINE
            )
        }
}
