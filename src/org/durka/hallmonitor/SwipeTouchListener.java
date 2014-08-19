package org.durka.hallmonitor;

import android.content.Context;
import android.support.v4.view.GestureDetectorCompat;
import android.util.Log;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.RelativeLayout;

public class SwipeTouchListener implements OnTouchListener {

	public enum ActionMode { MODE_CALL, MODE_ALARM, MODE_TORCH; }

    private GestureDetectorCompat gestureDetector;
    private int _leftxDelta;
    private int _rightxDelta;
    private int _xDelta;
    private ActionMode actionMode;

    public SwipeTouchListener(Context context, ActionMode actionMode) {
        this.actionMode = actionMode;
        gestureDetector = new GestureDetectorCompat(context, new SwipeGestureListener());
    }

    public void onSwipeLeft() {
    	Log.d("Swipe", "Swipe Left, receive");

    	switch (this.actionMode) {
    	case MODE_CALL:
        	Log.d("Swipe", "Swipe Left, reject the call");
        	Functions.Actions.hangup_call();
    		break;
    	case MODE_ALARM:
        	Log.d("Swipe", "Swipe Left, dismiss alarm");
    		Functions.Actions.dismiss_alarm();
    		break;
    	case MODE_TORCH:
        	Log.d("Swipe", "Swipe Left, toogle torch");
    		Functions.Actions.toggle_torch(Functions.defaultActivity);
    		break;
    	}
    }

    public void onSwipeRight() {
    	Log.d("Swipe", "Swipe Right, receive");

    	switch (this.actionMode) {
    	case MODE_CALL:
        	Log.d("Swipe", "Swipe Right, pickup the call");
        	Functions.Actions.pickup_call();
    		break;
    	case MODE_ALARM:
        	Log.d("Swipe", "Swipe Right, snooze alarm");
    		Functions.Actions.snooze_alarm();
    		break;
    	case MODE_TORCH:
        	Log.d("Swipe", "Swipe Right, toogle torch only Left");
    		//Functions.Actions.toggle_torch(Functions.defaultActivity);
    		break;
    	}
    }

    public boolean onTouch(View v, MotionEvent event) {	  	  
        final int X = (int) event.getRawX();
        
        View leftImage = null;
        View rightImage = null;

        switch (this.actionMode) {
    	case MODE_CALL:
            leftImage = (View)Functions.defaultActivity.findViewById(R.id.pickup_button);
            rightImage = (View)Functions.defaultActivity.findViewById(R.id.hangup_button);
    		break;
    	case MODE_ALARM:
            leftImage = (View)Functions.defaultActivity.findViewById(R.id.snoozebutton);
            rightImage = (View)Functions.defaultActivity.findViewById(R.id.dismissbutton);
    		break;
    	case MODE_TORCH:
            leftImage = null;
            rightImage = (View)Functions.defaultActivity.findViewById(R.id.torchbutton);
    		break;
    	}
        switch (event.getAction() & MotionEvent.ACTION_MASK) {
        case MotionEvent.ACTION_DOWN:
            if(leftImage != null)
            	_leftxDelta = (int) (X - leftImage.getTranslationX());
            if(rightImage != null)
            	_rightxDelta = (int) (X - rightImage.getTranslationX());
            _xDelta = (int) (X);
            break;
        case MotionEvent.ACTION_UP:
            if(leftImage != null)
            	leftImage.setTranslationX(0);
            if(rightImage != null)
            	rightImage.setTranslationX(0);
            break;
        case MotionEvent.ACTION_POINTER_DOWN:
            break;
        case MotionEvent.ACTION_POINTER_UP:
            break;
        case MotionEvent.ACTION_MOVE:

            if(X < _xDelta) {
                if(leftImage != null)
                	leftImage.setTranslationX(0);
                if(rightImage != null)
                	rightImage.setTranslationX(X - _rightxDelta);
            }
            else if(X > _xDelta) {
                if(rightImage != null)
                	rightImage.setTranslationX(0);
                if(leftImage != null)
                	leftImage.setTranslationX(X - _leftxDelta);
            }
            break;
	    }
        return gestureDetector.onTouchEvent(event);
    }

    private final class SwipeGestureListener extends SimpleOnGestureListener {

        private static final int SWIPE_DISTANCE_THRESHOLD = 200;
        private static final int SWIPE_VELOCITY_THRESHOLD = 40;       
        
        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            return true;
        }
        
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            float distanceX = e2.getRawX() - e1.getRawX();
          
            if (Math.abs(distanceX) > SWIPE_DISTANCE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                if (distanceX > 0) {
                	onSwipeRight();
                } else {
                	onSwipeLeft();
                }
            }
        	return true;
        }
    }
}