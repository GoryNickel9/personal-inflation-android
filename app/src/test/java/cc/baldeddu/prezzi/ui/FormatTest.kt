package cc.baldeddu.prezzi.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test fun `display uses Italian decimals and half up rounding like Intl NumberFormat`() {
        assertEquals("0,13\u00a0€", euro(12.5))
        assertEquals("+1,3%", percent(1.25))
        assertEquals("−1,3%", percent(-1.25))
        assertEquals("1,235", quantity(1.2345))
    }
}
