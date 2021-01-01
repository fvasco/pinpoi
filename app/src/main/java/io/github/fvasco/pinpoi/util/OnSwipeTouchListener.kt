package io.github.fvasco.pinpoi.util

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * Detects left and right swipes across a view.
 */
class OnSwipeTouchListener(private val swipeTouchListener: OnSwipeTouchListener.SwipeTouchListener, context: Context) :
    GestureDetector.SimpleOnGestureListener(), View.OnTouchListener {
    private val gestureDetector = GestureDetector(context, this)

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event)
    }

    override fun onDown(e: MotionEvent): Boolean {
        return true
    }

    override fun onFling(e1: MotionEvent?, e2: MotionEvent?, velocityX: Float, velocityY: Float): Boolean {
        if (e1 != null && e2 != null) {
            val distanceX = e2.x - e1.x
            val distanceY = e2.y - e1.y
            if (abs(distanceX) > abs(distanceY) && abs(distanceX) > SWIPE_DISTANCE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                swipeTouchListener.onSwipe(if (distanceX > 0) SWIPE_RIGHT else SWIPE_LEFT)
                return true
            }
        }
        return false
    }

    interface SwipeTouchListener {
        fun onSwipe(direction: Boolean)
    }

    companion object {
        const val SWIPE_LEFT = false
        const val SWIPE_RIGHT = true
        private const val SWIPE_DISTANCE_THRESHOLD = 100
        private const val SWIPE_VELOCITY_THRESHOLD = 100
    }
}
