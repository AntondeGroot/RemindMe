package nl.local.remindme

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {

    @Test
    fun `hhmm pads hours and minutes to two digits`() {
        assertEquals("09:05", hhmm(9 * 60 + 5))
    }
}
