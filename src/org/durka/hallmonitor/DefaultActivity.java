package org.durka.hallmonitor;

import java.util.Timer;
import java.util.TimerTask;

import org.durka.hallmonitor.Functions.Util;

import android.app.Activity;
import android.appwidget.AppWidgetHostView;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.net.Uri;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources.NotFoundException;
import android.database.Cursor;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.BatteryManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.provider.ContactsContract.PhoneLookup;
import android.service.notification.StatusBarNotification;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.GridLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextClock;
import android.widget.RelativeLayout;
import android.widget.TextView;

/**
 * This is the activity that is displayed by default - it is displayed for the configurable delay number of milliseconds when the case is closed,
 * it is also displayed when the power button is pressed when the case is already closed
 */
public class DefaultActivity extends Activity {
	private HMAppWidgetManager hmAppWidgetManager = Functions.hmAppWidgetManager;

	public static boolean on_screen;

	// states for alarm and phone
	public static boolean alarm_firing = false;
	public static boolean phone_ringing = false;

	//audio manager to detect media state
	private AudioManager audioManager;

	//Action fired when alarm goes off
    public static final String ALARM_ALERT_ACTION = "com.android.deskclock.ALARM_ALERT";
    //Action to trigger snooze of the alarm
    public static final String ALARM_SNOOZE_ACTION = "com.android.deskclock.ALARM_SNOOZE";
    //Action to trigger dismiss of the alarm
    public static final String ALARM_DISMISS_ACTION = "com.android.deskclock.ALARM_DISMISS";
    //This action should let us know if the alarm has been killed by another app
    public static final String ALARM_DONE_ACTION = "com.android.deskclock.ALARM_DONE";
    
    //all the views we need
    private GridView grid = null;
    private View snoozeButton = null;
    private View dismissButton = null;
    private View defaultWidget = null;
    private RelativeLayout defaultContent = null;
    private TextClock defaultTextClock = null;
    
    /**
     *  phone widget
     */
    protected GridLayout phoneView;
    protected TextView mCallerName;
    protected TextView mCallerNumber;
    protected View mAcceptButton;
    protected View mAcceptSlide;
    protected View mRejectButton;
    protected View mRejectSlide;

    private boolean mViewNeedsReset = false;

    // drawing stuff
    private final static int mButtonMargin = 15; // designer use just 10dp; ode rendering issue
    private final static int mRedrawOffset = 10; // move min 10dp before redraw

    private int mActivePointerId = -1;
    /**
     *  phone widget (end)
     */
    	
