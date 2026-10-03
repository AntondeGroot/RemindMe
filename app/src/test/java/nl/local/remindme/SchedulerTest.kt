package nl.local.remindme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class SchedulerTest {

    private val everyDayHome = (1..7).associateWith { DayType.HOME }

    @Test
    fun `next alarm on vacation skips reminders silenced by vacation and uses off-day times`() {
        val meals = Reminder(
            id = "meals", emoji = "", title = "Meals", duringVacation = true,
            times = mapOf(DayType.HOME to listOf(12 * 60), DayType.OFF to listOf(10 * 60))
        )
        val tidy = Reminder(
            id = "tidy", emoji = "", title = "Tidy",
            times = mapOf(DayType.HOME to listOf(9 * 60), DayType.OFF to listOf(9 * 60))
        )
        val config = Config(
            reminders = listOf(meals, tidy),
            week = everyDayHome,
            vacationUntil = LocalDate.of(2026, 10, 10)
        )

        val next = Scheduler.nextFire(config, LocalDateTime.of(2026, 10, 5, 8, 0))

        assertEquals(Scheduler.Fire(LocalDateTime.of(2026, 10, 5, 10, 0), listOf(meals)), next)
    }

    @Test
    fun `next alarm after a vacation that silenced everything lands on the day after the return date`() {
        val tidy = Reminder(
            id = "tidy", emoji = "", title = "Tidy",
            times = mapOf(DayType.HOME to listOf(9 * 60), DayType.OFF to listOf(9 * 60))
        )
        val config = Config(
            reminders = listOf(tidy),
            week = everyDayHome,
            vacationUntil = LocalDate.of(2026, 10, 10)
        )

        val next = Scheduler.nextFire(config, LocalDateTime.of(2026, 10, 5, 8, 0))

        assertEquals(Scheduler.Fire(LocalDateTime.of(2026, 10, 11, 9, 0), listOf(tidy)), next)
    }

    @Test
    fun `next alarm is strictly after now, not at the current minute`() {
        val tidy = Reminder(
            id = "tidy", emoji = "", title = "Tidy",
            times = mapOf(DayType.HOME to listOf(9 * 60, 12 * 60))
        )
        val config = Config(reminders = listOf(tidy), week = everyDayHome)

        val next = Scheduler.nextFire(config, LocalDateTime.of(2026, 10, 5, 9, 0))

        assertEquals(LocalDateTime.of(2026, 10, 5, 12, 0), next?.at)
    }

    @Test
    fun `next alarm is null when every reminder is inactive`() {
        val tidy = Reminder(
            id = "tidy", emoji = "", title = "Tidy", active = false,
            times = mapOf(DayType.HOME to listOf(9 * 60))
        )
        val config = Config(reminders = listOf(tidy), week = everyDayHome)

        assertNull(Scheduler.nextFire(config, LocalDateTime.of(2026, 10, 5, 8, 0)))
    }

    @Test
    fun `remaining today lists only times after the current minute, in order`() {
        val tidy = Reminder(
            id = "tidy", emoji = "", title = "Tidy",
            times = mapOf(DayType.HOME to listOf(9 * 60, 12 * 60, 15 * 60))
        )
        val water = Reminder(
            id = "water", emoji = "", title = "Water",
            times = mapOf(DayType.HOME to listOf(13 * 60, 16 * 60))
        )
        val config = Config(reminders = listOf(tidy, water), week = everyDayHome)

        val remaining = Scheduler.remainingToday(config, LocalDateTime.of(2026, 10, 5, 12, 0))

        assertEquals(listOf(13 * 60 to water, 15 * 60 to tidy, 16 * 60 to water), remaining)
    }
}
