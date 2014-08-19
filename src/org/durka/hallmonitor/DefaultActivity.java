package org.durka.hallmonitor;

import java.text.DateFormat;
import java.util.Date;

import org.durka.hallmonitor.Functions.TorchActions;

import android.app.Activity;
import android.appwidget.AppWidgetHostView;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.BatteryManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextClock;
import android.widget.RelativeLayout;
import android.widget.TextView;

import io.github.homelocker.lib.HomeKeyLocker;

/**
 * This is the activity that is displayed by default - it is displayed for the configurable delay number of milliseconds when the case is closed,
 * it is also displayed when the power button is pressed when the case is already closed
 */
public class DefaultActivity extends Activity {
	private HMAppWidgetManager hmAppWidgetManager = Functions.hmAppWidgetManager;

    private static boolean mDebug = false;

    public static boolean on_screen;

	// states for alarm and phone
	public static boolean alarm_firing = false;
	public static boolean phone_ringing = false;
	public static boolean camera_up = false;
	public static String call_from = "";

	//audio manager to detect media state
	private AudioManager audioManager;

    //manager for home key hack
    private HomeKeyLocker homeKeyLocker;

	//Action fired when alarm goes off
    public static final String ALARM_ALERT_ACTION = "com.android.deskclock.ALARM_ALERT";
    //Action to trigger snooze of the alarm
    public static final String ALARM_SNOOZE_ACTION = "com.android.deskclock.ALARM_SNOOZE";
    //Action to trigger dismiss of the alarm
    public static final String ALARM_DISMISS_ACTION = "com.android.deskclock.ALARM_DISMISS";
    //This action should let us know if the alarm has been killed by another app
    public static final String ALARM_DONE_ACTION = "com.android.deskclock.ALARM_DONE";

    public static final String TORCH_STATE = "torch_state";
    public static final String TOGGLE_FLASHLIGHT = "net.cactii.flash2.TOGGLE_FLASHLIGHT";
    public static final String TORCH_STATE_CHANGED = "net.cactii.flash2.TORCH_STATE_CHANGED";
    
    //all the views we need
    public ImageButton torchButton = null;
    private ImageButton cameraButton = null;

