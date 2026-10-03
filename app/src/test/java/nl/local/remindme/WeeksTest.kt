package nl.local.remindme

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeeksTest {

    @Test
    fun `odd weeks include both week 53 and week 1 across a 53-week new year`() {
        val inWeek53 = LocalDate.of(2026, 12, 31)
        val inWeek1 = LocalDate.of(2027, 1, 4)

        assertTrue(Weeks.ODD.includes(inWeek53))
        assertTrue(Weeks.ODD.includes(inWeek1))
    }
}
