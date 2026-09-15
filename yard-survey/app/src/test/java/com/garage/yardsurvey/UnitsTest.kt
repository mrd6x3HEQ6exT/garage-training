package com.garage.yardsurvey

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitsTest {
    @Test fun feetConversion() {
        assertEquals("3.28 ft", Units.fmtLen(1.0, imperial = true))
        assertEquals("1.00 m", Units.fmtLen(1.0, imperial = false))
        assertEquals("+0.50 m", Units.fmtLen(0.5, imperial = false, signed = true))
        assertEquals("-0.50 m", Units.fmtLen(-0.5, imperial = false, signed = true))
        assertEquals(1.0, Units.fromDisplay(Units.toDisplay(1.0, true), true), 1e-12)
    }

    @Test fun cardinals() {
        assertEquals("N", Units.cardinal(0.0))
        assertEquals("N", Units.cardinal(359.0))
        assertEquals("NE", Units.cardinal(45.0))
        assertEquals("W", Units.cardinal(270.0))
        assertEquals("NW", Units.cardinal(300.0))
    }
}
