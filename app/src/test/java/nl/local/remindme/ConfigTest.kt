package nl.local.remindme

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ConfigTest {

    private val everyDayHome = (1..7).associateWith { DayType.HOME }

    @Test
    fun `the return date is still vacation, the day after follows the usual week again`() {
        val returnDate = LocalDate.of(2026, 10, 10)
        val config = Config(reminders = emptyList(), week = everyDayHome, vacationUntil = returnDate)

        assertEquals(DayType.OFF, config.dayTypeFor(returnDate))
        assertEquals(DayType.HOME, config.dayTypeFor(returnDate.plusDays(1)))
    }

    @Test
    fun `a one-off override beats the usual week for that date only`() {
        val overridden = LocalDate.of(2026, 10, 6)
        val config = Config(
            reminders = emptyList(),
            week = everyDayHome,
            overrides = mapOf(overridden.toString() to DayType.OFFICE)
        )

        assertEquals(DayType.OFFICE, config.dayTypeFor(overridden))
        assertEquals(DayType.HOME, config.dayTypeFor(overridden.plusDays(7)))
    }
}
