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
                supportingText = if (hasStrongSignal) {
                    "Your phone has a strong signal, but Internet traffic isn't getting through."
                } else {
                    "Your phone is connected, but Internet traffic isn't getting through."
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
                supportingText = "This network needs you to sign in before you can use the Internet.",
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
                headline = "Checking your connection",
                supportingText = "Verifying your Internet connection.",
                statusLine = "Checking connection",
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
            ProductStatus.UNAVAILABLE -> ConnectionPresentation(
                headline = "No Internet Connection",
                supportingText = "Your device isn't connected to Wi-Fi or cellular data.",
                statusLine = "No connection",
                showFixAction = false,
                fixActionLabel = "",
                visual = ConnectionVisual.OFFLINE
            )
        }
    }
}
