package io.github.kusune.monomemo

import android.content.Context
import android.util.AttributeSet
import android.widget.EditText

/** EditText that exposes cursor and viewport changes to the local note store. */
class CursorAwareEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle,
) : EditText(context, attrs, defStyleAttr) {
    var onEditorStateChanged: (() -> Unit)? = null

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        onEditorStateChanged?.invoke()
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        if (l != oldl || t != oldt) {
            onEditorStateChanged?.invoke()
        }
    }
}
