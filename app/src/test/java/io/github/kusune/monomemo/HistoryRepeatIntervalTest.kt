package io.github.kusune.monomemo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryRepeatIntervalTest {
    @Test
    fun sliderCoversRequestedBounds() {
        assertEquals(200L, HistoryRepeatInterval.fromSliderProgress(0))
        assertEquals(10L, HistoryRepeatInterval.fromSliderProgress(HistoryRepeatInterval.SLIDER_STEPS))
    }

    @Test
    fun logarithmicSliderRoundTripsDefaultInterval() {
        val progress = HistoryRepeatInterval.toSliderProgress(HistoryRepeatInterval.DEFAULT_MILLIS)
        assertEquals(
            HistoryRepeatInterval.DEFAULT_MILLIS,
            HistoryRepeatInterval.fromSliderProgress(progress),
        )
    }

    @Test
    fun fasterIntervalsHaveHigherSliderProgress() {
        assertTrue(
            HistoryRepeatInterval.toSliderProgress(25L) >
                HistoryRepeatInterval.toSliderProgress(100L),
        )
    }
}
