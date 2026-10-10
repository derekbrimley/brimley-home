package home.brimley.audio

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.util.concurrent.TimeUnit

class PodcastFeeds {
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()

    suspend fun load(feedUrl: String): Result<Podcast> = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(feedUrl).header("User-Agent", "BrimleyHome/1.0").build()).execute().use { res ->
                if (!res.isSuccessful) throw IOException("feed ${res.code}")
                val parser = Xml.newPullParser().apply { setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false) }
                parser.setInput(res.body!!.charStream())
                FeedReader.parse(parser)
            }
        }
    }
}