	//we need to kill this activity when the screen opens
	private final BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {

				Log.d("DA.onReceive.screen", "Screen on event received.");

				if (Functions.Is.cover_closed(context)) {
					Log.d("DA.onReceive.screen", "Cover is closed, display Default Activity.");
					//easiest way to do this is actually just to invoke the close_cover action as it does what we want
					Functions.Actions.close_cover(getApplicationContext());
				} else {
					Log.d("DA.onReceive.screen", "Cover is open, stopping Default Activity.");

					// when the cover opens, the fullscreen activity goes poof				
					//Log.d("DA.onReceive", "Current task: " + ((ActivityManager)getSystemService(ACTIVITY_SERVICE)).getRunningTasks(1).get(0).topActivity.getPackageName());
					//moveTaskToBack(true);
					finish();
				}

			} else if (intent.getAction().equals(ALARM_ALERT_ACTION)) {

				Log.d("DA.onReceive.alarm", "Alarm on event received.");

				//only take action if alarm controls are enabled
				if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("pref_alarm_controls", false)) {

					Log.d("DA.onReceive.alarm", "Alarm controls are enabled, taking action.");

					Functions.Actions.choose_alarm_layout(context);
					Functions.Events.incoming_alarm(context);
					refreshDisplay();
				} else {
					Log.d("DA.onReceive.alarm", "Alarm controls are not enabled.");
				}
			} else if (intent.getAction().equals(ALARM_DONE_ACTION) ) {
					
					Log.d("DA.onReceive.alarm", "Alarm done event received.");
					
					Functions.Events.alarm_finished(context);
					refreshDisplay();
			} else if (intent.getAction().equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED)) {
				
				if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("pref_phone_controls", false)) {
					String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
					Log.d("DA.onReceive.phone", "phone state changed to " + state);
					if (state.equals(TelephonyManager.EXTRA_STATE_RINGING)) {
						Functions.Actions.choose_call_layout(context);
						Functions.Events.incoming_call(context, intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER));
					} else {
						if (state.equals(TelephonyManager.EXTRA_STATE_IDLE)) {
							Functions.Events.call_finished(context);
						}
					}
					refreshDisplay();
				} else {
					Log.d("DA.onReceive.phone", "phone controls are not enabled");
				}
	        } else if (intent.getAction().equals(TORCH_STATE_CHANGED) || intent.getAction().equals(TOGGLE_FLASHLIGHT)) {
	        	if(PreferenceManager.getDefaultSharedPreferences(context).getBoolean("pref_flash_controls", false)) {
					Log.d("DA.onReceive.torch", "torch state changed");
	        		refreshDisplay();
	        	}
	        	else {
					Log.d("DA.onReceive.torch", "torch controls are not enabled.");
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


	/**
	 * Refresh the display taking account of device and application state
	 */
	public void refreshDisplay() {
		
		set_real_fullscreen();
		
		if (findViewById(R.id.default_battery_picture_horizontal) != null) {
			Intent battery_status = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
			int level = (int) (battery_status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) / (float)battery_status.getIntExtra(BatteryManager.EXTRA_SCALE, -1) * 100),
				status = battery_status.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
			if (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL) {
				((ImageView)findViewById(R.id.default_battery_picture_horizontal)).setImageResource(R.drawable.stat_sys_battery_charge_horizontal);
				((TextView)findViewById(R.id.default_battery_percent)).setText(Integer.toString(level));
			} else {
				((ImageView)findViewById(R.id.default_battery_picture_horizontal)).setImageResource(R.drawable.stat_sys_battery_horizontal);
				((TextView)findViewById(R.id.default_battery_percent)).setText(Integer.toString(level) + "%");
			}
			((ImageView)findViewById(R.id.default_battery_picture_horizontal)).getDrawable().setLevel(level);
		}

		// we might have missed a phone-state revelation
		phone_ringing = ((TelephonyManager)getSystemService(TELEPHONY_SERVICE)).getCallState() == TelephonyManager.CALL_STATE_RINGING;

		//set the colours based on the picker values
		Drawable rounded = getResources().getDrawable(R.drawable.rounded);
		rounded.setColorFilter(new PorterDuffColorFilter(PreferenceManager.getDefaultSharedPreferences(this).getInt("pref_default_bgcolor", 0xFF000000), PorterDuff.Mode.MULTIPLY));
		((RelativeLayout)findViewById(R.id.default_content)).setBackground(rounded);
		((TextClock)findViewById(R.id.default_text_clock)).setTextColor(PreferenceManager.getDefaultSharedPreferences(this).getInt("pref_default_fgcolor", 0xFFFFFFFF));
		((TextClock)findViewById(R.id.default_text_clock_hour)).setTextColor(PreferenceManager.getDefaultSharedPreferences(this).getInt("pref_default_fgcolor", 0xFFFFFFFF));
		((TextView)findViewById(R.id.default_text_clock_date)).setTextColor(PreferenceManager.getDefaultSharedPreferences(this).getInt("pref_default_fgcolor", 0xFFFFFFFF));
		((TextView)findViewById(R.id.default_text_clock_date)).setText(DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date()));
		
		//hide or show the torch button as required
		if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_flash_controls", false)
				|| PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_flash_controls_alternative", false))
		{
			Functions.Actions.choose_torch_layout(this);
			if(PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_flash_controls", false)) {
				toggleTorchIcon();				
			}
			torchButton.setVisibility(View.VISIBLE);
		} else {
			torchButton.setVisibility(View.INVISIBLE);
		}
		
		//hide or show the camera button as required
		if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_camera_controls", false))
		{
			cameraButton.setVisibility(View.VISIBLE);
		} else {
			cameraButton.setVisibility(View.INVISIBLE);
		}
		
		//if the alarm is firing then show the alarm controls, otherwise
		//if we have a media app widget and media is playing or headphones are connected then display that, otherwise
		//if we have a default app widget to use then display that, if not then display our default clock screen
		//(which is part of the default layout so will show anyway)
		//will do this simply by setting the widgetType
		String widgetType = "default";
		if (hmAppWidgetManager.doesWidgetExist("media") && (audioManager.isWiredHeadsetOn() || audioManager.isMusicActive())) {
			widgetType = "media";
		}
		// reset to showing the clock, but in a second we might hide it and attach a widget
		if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_datetime", false))
		{
			findViewById(R.id.default_text_clock).setVisibility(View.INVISIBLE);
			findViewById(R.id.default_text_clock_hour).setVisibility(View.VISIBLE);
			findViewById(R.id.default_text_clock_date).setVisibility(View.VISIBLE);
		} else {
			findViewById(R.id.default_text_clock).setVisibility(View.VISIBLE);
			findViewById(R.id.default_text_clock_hour).setVisibility(View.INVISIBLE);
			findViewById(R.id.default_text_clock_date).setVisibility(View.INVISIBLE);
		}
		
		((RelativeLayout)findViewById(R.id.default_widget_area)).removeAllViews();

		if (alarm_firing) {
			Functions.Actions.choose_alarm_layout(this);
			// show the alarm controls
			findViewById(R.id.default_content_alarm).setVisibility(View.VISIBLE);
			findViewById(R.id.default_content_phone).setVisibility(View.INVISIBLE);
			findViewById(R.id.default_content_normal).setVisibility(View.INVISIBLE);
			findViewById(R.id.default_content_camera).setVisibility(View.INVISIBLE);
			
		} else if (phone_ringing) {
			Functions.Actions.choose_call_layout(this);

			// show the phone controls
			findViewById(R.id.default_content_alarm).setVisibility(View.INVISIBLE);
			findViewById(R.id.default_content_phone).setVisibility(View.VISIBLE);
			findViewById(R.id.default_content_normal).setVisibility(View.INVISIBLE);
			findViewById(R.id.default_content_camera).setVisibility(View.INVISIBLE);
			
			((TextView)findViewById(R.id.call_from)).setText(Functions.Util.getContactName(this, call_from));
			
		} else if (camera_up) {
			findViewById(R.id.default_content_alarm).setVisibility(View.INVISIBLE);
			findViewById(R.id.default_content_phone).setVisibility(View.INVISIBLE);
			findViewById(R.id.default_content_normal).setVisibility(View.INVISIBLE);
			findViewById(R.id.default_content_camera).setVisibility(View.VISIBLE);

		} else {
			//normal view
			findViewById(R.id.default_content_alarm).setVisibility(View.INVISIBLE);
			findViewById(R.id.default_content_phone).setVisibility(View.INVISIBLE);
			findViewById(R.id.default_content_normal).setVisibility(View.VISIBLE);
			findViewById(R.id.default_content_camera).setVisibility(View.INVISIBLE);

			//add the required widget based on the widgetType
			if (hmAppWidgetManager.doesWidgetExist(widgetType)) {

				//get the widget
				AppWidgetHostView hostView = hmAppWidgetManager.getAppWidgetHostViewByType(widgetType);

				//if the widget host view already has a parent then we need to detach it
				ViewGroup parent = (ViewGroup)hostView.getParent();
				if ( parent != null) {
					Log.d("DA.onCreate", "hostView had already been added to a group, detaching it.");
					parent.removeView(hostView);
				}    

				//add the widget to the view
				findViewById(R.id.default_text_clock).setVisibility(View.INVISIBLE);
				findViewById(R.id.default_text_clock_hour).setVisibility(View.INVISIBLE);
				findViewById(R.id.default_text_clock_date).setVisibility(View.INVISIBLE);
				((RelativeLayout)findViewById(R.id.default_widget_area)).addView(hostView);
			}
		}
	}

	/** Called when the user touches the snooze button */
	public void sendSnooze(View view) {
		Functions.Actions.snooze_alarm();
	}
	
	/** Called when the user touches the dismiss button */
	public void sendDismiss(View view) {
		Functions.Actions.dismiss_alarm();
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
	
	public void toggleTorchIcon()
	{
		Intent stateIntent = Functions.defaultActivity.registerReceiver(null, new IntentFilter(DefaultActivity.TORCH_STATE_CHANGED));
        boolean torchIsOn = stateIntent != null && stateIntent.getIntExtra("state", 0) != 0;
        
		if (torchIsOn) {
    		Log.d("torch", "Icon On");
        	((ImageButton)Functions.defaultActivity.findViewById(R.id.torchbutton)).setImageResource(R.drawable.ic_appwidget_torch_on);
        	if (Functions.Actions.timerTask != null) Functions.Actions.timerTask.cancel();
        } else {
    		Log.d("torch", "Icon Off");
        	((ImageButton)Functions.defaultActivity.findViewById(R.id.torchbutton)).setImageResource(R.drawable.ic_appwidget_torch_off);
        	if(Functions.Is.cover_closed(this))
        	{
        		Functions.Actions.setCloseTimer(this);
        	}
        }
	}
		
	//fire up the camera
	public void camera_start(View view) {
		if (Functions.flashIsOn) {
		 	TorchActions.turnOffFlash();
		 	torchButton.setImageResource(R.drawable.ic_appwidget_torch_off);
		}
		Functions.Actions.start_camera(this);
	}
	
	public void camera_capture(View view) {
		Log.d("hm-cam", "say cheese");
		((CameraPreview)findViewById(R.id.default_camera)).capture();
	}
	
	public void camera_back(View view) {
		Functions.Actions.end_camera(this);
	}
	
	public void set_real_fullscreen () {
		if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_realfullscreen", false) && PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_runasroot", false)) {
			//Remove notification bar
			this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
			//Remove navigation bar
	 		View decorView = this.getWindow().getDecorView();
	 		decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
		            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
		            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
		            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
		            | View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
		            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
		}
 	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		//pass a reference back to the Functions class so it can finish us when it wants to
		//FIXME Presumably there is a better way to do this
		Functions.defaultActivity = this;

		Log.d("DA.onCreate", "onCreate of DefaultView.");

		//Remove title bar
		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		
		set_real_fullscreen();

		//set default view
		Functions.Actions.choose_layout(this);

		//get the audio manager
		audioManager = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);

	    //add screen on and alarm fired intent receiver
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
		IntentFilter filter = new IntentFilter();
		filter.addAction(Intent.ACTION_SCREEN_ON);
		filter.addAction(ALARM_ALERT_ACTION);
		filter.addAction(ALARM_DONE_ACTION);
		filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
		filter.addAction(TORCH_STATE_CHANGED);
		filter.addAction(TOGGLE_FLASHLIGHT);
		filter.addAction("org.durka.hallmonitor.debug");
		registerReceiver(receiver, filter);
		
		//get the views we need
	    torchButton = (ImageButton) findViewById(R.id.torchbutton);
	    cameraButton = (ImageButton) findViewById(R.id.camerabutton);

        //home key hack
        homeKeyLocker = new HomeKeyLocker();
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);

		if(hasFocus) {
			Functions.Actions.enableCoverTouch(getBaseContext(), true);
			refreshDisplay();
		}

	}

	@Override
	protected void onStart() {
	    super.onStart();
	    Log.d("DA-oS", "starting");
	    on_screen = true;

		if (NotificationService.that != null) {
			// notification listener service is running, show the current notifications
			// TODO move this to Functions.java
			Functions.Actions.setup_notifications();
		}
	}

	
	@Override
	protected void onPause() {
		super.onPause();
		on_screen = false;
        homeKeyLocker.unlock();
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		on_screen = true;

        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_disable_home", true)) {
            homeKeyLocker.lock(this);
        }
		
        // load debug setting
        mDebug = PreferenceManager.getDefaultSharedPreferences(getBaseContext()).getBoolean("pref_dev_opts_debug", false);

		Log.d("DA.onResume", "On resume called.");

		refreshDisplay(); // TODO is this necessary to do here?`

        /*
        // check preview (extras are configured in xml)
        if (getIntent().getExtras() != null && !getIntent().getExtras().getString("preview", "").equals("")) {
            String preview = getIntent().getExtras().getString("preview");

            if (preview.equals("phoneWidget")) {
                new AlertDialog.Builder(this)
                        .setMessage("search AlertDialog in DefaultActivity to place your code for preview of '" + preview + "' there!")
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                finish();
                            }
                        })
                        .show();
            }
        }
        */
	}

	@Override
	protected void onStop() {
		super.onStop();
		Log.d("DA-oS", "stopping");
		Functions.Actions.dismiss_keyguard(this);
		if (Functions.Actions.timerTask != null) {
			Functions.Actions.timerTask.cancel();
		}
		if (Functions.flashIsOn) {
				TorchActions.turnOffFlash();
		}
		if (camera_up) {
			Functions.Actions.end_camera(this, false);
		}
		on_screen = false;
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		//tidy up our receiver when we are destroyed
		unregisterReceiver(receiver);
	}
	
    public static boolean isDebug() {
        return mDebug;
    }

}
