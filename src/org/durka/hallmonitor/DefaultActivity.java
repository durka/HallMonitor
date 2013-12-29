package org.durka.hallmonitor;

import java.util.Timer;
import java.util.TimerTask;
import java.io.IOException;

import org.durka.hallmonitor.Functions.Util;

import android.app.Activity;
import android.appwidget.AppWidgetHostView;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
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
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.provider.ContactsContract.PhoneLookup;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.Html;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.GridLayout;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextClock;
import android.widget.RelativeLayout;
import android.widget.TextView;

/**
 * This is the activity that is displayed by default - it is displayed for the configurable delay number of milliseconds when the case is closed,
 * it is also displayed when the power button is pressed when the case is already closed
 */
public class DefaultActivity extends Activity {
	
	private final static String LOG_TAG = "DA";
    public final static String INTENT_phoneWidgetShow = "phoneWidgetShow";
    public final static String INTENT_phoneWidgetIncomingNumber = "phoneWidgetIncomingNumber";
    public final static String INTENT_phoneWidgetInitialized = "phoneWidgetInitialized";
    public final static String INTENT_phoneWidgetTtsNotified = "phoneWidgetTtsNotified";

	private HMAppWidgetManager hmAppWidgetManager = Functions.hmAppWidgetManager;

	public static boolean on_screen;

	// states for alarm and phone
	public static boolean alarm_firing = false;
	public static boolean phone_ringing = false;
    private boolean showPhoneWidget = false;

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
    
    //this action will let us toggle the flashlight
    public static final String TOGGLE_FLASHLIGHT = "net.cactii.flash2.TOGGLE_FLASHLIGHT";
    boolean torchIsOn = false;
    
    //all the views we need
    private GridView grid = null;
    private View snoozeButton = null;
    private View dismissButton = null;
    private View defaultWidget = null;
    private RelativeLayout defaultContent = null;
    private TextClock defaultTextClock = null;
    public ImageButton torchButton = null;
    
    protected boolean mWiredHeadSetPlugged = false;

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
    protected MyPhoneStateListener mMyPhoneStateListener = null;

    private boolean mViewNeedsReset = false;

    // drawing stuff
    private final static int mButtonMargin = 15; // designer use just 10dp; ode rendering issue
    private final static int mRedrawOffset = 10; // move min 10dp before redraw

    private int mActivePointerId = -1;
    	
