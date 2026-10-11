package home.brimley.tv

import java.net.URI

// A title link opens the app on the title's page, not playing it, and Max first
// asks who's watching. These are the OK presses that get past that: once the app
// is in front, wait each delay and press OK. "Cold" is when the app wasn't in
// front before the link (so Max shows its profile picker); "warm" is when it was.
// The delays are guesses to tune on the real TV.
data class AutoPlay(val appPackage: String, val coldDelaysMs: List<Long>, val warmDelaysMs: List<Long>) {
    companion object {
        private val MAX = AutoPlay("com.wbd.stream", coldDelaysMs = listOf(5_000, 4_000), warmDelaysMs = listOf(3_500))
        private val PRIME = AutoPlay("com.amazon.amazonvideo.livingroom", coldDelaysMs = listOf(5_000), warmDelaysMs = listOf(3_000))

        private val byHost = listOf(
            "max.com" to MAX,
            "hbomax.com" to MAX,
            "primevideo.com" to PRIME,
            "amazon.com" to PRIME,
        )

        // Null for apps that play on their own (YouTube) or that we haven't tuned.
        fun forLink(link: String): AutoPlay? {
            val host = runCatching { URI(link).host }.getOrNull()?.lowercase() ?: return null
            return byHost.firstOrNull { (domain, _) -> host == domain || host.endsWith(".$domain") }?.second
        }
    }
}
