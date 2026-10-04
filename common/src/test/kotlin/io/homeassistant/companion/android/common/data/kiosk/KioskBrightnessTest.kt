package io.homeassistant.companion.android.common.data.kiosk

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KioskBrightnessTest {

    @Test
    fun `Given a value outside the range then it is clamped`() {
        assertEquals(KioskBrightness.MIN_VALUE, KioskBrightness.of(-1f).value)
        assertEquals(KioskBrightness.MAX_VALUE, KioskBrightness.of(2f).value)
    }

    @Test
    fun `Given NaN then it becomes the minimum rather than passing through`() {
        // coerceIn returns NaN untouched, which would put a value outside the promised range into
        // storage and onto a window.
        assertEquals(KioskBrightness.MIN_VALUE, KioskBrightness.of(Float.NaN).value)
    }

    @Test
    fun `Given every whole percentage then it survives a conversion round trip`() {
        for (percent in 0..100) {
            assertEquals(percent, KioskBrightness.fromPercent(percent)?.toPercent())
        }
    }

    @Test
    fun `Given no percentage then there is no brightness`() {
        assertEquals(null, KioskBrightness.fromPercent(null))
    }
}