	// we need to kill this activity when the screen opens
	private final BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            
			if (action.equals(Intent.ACTION_SCREEN_ON)) {

				Log.d(LOG_TAG + ".onReceive", "Screen on event received.");

				if (Functions.Is.cover_closed(context)) {
					Log.d(LOG_TAG + ".onReceive", "Cover is closed, display Default Activity.");
					//easiest way to do this is actually just to invoke the close_cover action as it does what we want
					Functions.Actions.close_cover(getApplicationContext());
				} else {
					Log.d(LOG_TAG + ".onReceive", "Cover is open, stopping Default Activity.");

					// when the cover opens, the fullscreen activity goes poof				
					moveTaskToBack(true);
					
					// stop screen off timer
					Functions.Actions.stopScreenOffTimer();
				}

			} else if (action.equals(ALARM_ALERT_ACTION)) {

				Log.d(LOG_TAG + ".onReceive", "Alarm on event received.");

				//only take action if alarm controls are enabled
				if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("pref_alarm_controls", false)) {

					Log.d(LOG_TAG + ".onReceive", "Alarm controls are enabled, taking action.");

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
								Intent myIntent = new Intent(getApplicationContext(),DefaultActivity.class);
								myIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK 
										| Intent.FLAG_ACTIVITY_CLEAR_TOP
										| WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
								myIntent.setAction(Intent.ACTION_MAIN);
								startActivity(myIntent);
							}
						}, 1000);	
					}
				} else {
					Log.d(LOG_TAG + ".onReceive", "Alarm controls are not enabled.");
				}

            } else if (intent.getAction().equals(ALARM_DONE_ACTION) ) {

                Log.d(LOG_TAG + ".onReceive", "Alarm done event received.");

                //if the alarm is turned off using the normal alarm screen this will
                //ensure that we will hide the alarm controls
                alarm_firing=false;
			
			} else if (action.equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED)) {
				String phoneExtraState = intent.getStringExtra(TelephonyManager.EXTRA_STATE);

				Log.d(LOG_TAG + ".onReceive", "ACTION_PHONE_STATE_CHANGED = " + phoneExtraState);
				
				if (phoneExtraState.equals(TelephonyManager.EXTRA_STATE_RINGING)) {
					Functions.Events.incoming_call(context, intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER));
                } else if (phoneExtraState.equals(TelephonyManager.EXTRA_STATE_OFFHOOK)) {
                    callAcceptedPhoneWidget(false);
                    phone_ringing = false;
                } else if (phoneExtraState.equals(TelephonyManager.EXTRA_STATE_IDLE)) {
                    //if (phone_ringing && phoneExtraState.equals(TelephonyManager.EXTRA_STATE_IDLE)) {
                        setShowPhoneWidget(false);
                        phone_ringing = false;
                        refreshDisplay();

                        // rearm screen off timer
                        Functions.Actions.rearmScreenOffTimer(context);
                    //}
                }
			} else if (action.equals("org.durka.hallmonitor.debug")) {
				Log.d(LOG_TAG + ".onReceive", "received debug intent");
				// test intent to show/hide a notification
				switch (intent.getIntExtra("notif", 0)) {
				case 1:
					Functions.Actions.debug_notification(context, true);
					break;
				case 2:
					Functions.Actions.debug_notification(context, false);
					break;
				}
			} else if (action.equals(Intent.ACTION_HEADSET_PLUG)) {	// headset (un-)plugged
                mWiredHeadSetPlugged = ((intent.getIntExtra("state", -1) == 1));
                Log.d(LOG_TAG + ".onReceive", "ACTION_HEADSET_PLUG: plugged " + mWiredHeadSetPlugged + " (" + intent.getIntExtra("state", -1) + ")");
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

		Log.d(LOG_TAG + ".onCreate", "onCreate of DefaultView.");

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
        filter.addAction(ALARM_DONE_ACTION);
		filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
		filter.addAction("org.durka.hallmonitor.debug");
        filter.addAction(Intent.ACTION_HEADSET_PLUG);
		registerReceiver(receiver, filter);
		
		//get the views we need
		grid = (GridView)findViewById(R.id.default_icon_container);
	    snoozeButton = findViewById(R.id.snoozebutton);
	    dismissButton = findViewById(R.id.dismissbutton);
	    defaultWidget = findViewById(R.id.default_widget);
	    defaultContent = (RelativeLayout) findViewById(R.id.default_content);
	    defaultTextClock = (TextClock) findViewById(R.id.default_text_clock);
	    //torchButton = (ImageButton) findViewById(R.id.torchbutton);

	    // phone_widget
	    phoneView = (GridLayout)findViewById(R.id.phone_widget);
        mCallerName = (TextView)findViewById(R.id.caller_name);
        mCallerNumber = (TextView)findViewById(R.id.caller_number);
        mAcceptButton = findViewById(R.id.call_accept_button);
        mAcceptSlide = findViewById(R.id.call_accept_slide);
        mRejectButton = findViewById(R.id.call_reject_button);
        mRejectSlide = findViewById(R.id.call_reject_slide);

        // TextClock format
        SpannableString spanString = new SpannableString(getResources().getString(R.string.styled_24_hour_clock));
        spanString.setSpan(new RelativeSizeSpan(2.8f), 1, 5, 0);
        spanString.setSpan(new StyleSpan(Typeface.BOLD), 1, 5, 0);
        spanString.setSpan(new RelativeSizeSpan(0.8f), 6, spanString.length(), 0);
        defaultTextClock.setFormat24Hour(spanString);

        // showPhoneWidget (overtake control if phone state in ringing or offhook)
        initPhoneWidget();
    }

	@Override
	protected void onStart() {
		super.onStart();
		Log.d(LOG_TAG + "-oS", "starting");
		on_screen = true;

        updateBatteryStatus();

		if (NotificationService.that != null) {
			// notification listener service is running, show the current notifications
			Functions.Actions.setup_notifications();
		}
	}

    @Override
    protected void onPause() {
        super.onPause();

        Log.d(LOG_TAG + ".onPause", "");
    }

	@Override
	protected void onResume() {
		super.onResume();

		Log.d(LOG_TAG + ".onResume", "On resume called.");

        // phoneWidget
        initPhoneWidget();

		refreshDisplay();
	}

	@Override
	protected void onStop() {
		super.onStop();
		Log.d(LOG_TAG + "-oS", "stopping");
		Functions.Actions.stopScreenOffTimer();
		on_screen = false;
	}

	@Override
	protected void onDestroy() {
        //tidy up our receiver when we are destroyed
        unregisterReceiver(receiver);

        if (mMyPhoneStateListener != null) {
            TelephonyManager telephonyManager = (TelephonyManager)getSystemService(TELEPHONY_SERVICE);
            telephonyManager.listen(mMyPhoneStateListener, PhoneStateListener.LISTEN_NONE);
        }

        super.onDestroy();
	}

    @Override
    public boolean onTouchEvent(MotionEvent motionEvent)
    {
        boolean result = true;

        // event handling phoneWidget
        if (phoneView.getVisibility() == View.VISIBLE)
            result = onTouchEvent_PhoneWidgetHandler(motionEvent);

        return result;
    }

    /**
	 * Refresh the display taking account of device and application state
	 */
    public void refreshDisplay() {

        //get the layout for the windowed view
        RelativeLayout contentView = (RelativeLayout)findViewById(R.id.default_widget);

        //hide or show the torch button as required
        if (torchButton != null) {
            boolean prefFlash = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_flash_controls", false);
            torchButton.setVisibility((prefFlash ? View.VISIBLE : View.INVISIBLE));
        }

        //if the alarm is firing then show the alarm controls, otherwise
        //if we have a media app widget and media is playing or headphones are connected then display that, otherwise
        //if we have a default app widget to use then display that, if not then display our default clock screen
        //(which is part of the default layout so will show anyway)
        //will do this simply by setting the widgetType
        String widgetType = "default";
        if (hmAppWidgetManager.doesWidgetExist("media") && (mWiredHeadSetPlugged || audioManager.isMusicActive())) {
            widgetType = "media";
        }
	    
	    if (alarm_firing) {
	    	Log.d(LOG_TAG, "refreshDisplay: alarm_firing");
	    	//show the alarm controls
	    	defaultContent.setVisibility(View.VISIBLE);
	    	phoneView.setVisibility(View.INVISIBLE);
	    	
	    	snoozeButton.setVisibility(View.VISIBLE);
	    	dismissButton.setVisibility(View.VISIBLE);
	    	defaultWidget.setVisibility(View.INVISIBLE);
	    	grid.setVisibility(View.INVISIBLE);
	    } else if (phone_ringing || showPhoneWidget) {
	    	Log.d(LOG_TAG, "refreshDisplay: phone_ringing");

            // wake up screen
            Functions.Actions.wakeUpScreen(getBaseContext());
            // stop screen off timer
            Functions.Actions.stopScreenOffTimer();

            //show the phone controls
    		defaultContent.setVisibility(View.INVISIBLE);
	    	phoneView.setVisibility(View.VISIBLE);
	    	
	    	snoozeButton.setVisibility(View.INVISIBLE);
	    	dismissButton.setVisibility(View.INVISIBLE);
	    	defaultWidget.setVisibility(View.INVISIBLE);
	    	grid.setVisibility(View.INVISIBLE);
	    	
			// reset to defaults
            boolean isPhoneWidgetInitialized = getIntent().getBooleanExtra(INTENT_phoneWidgetInitialized, false);
            if (!isPhoneWidgetInitialized) {
			    resetPhoneWidgetMakeVisible();
                // parse parameter
                setIncomingNumber(getIntent().getStringExtra(INTENT_phoneWidgetIncomingNumber));
                getIntent().putExtra(INTENT_phoneWidgetInitialized, true);
            }
        } else {
	    	//default view    	
	    	defaultContent.setVisibility(View.VISIBLE);
	    	phoneView.setVisibility(View.INVISIBLE);

	    	//add the required widget based on the widgetType
		    if (hmAppWidgetManager.doesWidgetExist(widgetType)) {
		    	Log.d(LOG_TAG, "refreshDisplay: default_widget");
		    	
		    	//remove the TextClock from the contentview
			    contentView.removeAllViews();
		    	
		    	//get the widget
			    AppWidgetHostView hostView = hmAppWidgetManager.getAppWidgetHostViewByType(widgetType);
			    
			    //if the widget host view already has a parent then we need to detach it
			    ViewGroup parent = (ViewGroup)hostView.getParent();
			    if ( parent != null) {
			    	Log.d(LOG_TAG + ".onCreate", "hostView had already been added to a group, detaching it.");
			       	parent.removeView(hostView);
			    }    
			    
			    //add the widget to the view
			    contentView.addView(hostView);
		    } else {
		    	Log.d(LOG_TAG, "refreshDisplay: default_widget");

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

    //toggle the torch
    public void sendToggleTorch(View view) {
        Functions.Actions.toggle_torch(this);
    }

    private void updateBatteryStatus() {
        if (findViewById(R.id.default_battery_picture) != null) {
            Intent battery_status = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (   battery_status.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_CHARGING
                    || battery_status.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_FULL) {
                ((ImageView)findViewById(R.id.default_battery_picture)).setImageResource(R.drawable.stat_sys_battery_charge);
            } else {
                ((ImageView)findViewById(R.id.default_battery_picture)).setImageResource(R.drawable.stat_sys_battery);
            }
            ((ImageView)findViewById(R.id.default_battery_picture)).getDrawable().setLevel((int) (battery_status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) / (float)battery_status.getIntExtra(BatteryManager.EXTRA_SCALE, -1) * 100));
        }
    }

    /**
     * Text-To-Speech
     */
	
    private void sendTextToSpeech(String text) {
        Intent intent = new Intent();
        intent.setAction(getString(R.string.ACTION_SEND_TO_SPEECH_RECEIVE));
        intent.putExtra("sendTextToSpeech", text);
        sendBroadcast(intent);
    }

    private void stopTextToSpeech() {
        Intent intent = new Intent();
        intent.setAction(getString(R.string.ACTION_STOP_TO_SPEECH_RECEIVE));
        sendBroadcast(intent);
    }

    /**
     * Text-To-Speech (end)
     */
	
	/**
	 * phone widget stuff
	 */

    private void initPhoneWidget() {
        final TelephonyManager telephonyManager = (TelephonyManager)getBaseContext().getSystemService(TELEPHONY_SERVICE);

        if (mMyPhoneStateListener == null) {
            mMyPhoneStateListener = new MyPhoneStateListener();
            telephonyManager.listen(mMyPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE | PhoneStateListener.LISTEN_CALL_FORWARDING_INDICATOR);
        }
        // check phone state (if not invoked by intent)
        if (!showPhoneWidget) {
            if (telephonyManager.getCallState() == TelephonyManager.CALL_STATE_RINGING || telephonyManager.getCallState() == TelephonyManager.CALL_STATE_OFFHOOK) {
                final String incomingNumber = "";
                //telephonyManager.notify();

                Log.d(LOG_TAG, "initPhoneWidget: callState = " + telephonyManager.getCallState() + " number = " + incomingNumber);
                getIntent().putExtra(INTENT_phoneWidgetShow, true);
                getIntent().putExtra(INTENT_phoneWidgetIncomingNumber, incomingNumber);
                getIntent().putExtra(INTENT_phoneWidgetTtsNotified, true);
                resetPhoneWidgetMakeVisible();
                setIncomingNumber(incomingNumber);

                if (telephonyManager.getCallState() == TelephonyManager.CALL_STATE_OFFHOOK) {
                    getIntent().putExtra(INTENT_phoneWidgetInitialized, true);
                    callAcceptedPhoneWidget(false);
                }
            }
        }

        setShowPhoneWidget(getIntent().getBooleanExtra(INTENT_phoneWidgetShow, false));
        if (showPhoneWidget) {
            Log.d(LOG_TAG, INTENT_phoneWidgetIncomingNumber + " = '" + getIntent().getStringExtra(INTENT_phoneWidgetIncomingNumber) + "'");
            Log.d(LOG_TAG, INTENT_phoneWidgetInitialized + " = '" + getIntent().getBooleanExtra(INTENT_phoneWidgetInitialized, false) + "'");
            Log.d(LOG_TAG, INTENT_phoneWidgetTtsNotified + " = '" + getIntent().getBooleanExtra(INTENT_phoneWidgetTtsNotified, false) + "'");
        }
    }

	private boolean setIncomingNumber(String incomingNumber) {
		Log.d(LOG_TAG, "incomingNumber: " + incomingNumber);

		boolean result = false;
				
		if (incomingNumber == null || incomingNumber.equals(""))
			return result;
		
		mCallerName.setText(incomingNumber);
		result = setDisplayNameByIncomingNumber(incomingNumber);
		
		return result;
	}
	
	private boolean setDisplayNameByIncomingNumber(String incomingNumber) {
        String name = null, type = null, label = null;
	    Cursor contactLookup = null;

	    try {
	    	contactLookup = getContentResolver().query(
	    			Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(incomingNumber))
		    	,	new String[]{ PhoneLookup.DISPLAY_NAME, PhoneLookup.TYPE, PhoneLookup.LABEL }
		    	,	null
		    	,	null
		    	, 	null);
	    	
	        if (contactLookup != null && contactLookup.getCount() > 0) {

		    	contactLookup.moveToFirst();
                name = contactLookup.getString(contactLookup.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME));
                type = contactLookup.getString(contactLookup.getColumnIndex(ContactsContract.PhoneLookup.TYPE));
                label = contactLookup.getString(contactLookup.getColumnIndex(ContactsContract.PhoneLookup.LABEL));
	        }

            if (name != null) {
                String typeString = (String)ContactsContract.CommonDataKinds.Phone.getTypeLabel(this.getResources(), Integer.parseInt(type), "");

                mCallerName.setText(name);
                mCallerNumber.setText((typeString == null ? incomingNumber : typeString));

                Log.d(LOG_TAG, "displayName: " + name + " aka " + label + " (" + type + " -> " + typeString + ")");

                boolean isTtsNotified = getIntent().getBooleanExtra(INTENT_phoneWidgetTtsNotified, false);

                if (!isTtsNotified) {
                    sendTextToSpeech(name + (typeString != null ? " " + typeString : ""));
                    getIntent().putExtra(INTENT_phoneWidgetTtsNotified, true);
                }
            }
	    } finally {
	        if (contactLookup != null) {
	            contactLookup.close();
	        }
	    }
	    
	    return (name != null);
	}

    private boolean onTouchEvent_PhoneWidgetHandler(MotionEvent motionEvent) {
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
                Log.d(LOG_TAG + "-oTE", "CANCEL: never seen");
                callRejectedPhoneWidget();
                TouchEventProcessor.stopTracking();
                mActivePointerId = -1;
                break;
            default:
                break;
        }

        return true;
    }

    private void moveCallButton(View button, int offset) {
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

    private void setShowPhoneWidget(boolean show) {
        Log.d(LOG_TAG, "setShowPhoneWidget: " + show);
        showPhoneWidget = show;

        if (showPhoneWidget) {
            getIntent().putExtra(INTENT_phoneWidgetShow, show);
        } else { // clean up extra info's from intent
            getIntent().removeExtra(INTENT_phoneWidgetShow);
            getIntent().removeExtra(INTENT_phoneWidgetIncomingNumber);
            getIntent().removeExtra(INTENT_phoneWidgetInitialized);
            getIntent().removeExtra(INTENT_phoneWidgetTtsNotified);
        }
    }

    private void callAcceptedPhoneWidget() {
        callAcceptedPhoneWidget(true);
    }

    private void callAcceptedPhoneWidget(boolean needsSendPickup) {
        Log.d(LOG_TAG, "callAcceptedPhoneWidget");
        stopTextToSpeech();
        mAcceptButton.setVisibility(View.INVISIBLE);
        mAcceptSlide.setVisibility(View.INVISIBLE);
        resetPhoneWidget();
        if (needsSendPickup)
            sendPickUp(phoneView);
    }

    private void callRejectedPhoneWidget() {
        callRejectedPhoneWidget(true);
    }

    private void callRejectedPhoneWidget(boolean needsSendHangup) {
        Log.d(LOG_TAG, "callRejectedPhoneWidget");
        setShowPhoneWidget(false);

        stopTextToSpeech();
        resetPhoneWidgetMakeVisible();
        // rearm screen off timer
		Functions.Actions.rearmScreenOffTimer(this);
        if (needsSendHangup)
            sendHangUp(phoneView);
    }

    private void resetPhoneWidget() {
        if (!mViewNeedsReset)
            return;

        Log.d(LOG_TAG, "resetPhoneWidget");
        moveCallButton(mAcceptButton, mButtonMargin);
        moveCallButton(mRejectButton, mButtonMargin);

        mViewNeedsReset = false;
    }

    private void resetPhoneWidgetMakeVisible() {
        Log.d(LOG_TAG, "resetPhoneWidgetMakeVisible");
        resetPhoneWidget();
        mAcceptButton.setVisibility(View.VISIBLE);
        mAcceptSlide.setVisibility(View.VISIBLE);
        mRejectButton.setVisibility(View.VISIBLE);
        mRejectSlide.setVisibility(View.VISIBLE);

        mCallerName.setText("");
        mCallerNumber.setText("");
    }

    private class  MyPhoneStateListener extends PhoneStateListener {
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
        switch (state) {
            case TelephonyManager.CALL_STATE_IDLE:
                break;
            case TelephonyManager.CALL_STATE_OFFHOOK:
                break;
            case TelephonyManager.CALL_STATE_RINGING:
                final boolean showPhoneWidget = getIntent().getBooleanExtra(DefaultActivity.INTENT_phoneWidgetShow, false);
                final String intentIncomingNumber = getIntent().getStringExtra(DefaultActivity.INTENT_phoneWidgetIncomingNumber);

                if (showPhoneWidget && (intentIncomingNumber == null || intentIncomingNumber.equals(""))) {
                    getIntent().putExtra(DefaultActivity.INTENT_phoneWidgetIncomingNumber, incomingNumber);
                    setIncomingNumber(incomingNumber);
                }
                break;
        }
    }
}

    private static class TouchEventProcessor {
        private static View mTrackObj = null;
        private static Point mTrackStartPoint;

        private final static int mHitRectBoost = 20;

        public static boolean pointerInRect(MotionEvent.PointerCoords pointer, View view) {
            return pointerInRect(pointer, view, mHitRectBoost);
        }

        public static boolean pointerInRect(MotionEvent.PointerCoords pointer, View view, int hitRectBoost) {
            Rect rect = new Rect();
            view.getGlobalVisibleRect(rect);
            // circle is tangent to edges
            double radius = rect.centerX() - rect.left;

            int extraSnap = (isTrackedObj(view) || !isTracking() ? hitRectBoost : 0);
            //return (pointer.x >= rect.left - extraSnap && pointer.x <= rect.right + extraSnap && pointer.y >= rect.top - extraSnap && pointer.y <= rect.bottom + extraSnap);
            return (Math.sqrt(Math.pow(rect.centerX() - pointer.x, 2) + Math.pow(rect.centerY() - pointer.y, 2)) <= radius + extraSnap);
        }

        public static boolean isTracking() {
            //Log.d(LOG_TAG + ".TouchEventProcessor", "isTracking: " + (mTrackObj != null));
            return (mTrackObj != null);
        }

        public static boolean isTrackedObj(View view) {
            //Log.d(LOG_TAG + ".TouchEventProcessor", "isTrackedObj: " + (isTracking() && mTrackObj.equals(view)));
            return (isTracking() && mTrackObj.equals(view));
        }

        public static void startTracking(View view) {
            //Log.d(LOG_TAG + ".TouchEventProcessor", "startTracking: " + view.getId());
            mTrackObj = view;

            Rect mRect = new Rect();
            view.getGlobalVisibleRect(mRect);

            mTrackStartPoint = new Point(mRect.centerX(), mRect.centerY());
        }

        public static void stopTracking() {
            //Log.d(LOG_TAG + ".TouchEventProcessor", "stopTracking");
            mTrackObj = null;
            mTrackStartPoint = null;
        }

        public static float getHorizontalDistance(float currentX) {
            return currentX - mTrackStartPoint.x;
        }
    }

    /**
	 * phone widget stuff (end)
	 */
    
}