	//we need to kill this activity when the screen opens
	private final BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {

				Log.d("DA.onReceive", "Screen on event received.");

				if (Functions.Is.cover_closed(context)) {
					Log.d("DA.onReceive", "Cover is closed, display Default Activity.");
					//easiest way to do this is actually just to invoke the close_cover action as it does what we want
					Functions.Actions.close_cover(getApplicationContext());
				} else {
					Log.d("DA.onReceive", "Cover is open, stopping Default Activity.");

					// when the cover opens, the fullscreen activity goes poof				
					moveTaskToBack(true);
				}

			} else if (intent.getAction().equals(ALARM_ALERT_ACTION)) {

				Log.d("DA.onReceive", "Alarm on event received.");

				//only take action if alarm controls are enabled
				if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("pref_alarm_controls", false)) {

					Log.d("DA.onReceive", "Alarm controls are enabled, taking action.");

					//set the alarm firing state
					alarm_firing=true;

					//if the cover is closed then
					//we want to pop this activity up over the top of the alarm activity
					//to guarantee that we need to hold off until the alarm activity is running
					//a 1 second delay seems to allow this
					if (Functions.Is.cover_closed(context)) {
						Timer timer = new Timer();
						timer.schedule(new TimerTask() {
							@Override
							public void run() {	
								Intent myIntent = new Intent(getApplicationContext(), DefaultActivity.class);
								myIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK 
										| Intent.FLAG_ACTIVITY_CLEAR_TOP
										| WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
								myIntent.setAction(Intent.ACTION_MAIN);
								startActivity(myIntent);
							}
						}, 1000);	
					}
				} else {
					Log.d("DA.onReceive", "Alarm controls are not enabled.");
				}

			
			} else if (intent.getAction().equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED)) {
				String phoneExtraState = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
				boolean isRinging = phoneExtraState.equals(TelephonyManager.EXTRA_STATE_RINGING);
				
				Log.d("DA", "onReceive: ACTION_PHONE_STATE_CHANGED = " + phoneExtraState + ", riniging = " + isRinging);
				
				if (isRinging) {
					Functions.Events.incoming_call(context, intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER));
				} else {
					Log.d("phone", "phone state changed to " + phoneExtraState);
					if (phone_ringing && phoneExtraState.equals(TelephonyManager.EXTRA_STATE_IDLE)) {
						phone_ringing = false;
						refreshDisplay();
						
						// rearm screen off timer
						Functions.Actions.rearmScreenOffTimer(context);
					}
				}
			} else if (intent.getAction().equals("org.durka.hallmonitor.debug")) {
				Log.d("DA.onReceive", "received debug intent");
				// test intent to show/hide a notification
				switch (intent.getIntExtra("notif", 0)) {
				case 1:
					Functions.Actions.debug_notification(context, true);
					break;
				case 2:
					Functions.Actions.debug_notification(context, false);
					break;
				}
			}
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		//get the audio manager
		audioManager = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);

		//pass a reference back to the Functions class so it can finish us when it wants to
		//FIXME Presumably there is a better way to do this
		Functions.defaultActivity = this;

		Log.d("DA.onCreate", "onCreate of DefaultView.");

		//Remove title bar
		this.requestWindowFeature(Window.FEATURE_NO_TITLE);

		//Remove notification bar
		this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

		//set default view
		setContentView(R.layout.activity_default);

		//add screen on and alarm fired intent receiver
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
		IntentFilter filter = new IntentFilter();
		filter.addAction(Intent.ACTION_SCREEN_ON);
		filter.addAction(ALARM_ALERT_ACTION);
		filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
		filter.addAction("org.durka.hallmonitor.debug");
		registerReceiver(receiver, filter);
		
		//get the views we need
		grid = (GridView)findViewById(R.id.default_icon_container);
	    snoozeButton = findViewById(R.id.snoozebutton);
	    dismissButton = findViewById(R.id.dismissbutton);
	    defaultWidget = findViewById(R.id.default_widget);
	    defaultContent = (RelativeLayout) findViewById(R.id.default_content);
	    defaultTextClock = (TextClock) findViewById(R.id.default_text_clock);

	    // phone_widget
	    phoneView = (GridLayout)findViewById(R.id.phone_widget);
        mCallerName = (TextView)findViewById(R.id.caller_name);
        mCallerNumber = (TextView)findViewById(R.id.caller_number);
        mAcceptButton = findViewById(R.id.call_accept_button);
        mAcceptSlide = findViewById(R.id.call_accept_slide);
        mRejectButton = findViewById(R.id.call_reject_button);
        mRejectSlide = findViewById(R.id.call_reject_slide);
	}  

	@Override
	protected void onResume() {
		super.onResume();

		Log.d("DA.onResume", "On resume called.");
			
		refreshDisplay();
	}

	/**
	 * Refresh the display taking account of device and application state
	 */
	@SuppressWarnings("deprecation")
	public void refreshDisplay() {

		//get the layout for the windowed view
	    RelativeLayout contentView = defaultContent;
	    
	    //if the alarm is firing then show the alarm controls, otherwise
	    //if we have a media app widget and media is playing or headphones are connected then display that, otherwise
	    //if we have a default app widget to use then display that, if not then display our default clock screen
	    //(which is part of the default layout so will show anyway)
	    //will do this simply by setting the widgetType
	    String widgetType = "default";
	    if (hmAppWidgetManager.doesWidgetExist("media") && (audioManager.isWiredHeadsetOn() || audioManager.isMusicActive())) {
	    	widgetType = "media";
	    }
	    
	    if (alarm_firing) {
	    	Log.d("DA", "refreshDisplay: alarm_firing");
	    	//show the alarm controls
	    	defaultContent.setVisibility(View.VISIBLE);
	    	phoneView.setVisibility(View.INVISIBLE);
	    	
	    	snoozeButton.setVisibility(View.VISIBLE);
	    	dismissButton.setVisibility(View.VISIBLE);
	    	defaultWidget.setVisibility(View.INVISIBLE);
	    	grid.setVisibility(View.INVISIBLE);
	    } else if (phone_ringing) {
	    	Log.d("DA", "refreshDisplay: phone_ringing");
	    	//show the phone controls
    		defaultContent.setVisibility(View.INVISIBLE);
	    	phoneView.setVisibility(View.VISIBLE);
	    	
	    	snoozeButton.setVisibility(View.INVISIBLE);
	    	dismissButton.setVisibility(View.INVISIBLE);
	    	defaultWidget.setVisibility(View.INVISIBLE);
	    	grid.setVisibility(View.INVISIBLE);
	    	
			// reset to defaults
			resetPhoneWidgetMakeVisible();

			// parse parameter
	    	setIncomingNumber(getIntent().getStringExtra("incomingNumber"));
	    } else {
	    	//default view    	
	    	defaultContent.setVisibility(View.VISIBLE);
	    	phoneView.setVisibility(View.INVISIBLE);

	    	//add the required widget based on the widgetType
		    if (hmAppWidgetManager.doesWidgetExist(widgetType)) {
		    	Log.d("DA", "refreshDisplay: default_widget");
		    	
		    	//remove the TextClock from the contentview
			    contentView.removeAllViews();
		    	
		    	//get the widget
			    AppWidgetHostView hostView = hmAppWidgetManager.getAppWidgetHostViewByType(widgetType);
			    
			    //if the widget host view already has a parent then we need to detach it
			    ViewGroup parent = (ViewGroup)hostView.getParent();
			    if ( parent != null) {
			    	Log.d("DA.onCreate", "hostView had already been added to a group, detaching it.");
			       	parent.removeView(hostView);
			    }    
			    
			    //add the widget to the view
			    contentView.addView(hostView);
		    } else {
		    	Log.d("DA", "refreshDisplay: default_widget");
			    
		    	snoozeButton.setVisibility(View.INVISIBLE);
		    	dismissButton.setVisibility(View.INVISIBLE);
		    	defaultWidget.setVisibility(View.VISIBLE);
		    	grid.setVisibility(View.VISIBLE);
		    	
		    	Drawable rounded = getResources().getDrawable(R.drawable.rounded);
		    	rounded.setColorFilter(new PorterDuffColorFilter(PreferenceManager.getDefaultSharedPreferences(this).getInt("pref_default_bgcolor", 0xFF000000), PorterDuff.Mode.MULTIPLY));
		    	//defaultContent.setBackground(rounded);
		    	defaultTextClock.setTextColor(PreferenceManager.getDefaultSharedPreferences(this).getInt("pref_default_fgcolor", 0xFFFFFFFF));
		    }
	    }
	}


	/** Called when the user touches the snooze button */
	public void sendSnooze(View view) {
		// Broadcast alarm snooze event
		Intent alarmSnooze = new Intent(ALARM_SNOOZE_ACTION);
		sendBroadcast(alarmSnooze);
		//unset alarm firing flag
		alarm_firing = false;
		//refresh the display
		refreshDisplay();
	}

	/** Called when the user touches the dismiss button */
	public void sendDismiss(View view) {
		// Broadcast alarm dismiss event
		Intent alarmDismiss = new Intent(ALARM_DISMISS_ACTION);
		sendBroadcast(alarmDismiss);
		//unset alarm firing flag
		alarm_firing = false;
		//refresh the display
		refreshDisplay();
	}
	
	public void sendHangUp(View view) {
		Functions.Actions.hangup_call();
	}
	
	public void sendPickUp(View view) {
		Functions.Actions.pickup_call();
	}

	/**
	 * phone widget stuff
	 */

	private boolean setIncomingNumber(String incomingNumber) {
		Log.d("CallActivity", "incomingNumber: " + incomingNumber);

		boolean result = false;
				
		if (incomingNumber == null) 
			return result;
		
		mCallerName.setText(incomingNumber);
		result = setDisplayNameByIncomingNumber(incomingNumber);
		
		return result;
	}
	
	private boolean setDisplayNameByIncomingNumber(String incomingNumber) {
	    String name = null, type = null;
	    Cursor contactLookup = null;
	    
	    try {
	    	contactLookup = getContentResolver().query(
	    			Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(incomingNumber))
		    	,	new String[]{ PhoneLookup.DISPLAY_NAME, PhoneLookup.TYPE }
		    	,	null
		    	,	null
		    	, 	null);
	    	
	        if (contactLookup != null && contactLookup.getCount() > 0) {

		    	contactLookup.moveToFirst();
	            name = contactLookup.getString(contactLookup.getColumnIndex(PhoneLookup.DISPLAY_NAME));
	            type = contactLookup.getString(contactLookup.getColumnIndex(PhoneLookup.TYPE));
	        }

	        if (name != null) {
				mCallerName.setText(name);
				mCallerNumber.setText(incomingNumber);

				Log.d("CallActivity", "displayName: " + name + " (" + type + ")");
	        }
	    } finally {
	        if (contactLookup != null) {
	            contactLookup.close();
	        }
	    }
	    
	    return (name != null);
	}

    @Override
    public boolean onTouchEvent(MotionEvent motionEvent)
    {
        float maxSwipe = 150;
        float swipeTolerance = 0.95f;
        int defaultOffset = 10;

        // point handling
        MotionEvent.PointerCoords pointerCoords = new MotionEvent.PointerCoords();
        final int actionIndex = motionEvent.getActionIndex();
        final int actionMasked = motionEvent.getActionMasked();
        final int pointerId = actionIndex;
        int pointerIndex = -1;
        motionEvent.getPointerCoords(actionIndex, pointerCoords);

        switch (actionMasked) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                if (!TouchEventProcessor.isTracking()) {
                    // check accept button
                    if (mAcceptButton.getVisibility() == View.VISIBLE && TouchEventProcessor.pointerInRect(pointerCoords, mAcceptButton) && !TouchEventProcessor.isTracking())
                        TouchEventProcessor.startTracking(mAcceptButton);

                    // check reject button
                    if (mRejectButton.getVisibility() == View.VISIBLE && TouchEventProcessor.pointerInRect(pointerCoords, mRejectButton) && !TouchEventProcessor.isTracking())
                        TouchEventProcessor.startTracking(mRejectButton);

                    if (TouchEventProcessor.isTracking()) {
                        mActivePointerId = pointerId;
                    }
                }
                break;
            case MotionEvent.ACTION_MOVE:
                for (int idx=0; idx < motionEvent.getPointerCount(); idx++)
                    if (motionEvent.getPointerId(idx) == mActivePointerId) {
                        pointerIndex = idx;
                        break;
                    }

                // process tracking
                if (TouchEventProcessor.isTracking() && pointerIndex != -1) {
                    motionEvent.getPointerCoords(pointerIndex, pointerCoords);

                    float dist = TouchEventProcessor.getHorizontalDistance(pointerCoords.x);

                    // check accept
                    if (TouchEventProcessor.isTrackedObj(mAcceptButton) && dist >= maxSwipe * swipeTolerance) {
                        callAcceptedPhoneWidget();
                        TouchEventProcessor.stopTracking();
                        mActivePointerId = -1;
                    } else
                    // animate accept
                    if (TouchEventProcessor.isTrackedObj(mAcceptButton) && dist > 0 && dist < maxSwipe)
                        moveCallButton(mAcceptButton, defaultOffset + Math.round(dist));

                    // modify negative dist
                    dist = Math.abs(dist);
                    // check rejected
                    if (TouchEventProcessor.isTrackedObj(mRejectButton) && dist >= maxSwipe * swipeTolerance) {
                        callRejectedPhoneWidget();
                        TouchEventProcessor.stopTracking();
                        mActivePointerId = -1;
                    } else
                    // animate rejected
                    if (TouchEventProcessor.isTrackedObj(mRejectButton) && dist > 0 && dist < maxSwipe)
                        moveCallButton(mRejectButton, defaultOffset + Math.round(dist));
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
                if (mActivePointerId == -1 || (mActivePointerId != -1 && motionEvent.findPointerIndex(mActivePointerId) != actionIndex))
                    break;
            case MotionEvent.ACTION_UP:
                if (TouchEventProcessor.isTracking()) {
                        resetPhoneWidget();
                        TouchEventProcessor.stopTracking();
                        mActivePointerId = -1;
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                Log.d("DA-oTE", "CANCEL: never seen");
                callRejectedPhoneWidget();
                TouchEventProcessor.stopTracking();
                mActivePointerId = -1;
                break;
            default:
                break;
        }

        return true;
    }

    public void moveCallButton(View button, int offset)
    {
        if (!mAcceptButton.equals(button) && !mRejectButton.equals(button))
            return;

        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(button.getLayoutParams());

        if (button.equals(mAcceptButton)) {
            // don't draw yet
            if (offset > mButtonMargin && offset <= lp.leftMargin + mRedrawOffset)
                return;

            lp.setMargins(offset, 0, 0, 0);
            lp.addRule(RelativeLayout.ALIGN_PARENT_START);
        }

        if (button.equals(mRejectButton)) {
            // don't draw yet
            if (offset > mButtonMargin && offset <= lp.rightMargin + mRedrawOffset)
                return;

            lp.setMargins(0, 0, offset, 0);
            lp.addRule(RelativeLayout.ALIGN_PARENT_END);
        }

        lp.addRule(RelativeLayout.CENTER_VERTICAL);
        button.setLayoutParams(lp);

        mViewNeedsReset = true;
    }

    public void callAcceptedPhoneWidget() {
        Log.d("DA", "callAcceptedPhoneWidget");
        mAcceptButton.setVisibility(View.INVISIBLE);
        mAcceptSlide.setVisibility(View.INVISIBLE);
        resetPhoneWidget();
        sendPickUp(phoneView);
    }

    public void callRejectedPhoneWidget() {
        Log.d("DA", "callRejectedPhoneWidget");
        resetPhoneWidgetMakeVisible();
        // rearm screen off timer
		Functions.Actions.rearmScreenOffTimer(this);
        sendHangUp(phoneView);
    }

    public void resetPhoneWidget() {
        if (!mViewNeedsReset)
            return;

        Log.d("DA", "resetPhoneWidget");
        moveCallButton(mAcceptButton, mButtonMargin);
        moveCallButton(mRejectButton, mButtonMargin);

        mViewNeedsReset = false;
    }

    public void resetPhoneWidgetMakeVisible() {
        Log.d("DA", "resetPhoneWidgetMakeVisible");
        resetPhoneWidget();
        mAcceptButton.setVisibility(View.VISIBLE);
        mAcceptSlide.setVisibility(View.VISIBLE);
        mRejectButton.setVisibility(View.VISIBLE);
        mRejectSlide.setVisibility(View.VISIBLE);

        // TODO: clear text fields
    }

	/**
	 * phone widget stuff (end)
	 */



	@Override
	protected void onStart() {
		super.onStart();
		Log.d("DA-oS", "starting");
		on_screen = true;

		if (findViewById(R.id.default_battery) != null) {
			Intent battery_status = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
			if (   battery_status.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_CHARGING
					|| battery_status.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_FULL) {
				((ImageView)findViewById(R.id.default_battery)).setImageResource(R.drawable.stat_sys_battery_charge);
			} else {
				((ImageView)findViewById(R.id.default_battery)).setImageResource(R.drawable.stat_sys_battery);
			}
			((ImageView)findViewById(R.id.default_battery)).getDrawable().setLevel((int) (battery_status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) / (float)battery_status.getIntExtra(BatteryManager.EXTRA_SCALE, -1) * 100));
		}

		if (NotificationService.that != null) {
			// notification listener service is running, show the current notifications
			// TODO move this to Functions.java
			Functions.Actions.setup_notifications();
		}
	}

	@Override
	protected void onStop() {
		super.onStop();
		Log.d("DA-oS", "stopping");
		if (Functions.Actions.timerTask != null) {
			Functions.Actions.timerTask.cancel();
		}
		on_screen = false;
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		//tidy up our receiver when we are destroyed
		unregisterReceiver(receiver);
}

	/**
	 * phone widget
	 */
    public static class TouchEventProcessor
    {
        private static View mTrackObj = null;
        private static Point mTrackStartPoint;

        private final static int mHitRectBoost = 50;

        public static boolean pointerInRect(MotionEvent.PointerCoords pointer, View view) {
            return pointerInRect(pointer, view, mHitRectBoost);
        }

        public static boolean pointerInRect(MotionEvent.PointerCoords pointer, View view, int hitRectBoost) {
            Rect rect = new Rect();
            view.getGlobalVisibleRect(rect);

            int extraSnap = (isTrackedObj(view) || !isTracking() ? hitRectBoost : 0);
            return (pointer.x >= rect.left - extraSnap && pointer.x <= rect.right + extraSnap && pointer.y >= rect.top - extraSnap && pointer.y <= rect.bottom + extraSnap);
        }

        public static boolean isTracking() {
            //Log.d("DA.TouchEventProcessor", "TouchEventProcessor.isTracking: " + (mTrackObj != null));
            return (mTrackObj != null);
        }

        public static boolean isTrackedObj(View view) {
            //Log.d("DA.TouchEventProcessor", "TouchEventProcessor.isTrackedObj: " + (isTracking() && mTrackObj.equals(view)));
            return (isTracking() && mTrackObj.equals(view));
        }

        public static void startTracking(View view) {
            //Log.d("DA.TouchEventProcessor", "TouchEventProcessor.startTracking: " + view.getId());
            mTrackObj = view;

            Rect mRect = new Rect();
            view.getGlobalVisibleRect(mRect);

            mTrackStartPoint = new Point(mRect.centerX(), mRect.centerY());
        }

        public static void stopTracking() {
            //Log.d("DA.TouchEventProcessor", "TouchEventProcessor.stopTracking");
            mTrackObj = null;
            mTrackStartPoint = null;
        }

        public static float getHorizontalDistance(float currentX) {
            return currentX - mTrackStartPoint.x;
        }
    }

}
