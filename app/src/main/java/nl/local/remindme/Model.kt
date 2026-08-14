package nl.local.remindme

import java.time.LocalDate

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
 * One reminder. [times] holds minutes-since-midnight per day type, so
 * "clean up 1 thing" can fire three times at home and once on office days.
 */
data class Reminder(
    val id: String,
    val emoji: String,
    val title: String,
    val active: Boolean = true,
    val times: Map<DayType, List<Int>> = emptyMap()
) {
    fun timesFor(type: DayType): List<Int> = times[type].orEmpty()
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
