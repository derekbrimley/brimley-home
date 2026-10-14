package home.brimley.tv

import java.net.URI

// Debug builds only: other shapes of a title link to try on the TV when the
// one JustWatch gave doesn't open the title. JustWatch gives every HBO Max
// title as play.hbomax.com/show/<id>, movies included, and the app opened its
// home page for those; Google TV's own links add ?utm_source=universal_search.
object LinkVariants {
    private const val UTM = "utm_source=universal_search"

    fun forLink(link: String): List<Pair<String, String>> {
        val uri = runCatching { URI(link) }.getOrNull() ?: return emptyList()
        val host = uri.host?.lowercase() ?: return emptyList()
        if (!(host.endsWith("hbomax.com") || host.endsWith("max.com"))) return emptyList()
        val m = Regex("^/(show|movie)/([0-9a-fA-F-]{36})").find(uri.path ?: "") ?: return emptyList()
        val id = m.groupValues[2]
        val base = "https://play.hbomax.com"
        return listOf(
            "/show/" to "$base/show/$id",
            "/show/ + utm" to "$base/show/$id?$UTM",
            "/movie/" to "$base/movie/$id",
            "/movie/ + utm" to "$base/movie/$id?$UTM",
        )
    }
}
