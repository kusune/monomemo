package io.github.kusune.monomemo

import android.content.Context
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.roundToLong

data class DisplaySettings(
    val fontSizePt: Float,
    val lineSpacingMultiplier: Float,
    val wrapLines: Boolean,
)

/** Logarithmic slider mapping for the undo/redo repeat interval. */
object HistoryRepeatInterval {
    const val MIN_MILLIS = 1L
    const val MAX_MILLIS = 200L
    const val DEFAULT_MILLIS = 25L
    const val SLIDER_STEPS = 100

    fun clamp(intervalMillis: Long): Long = intervalMillis.coerceIn(MIN_MILLIS, MAX_MILLIS)

    fun fromSliderProgress(progress: Int): Long {
        val normalized = progress.coerceIn(0, SLIDER_STEPS).toDouble() / SLIDER_STEPS
        val interval = MAX_MILLIS * exp(
            ln(MIN_MILLIS.toDouble() / MAX_MILLIS) * normalized,
        )
        return interval.roundToLong().coerceIn(MIN_MILLIS, MAX_MILLIS)
    }

    fun toSliderProgress(intervalMillis: Long): Int {
        val clamped = clamp(intervalMillis).toDouble()
        val normalized = ln(MAX_MILLIS / clamped) / ln(MAX_MILLIS.toDouble() / MIN_MILLIS)
        return (normalized * SLIDER_STEPS).roundToInt().coerceIn(0, SLIDER_STEPS)
    }
}

/** App-level display preferences, kept separate from document contents. */
class EditorPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("editor-preferences", Context.MODE_PRIVATE)

    fun load(): DisplaySettings = DisplaySettings(
        fontSizePt = loadFontSizePt(),
        lineSpacingMultiplier = preferences.getFloat(KEY_LINE_SPACING, DEFAULT_LINE_SPACING)
            .coerceIn(MIN_LINE_SPACING, MAX_LINE_SPACING),
        wrapLines = preferences.getBoolean(KEY_WRAP_LINES, false),
    )

    fun save(settings: DisplaySettings) {
        preferences.edit()
            .putFloat(KEY_FONT_SIZE_PT, settings.fontSizePt)
            .putFloat(KEY_LINE_SPACING, settings.lineSpacingMultiplier)
            .putBoolean(KEY_WRAP_LINES, settings.wrapLines)
            .apply()
    }

    fun loadHistoryRepeatIntervalMillis(
        defaultIntervalMillis: Long = HistoryRepeatInterval.DEFAULT_MILLIS,
    ): Long {
        val storedInterval = when {
            preferences.contains(KEY_HISTORY_REPEAT_INTERVAL_MILLIS) -> preferences.getLong(
                KEY_HISTORY_REPEAT_INTERVAL_MILLIS,
                defaultIntervalMillis,
            )
            preferences.contains(KEY_LEGACY_HISTORY_REPEAT_SPEED) -> {
                // Migrate the short-lived 1x/2x/4x preference format.
                when (preferences.getInt(KEY_LEGACY_HISTORY_REPEAT_SPEED, 2)) {
                    1 -> 100L
                    2 -> 50L
                    4 -> 25L
                    else -> defaultIntervalMillis
                }
            }
            else -> defaultIntervalMillis
        }
        return HistoryRepeatInterval.clamp(storedInterval)
    }

    fun saveHistoryRepeatIntervalMillis(intervalMillis: Long) {
        preferences.edit()
            .putLong(
                KEY_HISTORY_REPEAT_INTERVAL_MILLIS,
                HistoryRepeatInterval.clamp(intervalMillis),
            )
            .apply()
    }

    private fun loadFontSizePt(): Float {
        val storedSize = when {
            preferences.contains(KEY_FONT_SIZE_PT) -> preferences.getFloat(
                KEY_FONT_SIZE_PT,
                DEFAULT_FONT_SIZE_PT,
            )
            preferences.contains(LEGACY_KEY_FONT_SIZE_SP) -> {
                // Keep existing installations close to their previous visual size.
                preferences.getFloat(LEGACY_KEY_FONT_SIZE_SP, DEFAULT_FONT_SIZE_PT / 0.75f) * 0.75f
            }
            else -> DEFAULT_FONT_SIZE_PT
        }
        return storedSize.coerceIn(MIN_FONT_SIZE_PT, MAX_FONT_SIZE_PT)
    }

    companion object {
        private const val KEY_FONT_SIZE_PT = "font_size_pt"
        private const val LEGACY_KEY_FONT_SIZE_SP = "font_size_sp"
        private const val KEY_LINE_SPACING = "line_spacing_multiplier"
        private const val KEY_WRAP_LINES = "wrap_lines"
        private const val KEY_HISTORY_REPEAT_INTERVAL_MILLIS = "history_repeat_interval_millis"
        private const val KEY_LEGACY_HISTORY_REPEAT_SPEED = "history_repeat_speed"

        const val DEFAULT_FONT_SIZE_PT = 14f
        const val MIN_FONT_SIZE_PT = 8f
        const val MAX_FONT_SIZE_PT = 32f
        const val DEFAULT_LINE_SPACING = 1f
        const val MIN_LINE_SPACING = 0.8f
        const val MAX_LINE_SPACING = 1.6f
    }
}
