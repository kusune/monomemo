package io.github.kusune.monomemo

import android.content.Context
import android.util.AttributeSet
import android.widget.EditText
import android.widget.OverScroller
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.hypot

/** EditText with cursor/viewport callbacks and momentum scrolling. */
class CursorAwareEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle,
) : EditText(context, attrs, defStyleAttr) {
    var onEditorStateChanged: (() -> Unit)? = null

    private val flingScroller = OverScroller(context)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minimumFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    private val maximumFlingVelocity = ViewConfiguration.get(context).scaledMaximumFlingVelocity

    private var velocityTracker: VelocityTracker? = null
    private var downX = 0f
    private var downY = 0f
    private var movedEnough = false
    private var usedMultiplePointers = false
    private var selectionStartAtDown = 0
    private var selectionEndAtDown = 0

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                flingScroller.forceFinished(true)
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()
                downX = event.x
                downY = event.y
                movedEnough = false
                usedMultiplePointers = false
            }

            MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_POINTER_UP,
            -> usedMultiplePointers = true

            MotionEvent.ACTION_MOVE -> {
                if (!movedEnough && hypot(event.x - downX, event.y - downY) >= touchSlop) {
                    movedEnough = true
                }
            }
        }

        velocityTracker?.addMovement(event)
        val handled = super.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Let EditText establish focus/caret placement before deciding
                // whether a later drag changed the selection.
                selectionStartAtDown = selectionStart
                selectionEndAtDown = selectionEnd
            }

            MotionEvent.ACTION_UP -> {
                val shouldFling = movedEnough &&
                    !usedMultiplePointers &&
                    selectionStart == selectionStartAtDown &&
                    selectionEnd == selectionEndAtDown
                val started = if (shouldFling) startFling() else false
                recycleVelocityTracker()
                return handled || started
            }

            MotionEvent.ACTION_CANCEL -> recycleVelocityTracker()
        }
        return handled
    }

    override fun computeScroll() {
        super.computeScroll()
        if (flingScroller.computeScrollOffset()) {
            scrollTo(flingScroller.currX, flingScroller.currY)
            postInvalidateOnAnimation()
        }
    }

    override fun onDetachedFromWindow() {
        flingScroller.forceFinished(true)
        recycleVelocityTracker()
        super.onDetachedFromWindow()
    }

    private fun startFling(): Boolean {
        val tracker = velocityTracker ?: return false
        tracker.computeCurrentVelocity(1000, maximumFlingVelocity.toFloat())
        val velocityX = tracker.xVelocity
        val velocityY = tracker.yVelocity
        if (abs(velocityX) < minimumFlingVelocity && abs(velocityY) < minimumFlingVelocity) {
            return false
        }

        val maxX = (computeHorizontalScrollRange() - computeHorizontalScrollExtent()).coerceAtLeast(0)
        val maxY = (computeVerticalScrollRange() - computeVerticalScrollExtent()).coerceAtLeast(0)
        if (maxX == 0 && maxY == 0) return false

        // Finger velocity and content velocity point in opposite directions.
        flingScroller.fling(
            scrollX,
            scrollY,
            -velocityX.toInt(),
            -velocityY.toInt(),
            0,
            maxX,
            0,
            maxY,
        )
        postInvalidateOnAnimation()
        return true
    }

    private fun recycleVelocityTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

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
