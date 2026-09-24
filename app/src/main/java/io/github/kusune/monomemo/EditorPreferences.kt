package io.github.kusune.monomemo

import android.content.Context

data class DisplaySettings(
    val fontSizeSp: Float,
    val lineSpacingMultiplier: Float,
    val wrapLines: Boolean,
)

/** App-level display preferences, kept separate from document contents. */
class EditorPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("editor-preferences", Context.MODE_PRIVATE)

    fun load(): DisplaySettings = DisplaySettings(
        fontSizeSp = preferences.getFloat(KEY_FONT_SIZE, DEFAULT_FONT_SIZE_SP)
            .coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP),
        lineSpacingMultiplier = preferences.getFloat(KEY_LINE_SPACING, DEFAULT_LINE_SPACING)
            .coerceIn(MIN_LINE_SPACING, MAX_LINE_SPACING),
        wrapLines = preferences.getBoolean(KEY_WRAP_LINES, false),
    )

    fun save(settings: DisplaySettings) {
        preferences.edit()
            .putFloat(KEY_FONT_SIZE, settings.fontSizeSp)
            .putFloat(KEY_LINE_SPACING, settings.lineSpacingMultiplier)
            .putBoolean(KEY_WRAP_LINES, settings.wrapLines)
            .apply()
    }

    companion object {
        private const val KEY_FONT_SIZE = "font_size_sp"
        private const val KEY_LINE_SPACING = "line_spacing_multiplier"
        private const val KEY_WRAP_LINES = "wrap_lines"

        const val DEFAULT_FONT_SIZE_SP = 18f
        const val MIN_FONT_SIZE_SP = 12f
        const val MAX_FONT_SIZE_SP = 36f
        const val DEFAULT_LINE_SPACING = 1f
        const val MIN_LINE_SPACING = 0.8f
        const val MAX_LINE_SPACING = 1.6f
    }
}
