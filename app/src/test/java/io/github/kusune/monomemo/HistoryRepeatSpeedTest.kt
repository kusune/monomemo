package io.github.kusune.monomemo

import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryRepeatSpeedTest {
    @Test
    fun repeatSpeedsUseExpectedIntervals() {
        assertEquals(100L, HistoryRepeatSpeed.NORMAL.intervalMillis)
        assertEquals(50L, HistoryRepeatSpeed.DOUBLE.intervalMillis)
        assertEquals(25L, HistoryRepeatSpeed.QUADRUPLE.intervalMillis)
    }

    @Test
    fun unknownStoredSpeedUsesDefault() {
        assertEquals(
            EditorPreferences.DEFAULT_HISTORY_REPEAT_SPEED,
            HistoryRepeatSpeed.fromMultiplier(3),
        )
    }
}
