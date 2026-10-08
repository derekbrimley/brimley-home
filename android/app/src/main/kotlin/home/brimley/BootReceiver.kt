package home.brimley

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// Bring the dashboard back after a reboot even if it is not the launcher.
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val launch = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)
    }
}
