package io.github.kusune.monomemo

import android.content.Context

data class DisplaySettings(
    val fontSizePt: Float,
    val lineSpacingMultiplier: Float,
    val wrapLines: Boolean,
)

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

        const val DEFAULT_FONT_SIZE_PT = 14f
        const val MIN_FONT_SIZE_PT = 8f
        const val MAX_FONT_SIZE_PT = 32f
        const val DEFAULT_LINE_SPACING = 1f
        const val MIN_LINE_SPACING = 0.8f
        const val MAX_LINE_SPACING = 1.6f
    }
}
