package home.brimley

import android.app.ActivityManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import home.brimley.ui.BrimleyApp
import home.brimley.ui.theme.BrimleyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Kiosk basics: always on, no system bars. Lock task mode is attempted
        // below; it only sticks once the app is set as device owner
        // (adb shell dpm set-device-owner home.brimley/.DeviceAdmin) or the
        // user confirms screen pinning the first time.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val app = application as BrimleyApplication
        setContent {
            BrimleyTheme {
                BrimleyApp(repository = app.repository, tv = app.tv, podcasts = app.podcasts, feeds = app.feeds)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        if (am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) {
            runCatching { startLockTask() }
        }
    }
}
