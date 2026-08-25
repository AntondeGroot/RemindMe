package nl.local.remindme

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.LocalDate

object Notifications {

    private const val CHANNEL_ID = "reminders"
    private const val KEY_LAST_DAY = "lastNotifiedOn"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Your scheduled nudges"
            enableVibration(true)
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * One notification per reminder, with a stable id, so a new water nudge
     * replaces the previous one instead of stacking up all day.
     */
    fun show(context: Context, reminder: Reminder) {
        ensureChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        clearOlderDays(context, manager)

        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tap = PendingIntent.getActivity(
            context,
            reminder.id.hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(reminder.headline())
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setShowWhen(true)
            .setContentIntent(tap)
            .build()

        manager.notify(reminder.id.hashCode() and 0x7FFFFFFF, notification)
    }

    /**
     * A nudge you slept through is no use in the morning, and only a reminder that fires
     * again today replaces its own notification. So on the day's first post, sweep away
     * everything still standing from before it.
     */
    private fun clearOlderDays(context: Context, manager: NotificationManager) {
        val prefs = Store.prefs(context)
        val today = LocalDate.now().toString()
        if (prefs.getString(KEY_LAST_DAY, null) == today) return

        manager.cancelAll()
        prefs.edit().putString(KEY_LAST_DAY, today).apply()
    }
}
