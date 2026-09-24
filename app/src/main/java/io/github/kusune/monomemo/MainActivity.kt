package io.github.kusune.monomemo

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.text.LineBreakConfig
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.Layout
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
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
    private lateinit var undoButton: ImageButton
    private lateinit var redoButton: ImageButton
    private lateinit var wrapButton: ImageButton
    private lateinit var scaleDetector: ScaleGestureDetector
    private val editHistory = EditHistory()

    private var loading = true
    private var historyApplying = false
    private var pendingTextChange: PendingTextChange? = null
    private var nextSelectionAfterTextChange: Selection? = null
    private var historyRepeatAction: (() -> Boolean)? = null
    private var displaySettings = DisplaySettings(
        fontSizePt = EditorPreferences.DEFAULT_FONT_SIZE_PT,
        lineSpacingMultiplier = EditorPreferences.DEFAULT_LINE_SPACING,
        wrapLines = false,
    )
    private var historyRepeatSpeed = EditorPreferences.DEFAULT_HISTORY_REPEAT_SPEED

    private val saveRunnable = Runnable { saveNow() }
    private val historyRepeatRunnable = object : Runnable {
        override fun run() {
            val action = historyRepeatAction ?: return
            if (!action()) {
                stopHistoryRepeat()
                return
            }
            mainHandler.postDelayed(this, historyRepeatSpeed.intervalMillis)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureWindow()

        store = LocalNoteStore(this)
        editorPreferences = EditorPreferences(this)
        displaySettings = editorPreferences.load()
        historyRepeatSpeed = editorPreferences.loadHistoryRepeatSpeed()
        scaleDetector = createScaleDetector()
        editor = createEditor()

        setContentView(createScreen())
        applyDisplaySettings()
        restoreNote()
        editHistory.clear()
        observeEditor()
        updateHistoryButtons()
    }

    override fun onPause() {
        stopHistoryRepeat()
        saveNow()
        super.onPause()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(saveRunnable)
        stopHistoryRepeat()
        super.onDestroy()
    }

    private fun configureWindow() {
        window.statusBarColor = getColor(R.color.editor_background)
        window.navigationBarColor = Color.BLACK
        window.setSoftInputMode(
            // showKeyboard() handles the initial launch. Do not force the IME
            // visible again after the user dismisses it.
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
        )
    }

    private fun createEditor(): CursorAwareEditText = CursorAwareEditText(this).apply {
        id = R.id.editor
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
        configureCharacterWrapping()
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

        val pasteButton = createActionButton(R.drawable.ic_paste, "カーソル位置に貼り付け")
        pasteButton.setOnClickListener { pasteAtCursor() }
        addView(pasteButton)

        undoButton = createActionButton(R.drawable.ic_undo, "元に戻す（長押しで連続）")
        configureHistoryButton(undoButton, ::undo)
        addView(undoButton)

        redoButton = createActionButton(R.drawable.ic_redo, "やり直す（長押しで連続）")
        configureHistoryButton(redoButton, ::redo)
        addView(redoButton)

        wrapButton = createActionButton(R.drawable.ic_no_wrap, "折り返し切替")
        wrapButton.setOnClickListener { toggleWrapLines() }
        addView(wrapButton)

        val settingsButton = createActionButton(R.drawable.ic_settings, "設定")
        settingsButton.setOnClickListener { showSettings() }
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
                val nextSize = displaySettings.fontSizePt * detector.scaleFactor
                updateFontSize(nextSize)
                return true
            }
        },
    )

    private fun applyDisplaySettings() {
        // Android's physical PT unit becomes unexpectedly large on high-density
        // phones. This logical editor scale keeps 12pt close to 16sp.
        editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, displaySettings.fontSizePt * 4f / 3f)
        editor.setLineSpacing(0f, displaySettings.lineSpacingMultiplier)
        editor.setHorizontallyScrolling(!displaySettings.wrapLines)
        editor.requestLayout()
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
        val next = (value * 2f).roundToInt() / 2f
        displaySettings = displaySettings.copy(
            fontSizePt = next.coerceIn(
                EditorPreferences.MIN_FONT_SIZE_PT,
                EditorPreferences.MAX_FONT_SIZE_PT,
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
            menu.add("元に戻す").apply {
                isEnabled = editHistory.canUndo
                setOnMenuItemClickListener {
                    undo()
                    true
                }
            }
            menu.add("やり直す").apply {
                isEnabled = editHistory.canRedo
                setOnMenuItemClickListener {
                    redo()
                    true
                }
            }
            menu.add("設定").setOnMenuItemClickListener {
                showSettings()
                true
            }
            menu.add(if (displaySettings.wrapLines) "折り返しを解除" else "折り返しを有効化")
                .setOnMenuItemClickListener {
                    toggleWrapLines()
                    true
                }
            menu.add("ソフトウェア情報").setOnMenuItemClickListener {
                showSoftwareInfo()
                true
            }
            show()
        }
    }

    private fun showSettings() {
        val originalSettings = displaySettings
        val originalHistoryRepeatSpeed = historyRepeatSpeed
        var draftSettings = originalSettings
        var draftHistoryRepeatSpeed = originalHistoryRepeatSpeed
        var committed = false

        fun preview(settings: DisplaySettings) {
            draftSettings = settings
            displaySettings = settings
            applyDisplaySettings()
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(4), dp(24), 0)
        }

        val fontSizeLabel = TextView(this).apply {
            setTextColor(Color.WHITE)
        }
        val fontSizeSeekBar = SeekBar(this).apply {
            max = ((EditorPreferences.MAX_FONT_SIZE_PT - EditorPreferences.MIN_FONT_SIZE_PT) * 2).roundToInt()
            progress = ((draftSettings.fontSizePt - EditorPreferences.MIN_FONT_SIZE_PT) * 2).roundToInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    preview(draftSettings.copy(
                        fontSizePt = EditorPreferences.MIN_FONT_SIZE_PT + progress / 2f,
                    ))
                    fontSizeLabel.text = "文字サイズ  %.1fpt".format(draftSettings.fontSizePt)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        fontSizeLabel.text = "文字サイズ  %.1fpt".format(draftSettings.fontSizePt)

        val lineSpacingLabel = TextView(this).apply {
            setTextColor(Color.WHITE)
        }
        val lineSpacingSeekBar = SeekBar(this).apply {
            max = ((EditorPreferences.MAX_LINE_SPACING - EditorPreferences.MIN_LINE_SPACING) * 20).roundToInt()
            progress = ((draftSettings.lineSpacingMultiplier - EditorPreferences.MIN_LINE_SPACING) * 20).roundToInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    preview(draftSettings.copy(
                        lineSpacingMultiplier = EditorPreferences.MIN_LINE_SPACING + progress / 20f,
                    ))
                    lineSpacingLabel.text = "行間  %.0f%%".format(draftSettings.lineSpacingMultiplier * 100f)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        lineSpacingLabel.text = "行間  %.0f%%".format(draftSettings.lineSpacingMultiplier * 100f)

        val repeatSpeedLabel = TextView(this).apply {
            setTextColor(Color.WHITE)
        }
        val repeatSpeedOptions = HistoryRepeatSpeed.values()
        val repeatSpeedSeekBar = SeekBar(this).apply {
            max = repeatSpeedOptions.lastIndex
            progress = repeatSpeedOptions.indexOf(draftHistoryRepeatSpeed)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    draftHistoryRepeatSpeed = repeatSpeedOptions[progress.coerceIn(0, max)]
                    repeatSpeedLabel.text = repeatSpeedText(draftHistoryRepeatSpeed)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        repeatSpeedLabel.text = repeatSpeedText(draftHistoryRepeatSpeed)

        val wrapSwitch = Switch(this).apply {
            text = "画面端で折り返す"
            setTextColor(Color.WHITE)
            isChecked = draftSettings.wrapLines
            setOnCheckedChangeListener { _, checked ->
                preview(draftSettings.copy(wrapLines = checked))
            }
        }

        container.addView(fontSizeLabel)
        container.addView(fontSizeSeekBar)
        container.addView(lineSpacingLabel)
        container.addView(lineSpacingSeekBar)
        container.addView(repeatSpeedLabel)
        container.addView(repeatSpeedSeekBar)
        container.addView(wrapSwitch)

        val dialog = AlertDialog.Builder(this)
            .setTitle("設定")
            .setView(container)
            .setNegativeButton("キャンセル", null)
            .setPositiveButton("決定", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                committed = true
                displaySettings = draftSettings
                historyRepeatSpeed = draftHistoryRepeatSpeed
                editorPreferences.save(displaySettings)
                editorPreferences.saveHistoryRepeatSpeed(historyRepeatSpeed)
                applyDisplaySettings()
                dialog.dismiss()
            }
        }
        dialog.setOnDismissListener {
            if (!committed) {
                displaySettings = originalSettings
                historyRepeatSpeed = originalHistoryRepeatSpeed
                applyDisplaySettings()
            }
        }
        dialog.show()
    }

    @SuppressLint("WrongConstant")
    private fun CursorAwareEditText.configureCharacterWrapping() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            breakStrategy = Layout.BREAK_STRATEGY_SIMPLE
            hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            // Avoid dictionary/phrase-based wrapping when the platform supports
            // explicit line-break configuration.
            setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_NONE)
            setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_NONE)
        }
    }

    private fun pasteAtCursor() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip ?: return
        if (clip.itemCount == 0) return
        val pasted = clip.getItemAt(0).coerceToText(this)
        if (pasted.isNullOrEmpty()) return

        // Use the caret's end position and do not replace a selected range.
        val insertionPoint = editor.selectionEnd.coerceIn(0, editor.text.length)
        nextSelectionAfterTextChange = Selection(
            start = insertionPoint + pasted.length,
            end = insertionPoint + pasted.length,
        )
        try {
            editor.text.insert(insertionPoint, pasted)
            editor.setSelection(insertionPoint + pasted.length)
        } finally {
            nextSelectionAfterTextChange = null
        }
        editor.requestFocus()
        scheduleSave()
    }

    private fun undo(): Boolean {
        val target = editHistory.undo(currentEditorState()) ?: return false
        applyHistoryState(target)
        return true
    }

    private fun redo(): Boolean {
        val target = editHistory.redo(currentEditorState()) ?: return false
        applyHistoryState(target)
        return true
    }

    private fun configureHistoryButton(button: ImageButton, action: () -> Boolean) {
        button.setOnClickListener { action() }
        button.setOnLongClickListener {
            startHistoryRepeat(action)
            true
        }
        button.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                stopHistoryRepeat()
            }
            false
        }
    }

    private fun startHistoryRepeat(action: () -> Boolean) {
        stopHistoryRepeat()
        historyRepeatAction = action
        if (!action()) {
            stopHistoryRepeat()
            return
        }
        mainHandler.postDelayed(historyRepeatRunnable, historyRepeatSpeed.intervalMillis)
    }

    private fun stopHistoryRepeat() {
        historyRepeatAction = null
        mainHandler.removeCallbacks(historyRepeatRunnable)
    }

    private fun repeatSpeedText(speed: HistoryRepeatSpeed): String =
        "Undo/Redo連続速度  ${speed.multiplier}倍（${speed.intervalMillis}ms間隔）"

    private fun currentEditorState(): EditorState = EditorState(
        text = editor.text.toString(),
        selectionStart = editor.selectionStart,
        selectionEnd = editor.selectionEnd,
    ).normalized()

    private fun applyHistoryState(state: EditorState) {
        val scrollX = editor.scrollX
        val scrollY = editor.scrollY
        historyApplying = true
        try {
            val normalized = state.normalized()
            editor.setText(normalized.text)
            editor.setSelection(normalized.selectionStart, normalized.selectionEnd)
        } finally {
            historyApplying = false
        }
        editor.post { editor.scrollTo(scrollX, scrollY) }
        updateHistoryButtons()
        scheduleSave()
    }

    private fun updateHistoryButtons() {
        if (!::undoButton.isInitialized || !::redoButton.isInitialized) return
        undoButton.isEnabled = editHistory.canUndo
        undoButton.alpha = if (editHistory.canUndo) 1f else DISABLED_BUTTON_ALPHA
        redoButton.isEnabled = editHistory.canRedo
        redoButton.alpha = if (editHistory.canRedo) 1f else DISABLED_BUTTON_ALPHA
    }

    private fun showSoftwareInfo() {
        AlertDialog.Builder(this)
            .setTitle("ソフトウェア情報")
            .setMessage(
                "MonoMemo ${BuildConfig.VERSION_NAME}\n\n" +
                    "GNU General Public License v3.0\n" +
                    "Bundled font: BIZ UDGothic (SIL Open Font License 1.1)\n\n" +
                    "https://github.com/kusune/monomemo",
            )
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
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                pendingTextChange = null
                if (loading || historyApplying) {
                    return
                }
                val text = s ?: return
                val safeStart = start.coerceIn(0, text.length)
                val safeEnd = (safeStart + count).coerceAtMost(text.length)
                pendingTextChange = PendingTextChange(
                    start = safeStart,
                    removedText = text.subSequence(safeStart, safeEnd).toString(),
                    selectionBeforeStart = editor.selectionStart,
                    selectionBeforeEnd = editor.selectionEnd,
                )
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (loading || historyApplying) return
                val change = pendingTextChange ?: return
                val text = s ?: return
                val safeStart = start.coerceIn(0, text.length)
                val safeEnd = (safeStart + count).coerceAtMost(text.length)
                pendingTextChange = change.copy(
                    start = safeStart,
                    insertedText = text.subSequence(safeStart, safeEnd).toString(),
                )
                scheduleSave()
            }

            override fun afterTextChanged(s: Editable?) {
                val change = pendingTextChange ?: return
                pendingTextChange = null
                if (loading || historyApplying) return

                val recorded = editHistory.record(
                    TextEdit(
                        start = change.start,
                        removedText = change.removedText,
                        insertedText = change.insertedText,
                        selectionBeforeStart = change.selectionBeforeStart,
                        selectionBeforeEnd = change.selectionBeforeEnd,
                        selectionAfterStart = (nextSelectionAfterTextChange?.start ?: editor.selectionStart),
                        selectionAfterEnd = (nextSelectionAfterTextChange?.end ?: editor.selectionEnd),
                    ),
                )
                nextSelectionAfterTextChange = null
                if (recorded) updateHistoryButtons()
            }
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

    private data class PendingTextChange(
        val start: Int,
        val removedText: String,
        val selectionBeforeStart: Int,
        val selectionBeforeEnd: Int,
        val insertedText: String = "",
    )

    private data class Selection(
        val start: Int,
        val end: Int,
    )

    companion object {
        private const val DISABLED_BUTTON_ALPHA = 0.35f
        private const val AUTOSAVE_DELAY_MS = 800L
    }
}
