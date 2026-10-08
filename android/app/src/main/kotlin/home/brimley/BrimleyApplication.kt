package home.brimley

import android.app.Application
import home.brimley.data.HomeApi
import home.brimley.data.TodayRepository

class BrimleyApplication : Application() {
    lateinit var repository: TodayRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = TodayRepository(HomeApi(BuildConfig.API_BASE, BuildConfig.API_TOKEN), cacheDir)
    }
}
