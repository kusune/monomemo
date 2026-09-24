package io.github.kusune.monomemo

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import java.io.IOException
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var editor: CursorAwareEditText
    private lateinit var store: LocalNoteStore
    private lateinit var editorPreferences: EditorPreferences
    private lateinit var wrapButton: ImageButton
    private lateinit var scaleDetector: ScaleGestureDetector

    private var loading = true
    private var displaySettings = DisplaySettings(
        fontSizeSp = EditorPreferences.DEFAULT_FONT_SIZE_SP,
        lineSpacingMultiplier = EditorPreferences.DEFAULT_LINE_SPACING,
        wrapLines = false,
    )

    private val saveRunnable = Runnable { saveNow() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureWindow()

        store = LocalNoteStore(this)
        editorPreferences = EditorPreferences(this)
        displaySettings = editorPreferences.load()
        scaleDetector = createScaleDetector()
        editor = createEditor()

        setContentView(createScreen())
        applyDisplaySettings()
        restoreNote()
        observeEditor()
    }

    override fun onPause() {
        saveNow()
        super.onPause()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(saveRunnable)
        super.onDestroy()
    }

    private fun configureWindow() {
        window.statusBarColor = getColor(R.color.editor_background)
        window.navigationBarColor = Color.BLACK
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE,
        )
    }

    private fun createEditor(): CursorAwareEditText = CursorAwareEditText(this).apply {
        setBackgroundColor(getColor(R.color.editor_background))
        setTextColor(getColor(R.color.editor_text))
        setHintTextColor(getColor(R.color.editor_hint))
        hint = ""
        typeface = resources.getFont(R.font.biz_ud_gothic_regular)
        includeFontPadding = false
        setPadding(dp(16), dp(12), dp(16), dp(24))
        gravity = Gravity.TOP or Gravity.START
        inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
        isSingleLine = false
        setHorizontalScrollBarEnabled(false)
        setVerticalScrollBarEnabled(true)
        setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            false
        }
    }

    private fun createScreen(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(getColor(R.color.editor_background))
        addView(createToolbar(), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(56),
        ))
        addView(editor, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f,
        ))
    }

    private fun createToolbar(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(getColor(R.color.toolbar_background))

        val menuButton = createActionButton(R.drawable.ic_menu, "メニュー")
        menuButton.setOnClickListener { showMainMenu(menuButton) }
        addView(menuButton)

        addView(TextView(this@MainActivity).apply {
            text = getString(R.string.app_name)
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(8), 0)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))

        wrapButton = createActionButton(R.drawable.ic_no_wrap, "折り返し切替")
        wrapButton.setOnClickListener { toggleWrapLines() }
        addView(wrapButton)

        val settingsButton = createActionButton(R.drawable.ic_settings, "表示設定")
        settingsButton.setOnClickListener { showDisplaySettings() }
        addView(settingsButton)
    }

    private fun createActionButton(icon: Int, description: String): ImageButton = ImageButton(this).apply {
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(56))
        setImageResource(icon)
        imageTintList = ColorStateList.valueOf(Color.WHITE)
        contentDescription = description
        setBackgroundColor(Color.TRANSPARENT)
        setPadding(dp(12), dp(16), dp(12), dp(16))
    }

    private fun createScaleDetector(): ScaleGestureDetector = ScaleGestureDetector(
        this,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val nextSize = displaySettings.fontSizeSp * detector.scaleFactor
                updateFontSize(nextSize)
                return true
            }
        },
    )

    private fun applyDisplaySettings() {
        editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, displaySettings.fontSizeSp)
        editor.setLineSpacing(0f, displaySettings.lineSpacingMultiplier)
        editor.setHorizontallyScrolling(!displaySettings.wrapLines)
        wrapButton.setImageResource(
            if (displaySettings.wrapLines) R.drawable.ic_wrap else R.drawable.ic_no_wrap,
        )
        wrapButton.contentDescription = if (displaySettings.wrapLines) {
            "折り返しを解除"
        } else {
            "折り返しを有効化"
        }
    }

    private fun updateFontSize(value: Float) {
        val next = (value * 10f).roundToInt() / 10f
        displaySettings = displaySettings.copy(
            fontSizeSp = next.coerceIn(
                EditorPreferences.MIN_FONT_SIZE_SP,
                EditorPreferences.MAX_FONT_SIZE_SP,
            ),
        )
        editorPreferences.save(displaySettings)
        applyDisplaySettings()
    }

    private fun updateLineSpacing(value: Float) {
        displaySettings = displaySettings.copy(
            lineSpacingMultiplier = value.coerceIn(
                EditorPreferences.MIN_LINE_SPACING,
                EditorPreferences.MAX_LINE_SPACING,
            ),
        )
        editorPreferences.save(displaySettings)
        applyDisplaySettings()
    }

    private fun toggleWrapLines() {
        displaySettings = displaySettings.copy(wrapLines = !displaySettings.wrapLines)
        editorPreferences.save(displaySettings)
        applyDisplaySettings()
    }

    private fun showMainMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add("表示設定").setOnMenuItemClickListener {
                showDisplaySettings()
                true
            }
            menu.add(if (displaySettings.wrapLines) "折り返しを解除" else "折り返しを有効化")
                .setOnMenuItemClickListener {
                    toggleWrapLines()
                    true
                }
            show()
        }
    }

    private fun showDisplaySettings() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(4), dp(24), 0)
        }

        val fontSizeLabel = TextView(this).apply {
            setTextColor(Color.WHITE)
        }
        val fontSizeSeekBar = SeekBar(this).apply {
            max = ((EditorPreferences.MAX_FONT_SIZE_SP - EditorPreferences.MIN_FONT_SIZE_SP) * 10).roundToInt()
            progress = ((displaySettings.fontSizeSp - EditorPreferences.MIN_FONT_SIZE_SP) * 10).roundToInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    updateFontSize(EditorPreferences.MIN_FONT_SIZE_SP + progress / 10f)
                    fontSizeLabel.text = "文字サイズ  %.1fsp".format(displaySettings.fontSizeSp)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        fontSizeLabel.text = "文字サイズ  %.1fsp".format(displaySettings.fontSizeSp)

        val lineSpacingLabel = TextView(this).apply {
            setTextColor(Color.WHITE)
        }
        val lineSpacingSeekBar = SeekBar(this).apply {
            max = ((EditorPreferences.MAX_LINE_SPACING - EditorPreferences.MIN_LINE_SPACING) * 20).roundToInt()
            progress = ((displaySettings.lineSpacingMultiplier - EditorPreferences.MIN_LINE_SPACING) * 20).roundToInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    updateLineSpacing(EditorPreferences.MIN_LINE_SPACING + progress / 20f)
                    lineSpacingLabel.text = "行間  %.0f%%".format(displaySettings.lineSpacingMultiplier * 100f)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        lineSpacingLabel.text = "行間  %.0f%%".format(displaySettings.lineSpacingMultiplier * 100f)

        val wrapSwitch = Switch(this).apply {
            text = "画面端で折り返す"
            setTextColor(Color.WHITE)
            isChecked = displaySettings.wrapLines
            setOnCheckedChangeListener { _, checked ->
                if (displaySettings.wrapLines != checked) {
                    toggleWrapLines()
                }
            }
        }

        container.addView(fontSizeLabel)
        container.addView(fontSizeSeekBar)
        container.addView(lineSpacingLabel)
        container.addView(lineSpacingSeekBar)
        container.addView(wrapSwitch)

        AlertDialog.Builder(this)
            .setTitle("表示設定")
            .setView(container)
            .setPositiveButton("閉じる", null)
            .show()
    }

    private fun restoreNote() {
        val snapshot = store.load()
        editor.setText(snapshot.text)
        editor.setSelection(snapshot.selectionStart, snapshot.selectionEnd)
        editor.post {
            editor.scrollTo(snapshot.scrollX, snapshot.scrollY)
            editor.requestFocus()
            loading = false
            showKeyboard()
        }
    }

    private fun observeEditor() {
        editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = scheduleSave()
            override fun afterTextChanged(s: Editable?) = Unit
        })
        editor.onEditorStateChanged = { if (!loading) scheduleSave() }
    }

    private fun scheduleSave() {
        if (loading) return
        mainHandler.removeCallbacks(saveRunnable)
        mainHandler.postDelayed(saveRunnable, AUTOSAVE_DELAY_MS)
    }

    private fun saveNow() {
        if (!::editor.isInitialized || loading) return
        mainHandler.removeCallbacks(saveRunnable)
        try {
            store.save(
                text = editor.text.toString(),
                selectionStart = editor.selectionStart,
                selectionEnd = editor.selectionEnd,
                scrollX = editor.scrollX,
                scrollY = editor.scrollY,
            )
        } catch (_: IOException) {
            // The next edit or lifecycle event will retry the local save.
        }
    }

    private fun showKeyboard() {
        editor.postDelayed({
            val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
        }, 150L)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val AUTOSAVE_DELAY_MS = 800L
    }
}
