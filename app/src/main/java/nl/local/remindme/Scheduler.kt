package nl.local.remindme

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Only ever one alarm is pending: the next moment something is due.
 * When it fires, the receiver posts the notifications and books the following one.
 * That keeps us well clear of the per-app alarm limits, and it survives
 * reboots, time-zone changes and edits to the schedule.
 */
object Scheduler {

    const val EXTRA_AT = "nl.local.remindme.AT"
    private const val REQUEST_CODE = 4711

    /**
     * How far ahead to look for the next due moment. A reminder on one weekday of even
     * weeks only is normally 13 days out at worst, but the two odd weeks a 53-week year
     * puts back to back can stretch that to 20, so search a comfortable four weeks.
     */
    private const val SEARCH_DAYS = 28L

    data class Fire(val at: LocalDateTime, val reminders: List<Reminder>)

    fun reschedule(context: Context) {
        val app = context.applicationContext
        val manager = app.getSystemService(AlarmManager::class.java) ?: return
        val config = Store.load(app)

        val next = nextFire(config, LocalDateTime.now())
        if (next == null) {
            manager.cancel(pendingIntent(app, 0L, mutable = false))
            return
        }

        val millis = next.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pending = pendingIntent(app, millis, mutable = false)
        manager.cancel(pending)

        val canBeExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        if (canBeExact) {
            // Fires on the minute even in doze.
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
        } else {
            // Without the exact-alarm permission this can drift by several minutes.
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
        }
    }

    /** The next moment at or after [from] when at least one reminder is due. */
    fun nextFire(config: Config, from: LocalDateTime): Fire? {
        val active = config.reminders.filter { it.active }
        if (active.isEmpty()) return null

        for (offset in 0..SEARCH_DAYS) {
            val date = from.toLocalDate().plusDays(offset)
            val type = config.dayTypeFor(date)
            val minutes = active.flatMap { it.timesOn(date, type) }.distinct().sorted()

            for (minute in minutes) {
                val at = date.atStartOfDay().plusMinutes(minute.toLong())
                if (at.isAfter(from)) {
                    val due = active.filter { minute in it.timesOn(date, type) }
                    if (due.isNotEmpty()) return Fire(at, due)
                }
            }
        }
        return null
    }

    /** Everything still to come today, for the status line in the app. */
    fun remainingToday(config: Config, now: LocalDateTime): List<Pair<Int, Reminder>> {
        val today = now.toLocalDate()
        val type = config.dayTypeFor(today)
        val minuteNow = now.hour * 60 + now.minute
        return config.reminders
            .filter { it.active }
            .flatMap { r -> r.timesOn(today, type).filter { it > minuteNow }.map { it to r } }
            .sortedBy { it.first }
    }

    fun scheduledFor(config: Config, date: LocalDate, minute: Int): List<Reminder> {
        val type = config.dayTypeFor(date)
        return config.reminders.filter { it.active && minute in it.timesOn(date, type) }
    }

    private fun pendingIntent(context: Context, at: Long, mutable: Boolean): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).putExtra(EXTRA_AT, at)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }
}
