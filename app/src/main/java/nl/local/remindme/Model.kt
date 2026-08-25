package nl.local.remindme

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** Home / office / day off. Each one can carry its own set of times. */
enum class DayType(val label: String) {
    HOME("Home"),
    OFFICE("Office"),
    OFF("Off");

    companion object {
        fun from(name: String?): DayType = entries.firstOrNull { it.name == name } ?: HOME
    }
}

/**
 * Which weeks a reminder may fire in, by the ISO week number printed on the calendar.
 * Note that a 53-week year puts two odd weeks back to back over new year; that is what
 * the number on the calendar says, so it is what this follows.
 */
enum class Weeks(val label: String) {
    EVERY("Every week"),
    EVEN("Even weeks"),
    ODD("Odd weeks");

    fun includes(date: LocalDate): Boolean = when (this) {
        EVERY -> true
        EVEN -> isoWeek(date) % 2 == 0
        ODD -> isoWeek(date) % 2 != 0
    }

    companion object {
        fun from(name: String?): Weeks = entries.firstOrNull { it.name == name } ?: EVERY
    }
}

/**
 * The fourth kind of day, and the only one a reminder picks for itself: any combination of
 * weekdays, optionally only in even or odd weeks. A date it covers fires [times] on top of
 * whatever the day type already asks for.
 *
 * No days picked means the reminder has no specific days and nothing changes for it.
 */
data class SpecificDays(
    /** java.time day-of-week values, 1 = Monday .. 7 = Sunday. */
    val days: Set<Int> = emptySet(),
    val weeks: Weeks = Weeks.EVERY,
    val times: List<Int> = emptyList()
) {
    fun covers(date: LocalDate): Boolean =
        date.dayOfWeek.value in days && weeks.includes(date)

    fun isSet(): Boolean = days.isNotEmpty()
}

/**
 * One reminder. [times] holds minutes-since-midnight per day type, so
 * "clean up 1 thing" can fire three times at home and once on office days.
 * [specific] pins particular weekdays — Friday of even weeks, say — to their own times.
 */
data class Reminder(
    val id: String,
    val emoji: String,
    val title: String,
    val active: Boolean = true,
    val times: Map<DayType, List<Int>> = emptyMap(),
    val specific: SpecificDays = SpecificDays()
) {
    fun timesFor(type: DayType): List<Int> = times[type].orEmpty()

    /** Title with its icon in front, or on its own when there is no icon. */
    fun headline(): String = if (emoji.isBlank()) title else "$emoji  $title"

    /** What this reminder fires on [date]: the day type's times, plus any specific extras. */
    fun timesOn(date: LocalDate, type: DayType): List<Int> {
        if (!specific.covers(date)) return timesFor(type)
        return (timesFor(type) + specific.times).distinct().sorted()
    }
}

data class Config(
    val reminders: List<Reminder>,
    /** java.time DayOfWeek value (1 = Monday .. 7 = Sunday) -> the usual day type. */
    val week: Map<Int, DayType>,
    /** ISO date -> a one-off day type for that date only. */
    val overrides: Map<String, DayType> = emptyMap()
) {
    fun dayTypeFor(date: LocalDate): DayType =
        overrides[date.toString()] ?: week[date.dayOfWeek.value] ?: DayType.HOME
}

fun hhmm(minuteOfDay: Int): String = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

/** The ISO-8601 week number, the one printed on the calendar. */
fun isoWeek(date: LocalDate): Int = date.get(WeekFields.ISO.weekOfWeekBasedYear())

fun dayName(day: Int): String =
    DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, Locale.getDefault())

/** "Mon, Wed, Fri · odd weeks", for a caption under a reminder. */
fun patternSummary(specific: SpecificDays): String {
    val whichDays = specific.days.sorted().joinToString(", ") { dayName(it) }
    if (specific.weeks == Weeks.EVERY) return whichDays
    return "$whichDays · ${specific.weeks.label.lowercase(Locale.getDefault())}"
}

object Defaults {

    private fun everyDay(vararg times: Int): Map<DayType, List<Int>> =
        DayType.entries.associateWith { times.toList() }

    private fun t(h: Int, m: Int = 0) = h * 60 + m

    val config = Config(
        week = mapOf(
            1 to DayType.HOME,    // Monday
            2 to DayType.OFFICE,  // Tuesday
            3 to DayType.HOME,    // Wednesday
            4 to DayType.OFFICE,  // Thursday
            5 to DayType.HOME,    // Friday
            6 to DayType.OFF,     // Saturday
            7 to DayType.OFF      // Sunday
        ),
        reminders = listOf(
            Reminder(
                id = "litter",
                emoji = "🐈",
                title = "Clean out the kitty litter bins",
                times = everyDay(t(21))
            ),
            Reminder(
                id = "tidy",
                emoji = "🧺",
                title = "Clean up 1 thing",
                times = mapOf(
                    DayType.HOME to listOf(t(11), t(15), t(19, 30)),
                    DayType.OFFICE to listOf(t(19, 30)),
                    DayType.OFF to listOf(t(11), t(16))
                )
            ),
            Reminder(
                id = "meals",
                emoji = "📓",
                title = "Did you register all your meals?",
                times = mapOf(
                    DayType.HOME to listOf(t(9, 30), t(13, 30), t(16, 30), t(20)),
                    DayType.OFFICE to listOf(t(9, 30), t(13, 30), t(16, 30), t(20)),
                    DayType.OFF to listOf(t(10), t(14), t(17), t(20, 30))
                )
            ),
            Reminder(
                id = "water",
                emoji = "💧",
                title = "Drink a glass of water",
                times = mapOf(
                    DayType.HOME to listOf(
                        t(8), t(10), t(11, 30), t(13), t(14, 30), t(16), t(17, 30), t(19), t(20, 30)
                    ),
                    DayType.OFFICE to listOf(
                        t(8), t(10), t(11, 30), t(13), t(14, 30), t(16), t(17, 30), t(19), t(20, 30)
                    ),
                    DayType.OFF to listOf(t(9), t(11), t(13), t(15), t(17), t(19), t(21))
                )
            )
        )
    )
}
