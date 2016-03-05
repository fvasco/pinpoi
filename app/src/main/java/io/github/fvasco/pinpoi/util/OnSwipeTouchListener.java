package io.github.fvasco.pinpoi.util;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

/**
 * Detects left and right swipes across a view.
 */
public class OnSwipeTouchListener extends GestureDetector.SimpleOnGestureListener implements View.OnTouchListener {

    public static final boolean SWIPE_LEFT = false;
    public static final boolean SWIPE_RIGHT = true;
    private static final int SWIPE_DISTANCE_THRESHOLD = 100;
    private static final int SWIPE_VELOCITY_THRESHOLD = 100;
    private final GestureDetector gestureDetector;
    private final SwipeTouchListener swipeTouchListener;


    public OnSwipeTouchListener(SwipeTouchListener swipeTouchListener, Context context) {
        Util.requireNonNull(swipeTouchListener);
        this.swipeTouchListener = swipeTouchListener;
        gestureDetector = new GestureDetector(context, this);
    }

    public boolean onTouch(View v, MotionEvent event) {
        return gestureDetector.onTouchEvent(event);
    }

    @Override
    public boolean onDown(MotionEvent e) {
        return true;
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        if (e1 != null && e2 != null) {
            float distanceX = e2.getX() - e1.getX();
            float distanceY = e2.getY() - e1.getY();
            if (Math.abs(distanceX) > Math.abs(distanceY) && Math.abs(distanceX) > SWIPE_DISTANCE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                swipeTouchListener.onSwipe(distanceX > 0 ? SWIPE_RIGHT : SWIPE_LEFT);
                return true;
            }
        }
        return false;
    }

    public interface SwipeTouchListener {
        void onSwipe(boolean direction);
    }
}
