package home.brimley

import android.app.Application
import home.brimley.audio.PodcastFeeds
import home.brimley.audio.PodcastPlayer
import home.brimley.data.HomeApi
import home.brimley.data.TodayRepository
import home.brimley.tv.AtvConnector
import home.brimley.tv.NsdDiscovery
import home.brimley.tv.PrefsTvStore
import home.brimley.tv.TvController
import home.brimley.tv.TvIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class BrimleyApplication : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var repository: TodayRepository
        private set
    lateinit var tv: TvController
        private set
    lateinit var podcasts: PodcastPlayer
        private set
    val feeds = PodcastFeeds()

    override fun onCreate() {
        super.onCreate()
        repository = TodayRepository(HomeApi(BuildConfig.API_BASE, BuildConfig.API_TOKEN), cacheDir)
        podcasts = PodcastPlayer(this)
        tv = TvController(PrefsTvStore(this), AtvConnector(TvIdentity(), scope), NsdDiscovery(this), scope).also { it.start() }
    }
}
