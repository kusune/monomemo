package io.github.kusune.monomemo

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.FrameLayout
import java.io.IOException

class MainActivity : Activity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var editor: CursorAwareEditText
    private lateinit var store: LocalNoteStore
    private var loading = true

    private val saveRunnable = Runnable { saveNow() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureWindow()

        store = LocalNoteStore(this)
        editor = CursorAwareEditText(this).apply {
            setBackgroundColor(getColor(R.color.editor_background))
            setTextColor(getColor(R.color.editor_text))
            setHintTextColor(getColor(R.color.editor_hint))
            hint = ""
            textSize = 18f
            typeface = resources.getFont(R.font.biz_ud_gothic_regular)
            includeFontPadding = false
            setLineSpacing(0f, 1f)
            setPadding(dp(16), dp(12), dp(16), dp(24))
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
            isSingleLine = false
            setHorizontallyScrolling(true)
            setHorizontalScrollBarEnabled(false)
            setVerticalScrollBarEnabled(true)
        }

        setContentView(FrameLayout(this).apply {
            setBackgroundColor(getColor(R.color.editor_background))
            addView(editor, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ))
        })

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
        window.navigationBarColor = android.graphics.Color.BLACK
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE,
        )
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
