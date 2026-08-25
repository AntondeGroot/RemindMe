package nl.local.remindme

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object Notifications {

    private const val CHANNEL_ID = "reminders"

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
}
