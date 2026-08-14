package nl.local.remindme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.Instant
import java.time.ZoneId

/** Fires on the minute, posts whatever was due, then books the next alarm. */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val scheduledAt = intent.getLongExtra(Scheduler.EXTRA_AT, System.currentTimeMillis())

        val moment = Instant.ofEpochMilli(scheduledAt)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()

        val config = Store.load(app)
        val minute = moment.hour * 60 + moment.minute

        Scheduler.scheduledFor(config, moment.toLocalDate(), minute)
            .forEach { Notifications.show(app, it) }

        Scheduler.reschedule(app)
    }
}
