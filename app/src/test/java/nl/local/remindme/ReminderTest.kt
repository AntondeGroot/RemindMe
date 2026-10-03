package nl.local.remindme

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ReminderTest {

    @Test
    fun `specific days add their times on top of the day type's, without duplicates`() {
        val reminder = Reminder(
            id = "tidy", emoji = "", title = "Tidy",
            times = mapOf(DayType.HOME to listOf(12 * 60, 17 * 60)),
            specific = SpecificDays(days = setOf(5), times = listOf(17 * 60, 9 * 60))
        )
        val friday = LocalDate.of(2026, 10, 9)

        assertEquals(listOf(9 * 60, 12 * 60, 17 * 60), reminder.timesOn(friday, DayType.HOME))
    }
}
