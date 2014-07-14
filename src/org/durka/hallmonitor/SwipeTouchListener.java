package org.durka.hallmonitor;

import android.content.ClipData;
import android.content.Context;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.DragShadowBuilder;
import android.view.View.OnTouchListener;
import android.widget.ImageView;

public class SwipeTouchListener implements OnTouchListener {

    private final GestureDetector gestureDetector;

    public SwipeTouchListener(Context context) {
        gestureDetector = new GestureDetector(context, new GestureListener());
    }

    public void onSwipeLeft() {
    	 Functions.Actions.hangup_call();
    	 Log.d("Swipe", "Swipe Left, reject the call");
    }

    public void onSwipeRight() {
    	Functions.Actions.pickup_call();
    	Log.d("Swipe", "Swipe Right, pickup the call");
    }

    public boolean onTouch(View v, MotionEvent event) {	  	  
        return gestureDetector.onTouchEvent(event);
    }

    private final class GestureListener extends SimpleOnGestureListener {

        private static final int SWIPE_DISTANCE_THRESHOLD = 350;
        private static final int SWIPE_VELOCITY_THRESHOLD = 40;
        
        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            float distanceX = e2.getX() - e1.getX();
            float distanceY = e2.getY() - e1.getY();
            ImageView hangup = (ImageView) Functions.defaultActivity.findViewById(R.id.hangup_button);
            ImageView pickup = (ImageView) Functions.defaultActivity.findViewById(R.id.pickup_button);
            
            if (Math.abs(distanceX) > Math.abs(distanceY) && Math.abs(distanceX) > SWIPE_DISTANCE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                if (distanceX > 0) {
                    onSwipeRight();
                    pickup.setTranslationX(e1.getX());
                    Log.d("Swipe", "moving pickup icon");
                } else {
                    onSwipeLeft();
                    hangup.setTranslationX(e2.getX());
                    Log.d("Swipe", "moving hangup icon");
                }
                return true;
            }
            return false;
        }
    }
}