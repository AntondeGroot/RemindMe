package nl.local.remindme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Alarms are wiped on reboot, and a time-zone or clock change moves every
 * scheduled moment, so rebuild the chain whenever any of that happens.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                Notifications.ensureChannel(context.applicationContext)
                Scheduler.reschedule(context.applicationContext)
            }
        }
    }
}
