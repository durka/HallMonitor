package org.durka.hallmonitor;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v4.view.MotionEventCompat;
import android.util.Log;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;

//import android.graphics.Color;

@SuppressLint("ClickableViewAccessibility")
public class SwipeTouchListener implements OnTouchListener {

	private final String LOG_TAG = "Hall.Swipe";

	private final CoreStateManager mStateManager;

	public enum ActionMode {
		MODE_NOTHINGTRUE, MODE_CALL, MODE_ALARM, MODE_TORCH, MODE_CAMERA;
	}

	private final GestureDetectorCompat gestureDetector;
	private int _leftxDelta;
	private int _rightxDelta;
	private int _xDelta;
	private final ActionMode actionMode;
	private final Context ctx;
	private int mActivePointerId = MotionEvent.INVALID_POINTER_ID;

	public SwipeTouchListener(Context context, ActionMode actionMode) {
		this.ctx = context;
		mStateManager = ((CoreApp) context.getApplicationContext())
				.getStateManager();
		this.actionMode = actionMode;
		this.gestureDetector = new GestureDetectorCompat(this.ctx,
				new SwipeGestureListener());
	}

	private void onSwipeLeft() {
		Log.d(LOG_TAG, "Swipe Left, receive");

		switch (this.actionMode) {
		case MODE_CALL:
			Log.d(LOG_TAG, "Swipe Left, reject the call");
			Intent hangUpCallIntent = new Intent(ctx, CoreService.class);
			hangUpCallIntent.putExtra(CoreApp.CS_EXTRA_TASK,
					CoreApp.CS_TASK_HANGUP_CALL);
			mStateManager.sendToCoreService(hangUpCallIntent);
			break;
		case MODE_ALARM:
			Log.d(LOG_TAG, "Swipe Left, dismiss... I am wake");
			Intent alarmDismiss = new Intent(ctx, CoreService.class);
			alarmDismiss.putExtra(CoreApp.CS_EXTRA_TASK,
					CoreApp.CS_TASK_DISMISS_ALARM);
			mStateManager.sendToCoreService(alarmDismiss);
			break;
		case MODE_TORCH:
			Log.d(LOG_TAG, "Swipe Left, toggle torch");
			Intent torchIntent = new Intent(ctx, CoreService.class);
			torchIntent.putExtra(CoreApp.CS_EXTRA_TASK,
					CoreApp.CS_TASK_TORCH_TOGGLE);
			mStateManager.sendToCoreService(torchIntent);
			break;
		case MODE_CAMERA:
			Log.d(LOG_TAG, "Swipe Left, toggle camera");
			Intent cameraIntent = new Intent(CoreApp.DA_ACTION_START_CAMERA);
			LocalBroadcastManager mLocalBroadcastManager = LocalBroadcastManager
					.getInstance(ctx);
			mLocalBroadcastManager.sendBroadcastSync(cameraIntent);
			break;
		case MODE_NOTHINGTRUE:
			break;
		}
	}

	private void onSwipeRight() {
		Log.d(LOG_TAG, "Swipe Right, receive");

		switch (this.actionMode) {
		case MODE_CALL:
			Log.d(LOG_TAG, "Swipe Right, pickup the call");
			Intent pickUpCallIntent = new Intent(ctx, CoreService.class);
			pickUpCallIntent.putExtra(CoreApp.CS_EXTRA_TASK,
					CoreApp.CS_TASK_PICKUP_CALL);
			mStateManager.sendToCoreService(pickUpCallIntent);
			break;
		case MODE_ALARM:
			Log.d(LOG_TAG, "Swipe Right, snooze alarm");
			Intent torchIntent = new Intent(ctx, CoreService.class);
			torchIntent.putExtra(CoreApp.CS_EXTRA_TASK,
					CoreApp.CS_TASK_SNOOZE_ALARM);
			mStateManager.sendToCoreService(torchIntent);
			break;
		case MODE_TORCH:
			Log.d(LOG_TAG, "Swipe Right, toggle torch only Left");
			break;
		case MODE_CAMERA:
			Log.d(LOG_TAG, "Swipe Right, toggle camera only Left");
			break;
		case MODE_NOTHINGTRUE:
			break;
		}
	}

