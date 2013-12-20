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
import android.media.AudioManager;
import android.net.Uri;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources.NotFoundException;
import android.database.Cursor;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.service.notification.StatusBarNotification;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.BaseAdapter;
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
	public static String call_from = "";

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
					Log.d("DA.onReceive", "Alarm controls are not enabled.");
				}

			
			} else if (intent.getAction().equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED)) {
				
				if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("pref_phone_controls", false)) {
					String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
					Log.d("phone", "phone state changed to " + state);
					if (state.equals(TelephonyManager.EXTRA_STATE_RINGING)) {
						Functions.Events.incoming_call(context, intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER));
					} else {
						if (state.equals(TelephonyManager.CALL_STATE_IDLE)) {
							Functions.Events.call_finished(context);
							refreshDisplay();
						}
					}
				} else {
					Log.d("phone", "phone controls are not enabled");
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

	}  

	@SuppressWarnings("deprecation")
	@Override
	protected void onResume() {
		super.onResume();

		Log.d("DA.onResume", "On resume called.");

		refreshDisplay();

	}

	/**
	 * Refresh the display taking account of device and application state
	 */
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
	    	//show the alarm controls
	    	snoozeButton.setVisibility(View.VISIBLE);
	    	dismissButton.setVisibility(View.VISIBLE);
	    	defaultWidget.setVisibility(View.INVISIBLE);
	    	grid.setVisibility(View.INVISIBLE);
	
	    } else {
		    
		    
		    //add the required widget based on the widgetType
		    if (hmAppWidgetManager.doesWidgetExist(widgetType)) {
		    	
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
		    	//default view    	
		    	snoozeButton.setVisibility(View.INVISIBLE);
		    	dismissButton.setVisibility(View.INVISIBLE);
		    	defaultWidget.setVisibility(View.VISIBLE);
		    	grid.setVisibility(View.VISIBLE);
		    	
		    	Drawable rounded = getResources().getDrawable(R.drawable.rounded);
		    	rounded.setColorFilter(new PorterDuffColorFilter(PreferenceManager.getDefaultSharedPreferences(this).getInt("pref_default_bgcolor", 0xFF000000), PorterDuff.Mode.MULTIPLY));
		    	defaultContent.setBackground(rounded);
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

	@Override
	protected void onStart() {
		super.onStart();
		Log.d("DA-oS", "starting");
		on_screen = true;

		if (findViewById(R.id.default_battery_picture) != null) {
			Intent battery_status = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
			int level = (int) (battery_status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) / (float)battery_status.getIntExtra(BatteryManager.EXTRA_SCALE, -1) * 100),
				status = battery_status.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
			if (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL) {
				((ImageView)findViewById(R.id.default_battery_picture)).setImageResource(R.drawable.stat_sys_battery_charge);
			} else {
				((ImageView)findViewById(R.id.default_battery_picture)).setImageResource(R.drawable.stat_sys_battery);
			}
			((ImageView)findViewById(R.id.default_battery_picture)).getDrawable().setLevel(level);
			((TextView)findViewById(R.id.default_battery_percent)).setText(Integer.toString(level) + "%");
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
}