	private void sendMotionEvent(final MotionEvent event,
			final int pointerIndex, float x, float y) {
		MotionEvent singleEvent = MotionEvent.obtain(event.getDownTime(),
				event.getEventTime(), event.getActionMasked(), x, y,
				event.getPressure(pointerIndex), event.getSize(pointerIndex),
				event.getMetaState(), event.getXPrecision(),
				event.getYPrecision(), event.getDeviceId(),
				event.getEdgeFlags());
		this.gestureDetector.onTouchEvent(singleEvent);
		singleEvent.recycle();
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		View leftImage = null;
		View rightImage = null;

		boolean consumedEvent = false;

		switch (this.actionMode) {
		case MODE_CALL:
			leftImage = ((DefaultActivity) ctx)
					.findViewById(R.id.pickup_button);
			rightImage = ((DefaultActivity) ctx)
					.findViewById(R.id.hangup_button);
			break;
		case MODE_ALARM:
			leftImage = ((DefaultActivity) ctx).findViewById(R.id.snoozebutton);
			rightImage = ((DefaultActivity) ctx)
					.findViewById(R.id.dismissbutton);
			break;
		case MODE_TORCH:
			leftImage = null;
			rightImage = ((DefaultActivity) ctx).findViewById(R.id.torchbutton);
			break;
		case MODE_CAMERA:
			leftImage = null;
			rightImage = ((DefaultActivity) ctx)
					.findViewById(R.id.camerabutton);
			break;
		case MODE_NOTHINGTRUE:
			return consumedEvent = true;
		}

		final int action = MotionEventCompat.getActionMasked(event);

		switch (action) {
		case MotionEvent.ACTION_POINTER_DOWN:
		case MotionEvent.ACTION_DOWN: {
			// v.setBackgroundColor(Color.RED);
			// v.invalidate();

			if (mActivePointerId != MotionEvent.INVALID_POINTER_ID) {
				break;
			}

			final int pointerIndex = MotionEventCompat.getActionIndex(event);
			final int pointerId = MotionEventCompat.getPointerId(event,
					pointerIndex);

			// Get absolute coordinate of view
			int pos[] = new int[2];
			v.getLocationOnScreen(pos);

			// v.setBackgroundColor(Color.MAGENTA);
			// v.invalidate();

			// // Only take pointer in our view
			// if (MotionEventCompat.getX(event, pointerIndex) >= pos[0]
			// && MotionEventCompat.getX(event, pointerIndex) <= (pos[0] + v
			// .getWidth())
			// && MotionEventCompat.getY(event, pointerIndex) >= pos[1]
			// && MotionEventCompat.getY(event, pointerIndex) <= (pos[1] + v
			// .getHeight())) {
			// mActivePointerId = pointerId;
			// } else {
			// mActivePointerId = MotionEvent.INVALID_POINTER_ID;
			// // But follow all next touch
			// consumedEvent = true;
			// break;
			// }
			// v.setBackgroundColor(Color.YELLOW);
			// v.invalidate();
			mActivePointerId = pointerId;

			// Disable sleep/lock timerTask
			mStateManager.setBlackScreenTime(0);

			final int X = (int) MotionEventCompat.getX(event, pointerIndex);
			// final int Y = (int)MotionEventCompat.getY(event, pointerIndex);

			// Move image
			if (leftImage != null) {
				_leftxDelta = (int) (X - leftImage.getTranslationX());
			}
			if (rightImage != null) {
				_rightxDelta = (int) (X - rightImage.getTranslationX());
			}
			_xDelta = (X);

			// Forward to gestureDetector
			this.sendMotionEvent(event, pointerIndex,
					X * event.getXPrecision(), 0.0f);

			consumedEvent = true;
			break;
		}
		case MotionEvent.ACTION_MOVE: {
			int pointerIndex = MotionEventCompat.findPointerIndex(event,
					mActivePointerId);

			if (pointerIndex == -1) {
				break;
			}

			mStateManager.setBlackScreenTime(0);

			// v.setBackgroundColor(Color.GREEN);
			// v.invalidate();

			// Get absolute coordinate from event
			int pos[] = new int[2];
			v.getLocationOnScreen(pos);
			final int X = (int) MotionEventCompat.getX(event, pointerIndex)
					+ pos[0];
			// final int Y = (int)MotionEventCompat.getY(event, pointerIndex) +
			// pos[1];

			// Move image
			if (X < _xDelta) {
				if (leftImage != null) {
					leftImage.setTranslationX(0);
				}
				if (rightImage != null) {
					rightImage.setTranslationX(X - _rightxDelta);
				}
			} else if (X > _xDelta) {
				if (rightImage != null) {
					rightImage.setTranslationX(0);
				}
				if (leftImage != null) {
					leftImage.setTranslationX(X - _leftxDelta);
				}
			}

			// Forward to gestureDetector
			this.sendMotionEvent(event, pointerIndex,
					X * event.getXPrecision(), 0.0f);

			consumedEvent = true;
			break;
		}
		case MotionEvent.ACTION_POINTER_UP:
		case MotionEvent.ACTION_UP: {
			// v.setBackgroundColor(Color.CYAN);
			// v.invalidate();

			final int pointerIndex = MotionEventCompat.getActionIndex(event);
			final int pointerId = MotionEventCompat.getPointerId(event,
					pointerIndex);

			if (pointerId != mActivePointerId) {
				break;
			}

			// Get absolute coordinate from event
			int pos[] = new int[2];
			v.getLocationOnScreen(pos);
			final int X = (int) MotionEventCompat.getX(event, pointerIndex)
					+ pos[0];
			// final int Y = (int)MotionEventCompat.getY(event, pointerIndex) +
			// pos[1];

			// Reset image
			if (leftImage != null) {
				leftImage.setTranslationX(0);
			}
			if (rightImage != null) {
				rightImage.setTranslationX(0);
			}

			// Forward to gestureDetector
			this.sendMotionEvent(event, pointerIndex,
					X * event.getXPrecision(), 0.0f);

			consumedEvent = true;

			// Enable sleep/lock timerTask
			Intent mIntent = new Intent(ctx, CoreService.class);
			mIntent.putExtra(CoreApp.CS_EXTRA_TASK,
					CoreApp.CS_TASK_AUTO_BLACKSCREEN);
			mStateManager.sendToCoreService(mIntent);

			// End our touch event
			mActivePointerId = MotionEvent.INVALID_POINTER_ID;
			break;
		}
		case MotionEvent.ACTION_CANCEL: {
			// v.setBackgroundColor(Color.WHITE);
			// v.invalidate();

			final int pointerIndex = MotionEventCompat.getActionIndex(event);
			final int pointerId = MotionEventCompat.getPointerId(event,
					pointerIndex);

			if (pointerId != mActivePointerId) {
				break;
			}

			consumedEvent = true;

			// Enable sleep/lock timerTask
			Intent mIntent = new Intent(ctx, CoreService.class);
			mIntent.putExtra(CoreApp.CS_EXTRA_TASK,
					CoreApp.CS_TASK_AUTO_BLACKSCREEN);
			mStateManager.sendToCoreService(mIntent);

			mActivePointerId = MotionEvent.INVALID_POINTER_ID;
			break;
		}
		}
		return consumedEvent;
	}

	private final class SwipeGestureListener extends SimpleOnGestureListener {

		private static final int SWIPE_DISTANCE_THRESHOLD = 200;
		private static final int SWIPE_VELOCITY_THRESHOLD = 40;

		@Override
		public boolean onDown(MotionEvent e) {
			return true;
		}

		@Override
		public boolean onScroll(MotionEvent e1, MotionEvent e2,
				float distanceX, float distanceY) {
			return true;
		}

		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
				float velocityY) {
			float distanceX = e2.getRawX() - e1.getRawX();

			if (Math.abs(distanceX) > SWIPE_DISTANCE_THRESHOLD
					&& Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
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