package org.durka.hallmonitor;

import io.github.homelocker.lib.HomeKeyLocker;

import java.text.DateFormat;
import java.util.Date;

import android.app.Activity;
import android.appwidget.AppWidgetHostView;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.service.notification.StatusBarNotification;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextClock;
import android.widget.TextView;

/**
 * This is the activity that is displayed by default - it is displayed for the
 * configurable delay number of milliseconds when the case is closed, it is also
 * displayed when the power button is pressed when the case is already closed
 */
public class DefaultActivity extends Activity {
	private static final String LOG_TAG = "Hall.DA";

	// all the views we need
	private ImageButton torchButton = null;
	private ImageButton cameraButton = null;
	private ViewGroup defaultWidgetAreaVG = null;
	private View mainView = null;

	// manager for home key hack
	private HomeKeyLocker homeKeyLocker;

	private CoreStateManager mStateManager;

	private String daId;
	private BroadcastReceiver mMessageReceiver;

	private int allLayoutParams;

	/**
	 * Refresh the display taking account of device and application state
	 */
	private void refreshDisplay() {
		Log.d(LOG_TAG + daId + ".rD", "refreshing");

		// if the alarm is firing then show the alarm controls, otherwise
		if (mStateManager.getAlarmFiring()) {
			displayAlarm();
		} else if (mStateManager.getPhoneRinging()) {
			displayPhone();
		} else if (mStateManager.getCameraUp()) {
			displayCamera();
		} else {
			displayNormal();
		}
	}

	private void displayAlarm() {
		// show the alarm controls
		findViewById(R.id.default_content_alarm).setVisibility(View.VISIBLE);
		findViewById(R.id.default_content_phone).setVisibility(View.GONE);
		findViewById(R.id.default_content_normal).setVisibility(View.GONE);
		findViewById(R.id.default_content_camera).setVisibility(View.GONE);
	}

	private void displayPhone() {
		((TextView) findViewById(R.id.call_from))
				.setText(getString(R.string.unknown_caller));
		// show the phone controls
		findViewById(R.id.default_content_alarm).setVisibility(View.GONE);
		findViewById(R.id.default_content_phone).setVisibility(View.VISIBLE);
		findViewById(R.id.default_content_normal).setVisibility(View.GONE);
		findViewById(R.id.default_content_camera).setVisibility(View.GONE);

		((TextView) findViewById(R.id.call_from)).setText(getContactName(this,
				mStateManager.getCallFrom()));

	}

	private void displayCamera() {
		findViewById(R.id.default_content_alarm).setVisibility(View.GONE);
		findViewById(R.id.default_content_phone).setVisibility(View.GONE);
		findViewById(R.id.default_content_normal).setVisibility(View.GONE);
		findViewById(R.id.default_content_camera).setVisibility(View.VISIBLE);
	}

	private void displayNormal() {
		setWidgetContent();
		setBatteryIcon();

		// normal view
		findViewById(R.id.default_content_alarm).setVisibility(View.GONE);
		findViewById(R.id.default_content_phone).setVisibility(View.GONE);
		findViewById(R.id.default_content_normal).setVisibility(View.VISIBLE);
		findViewById(R.id.default_content_camera).setVisibility(View.GONE);
	}

	private void setBatteryIcon() {
		if (findViewById(R.id.default_battery_picture_horizontal) != null) {
			Intent battery_status = registerReceiver(null, new IntentFilter(
					Intent.ACTION_BATTERY_CHANGED));
			int level = (int) (battery_status.getIntExtra(
					BatteryManager.EXTRA_LEVEL, -1)
					/ (float) battery_status.getIntExtra(
							BatteryManager.EXTRA_SCALE, -1) * 100), status = battery_status
					.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
			if (status == BatteryManager.BATTERY_STATUS_CHARGING
					|| status == BatteryManager.BATTERY_STATUS_FULL) {
				((ImageView) findViewById(R.id.default_battery_picture_horizontal))
						.setImageResource(R.drawable.stat_sys_battery_charge_horizontal);
				if (level > 99) {
					((TextView) findViewById(R.id.default_battery_percent))
							.setText("");
				} else {
					((TextView) findViewById(R.id.default_battery_percent))
							.setText(Integer.toString(level));
				}
			} else {
				((ImageView) findViewById(R.id.default_battery_picture_horizontal))
						.setImageResource(R.drawable.stat_sys_battery_horizontal);
				if (level > 99) {
					((TextView) findViewById(R.id.default_battery_percent))
							.setText("");
				} else {
					((TextView) findViewById(R.id.default_battery_percent))
							.setText(Integer.toString(level));
				}
			}
			((ImageView) findViewById(R.id.default_battery_picture_horizontal))
					.getDrawable().setLevel(level);
		}
	}

	private void refreshNotifications() {
		if (mStateManager.getPreference().getBoolean("pref_do_notifications",
				false)) {
			final GridView grid = (GridView) findViewById(R.id.default_icon_container);
			final NotificationAdapter adapter = (NotificationAdapter) grid
					.getAdapter();
			final StatusBarNotification[] notifs = NotificationService.that
					.getActiveNotifications();
			adapter.update(notifs);
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					grid.setNumColumns(notifs.length);
					adapter.notifyDataSetChanged();
				}
			});
		}
	}

	private void setupNotifications() {
		StatusBarNotification[] notifs = NotificationService.that
				.getActiveNotifications();
		Log.d(LOG_TAG + daId + ".sN", Integer.toString(notifs.length)
				+ " notifications");
		GridView grid = (GridView) findViewById(R.id.default_icon_container);
		grid.setNumColumns(notifs.length);
		grid.setAdapter(new NotificationAdapter(this, notifs));
	}

	private void setMainLayout() {
		if (mStateManager.getPreference().getBoolean("pref_layout", true)) {
			setContentView(R.layout.activity_alternative);
			if (mStateManager.getPreference().getBoolean(
					"pref_do_notifications", true)) {
				getWindow().clearFlags(
						WindowManager.LayoutParams.FLAG_FULLSCREEN);
				findViewById(R.id.default_battery_percent).setVisibility(
						View.INVISIBLE);
				findViewById(R.id.default_battery_picture_horizontal)
						.setVisibility(View.INVISIBLE);
			}
		} else {
			setContentView(R.layout.activity_default);
		}
		mainView = findViewById(R.id.default_main);
		mainView.setFocusable(true);
		mainView.setFocusableInTouchMode(true);
	}

	private void setCallInput() {
		if (mStateManager.getPreference().getBoolean(
				"pref_incoming_call_input", false)) {
			findViewById(R.id.swipe_call).setLongClickable(true);
			findViewById(R.id.swipe_call).setOnTouchListener(
					new SwipeTouchListener(this,
							SwipeTouchListener.ActionMode.MODE_CALL));
			findViewById(R.id.swipe_call).setVisibility(View.VISIBLE);
			Log.d(LOG_TAG + daId + ".sCI", "Call Swipe");
		} else {
			findViewById(R.id.swipe_call).setVisibility(View.GONE);
			Log.d(LOG_TAG + daId + ".sCI", "Call Button");
		}
	}

	private void setAlarmInput() {
		if (mStateManager.getPreference().getBoolean("pref_alarm_input", false)) {
			findViewById(R.id.swipe_alarm).setLongClickable(true);
			findViewById(R.id.swipe_alarm).setOnTouchListener(
					new SwipeTouchListener(this,
							SwipeTouchListener.ActionMode.MODE_ALARM));
			findViewById(R.id.swipe_alarm).setVisibility(View.VISIBLE);
			Log.d(LOG_TAG + daId + ".sAI", "Alarm Swipe");
		} else {
			findViewById(R.id.swipe_alarm).setVisibility(View.GONE);
			Log.d(LOG_TAG + daId + ".sAI", "Alarm Button");
		}
	}

	// TODO simpler layout swipe (add a transparent view?)
	private void setNormalInput() {
		// Torch swipe/button
		if ((mStateManager.getPreference().getBoolean("pref_flash_controls",
				false) || mStateManager.getPreference().getBoolean(
				"pref_flash_controls_alternative", false))
				&& mStateManager.getPreference().getBoolean("pref_torch_input",
						false)) {
			findViewById(R.id.swipe_torch).setLongClickable(true);
			findViewById(R.id.swipe_torch).setOnTouchListener(
					new SwipeTouchListener(this,
							SwipeTouchListener.ActionMode.MODE_TORCH));
			findViewById(R.id.swipe_torch).setVisibility(View.VISIBLE);

			Log.d(LOG_TAG + daId + ".sTI", "Torch Swipe");
		} else {
			findViewById(R.id.swipe_torch).setVisibility(View.GONE);
			Log.d(LOG_TAG + daId + ".sTI", "Torch Button");
		}

		// Camera swipe/button
		if (mStateManager.getPreference().getBoolean("pref_camera_controls",
				false)
				&& mStateManager.getPreference().getBoolean(
						"pref_camera_input", false)) {
			findViewById(R.id.swipe_camera).setLongClickable(true);
			findViewById(R.id.swipe_camera).setOnTouchListener(
					new SwipeTouchListener(this,
							SwipeTouchListener.ActionMode.MODE_CAMERA));
			findViewById(R.id.swipe_camera).setVisibility(View.VISIBLE);

			Log.d(LOG_TAG + daId + ".sTI", "Camera Swipe");
		} else {
			findViewById(R.id.swipe_camera).setVisibility(View.GONE);
			Log.d(LOG_TAG + daId + ".sTI", "Camera Button");
		}
	}

	private void visibilityTorchButton() {
		// hide or show the torch button as required
		if (mStateManager.getPreference().getBoolean("pref_flash_controls",
				false)
				|| mStateManager.getPreference().getBoolean(
						"pref_flash_controls_alternative", false)) {
			torchButton.setVisibility(View.VISIBLE);
		} else {
			torchButton.setVisibility(View.GONE);
		}
	}

	private void visibilityCameraButton() {
		// hide or show the camera button as required
		if (mStateManager.getPreference().getBoolean("pref_camera_controls",
				false)) {
			cameraButton.setVisibility(View.VISIBLE);
		} else {
			cameraButton.setVisibility(View.GONE);
		}
	}

	private void changeColor() {
		// set the colors based on the picker values
		Drawable rounded = getResources().getDrawable(R.drawable.rounded);
		rounded.setColorFilter(new PorterDuffColorFilter(mStateManager
				.getPreference().getInt("pref_default_bgcolor", 0xFF000000),
				PorterDuff.Mode.MULTIPLY));
		((RelativeLayout) findViewById(R.id.default_content))
				.setBackground(rounded);
		((TextClock) findViewById(R.id.default_text_clock))
				.setTextColor(mStateManager.getPreference().getInt(
						"pref_default_fgcolor", 0xFFFFFFFF));
		((TextClock) findViewById(R.id.default_text_clock_hour))
				.setTextColor(mStateManager.getPreference().getInt(
						"pref_default_fgcolor", 0xFFFFFFFF));
		((TextView) findViewById(R.id.default_text_clock_date))
				.setTextColor(mStateManager.getPreference().getInt(
						"pref_default_fgcolor", 0xFFFFFFFF));
		((TextView) findViewById(R.id.default_text_fulltime_very_small))
				.setTextColor(mStateManager.getPreference().getInt(
						"pref_default_fgcolor", 0xFFFFFFFF));
	}

	private void changeTimeDateDisplay() {
		changeTimeDateDisplay(false);
	}

	private void changeTimeDateDisplay(boolean removeAll) {
		if (removeAll) {
			findViewById(R.id.default_text_clock).setVisibility(View.GONE);
			findViewById(R.id.default_text_clock_hour).setVisibility(View.GONE);
			findViewById(R.id.default_text_clock_date).setVisibility(View.GONE);
		} else {
			if (mStateManager.getPreference()
					.getBoolean("pref_datetime", false)) {
				findViewById(R.id.default_text_clock).setVisibility(View.GONE);
				findViewById(R.id.default_text_clock_hour).setVisibility(
						View.VISIBLE);
				findViewById(R.id.default_text_clock_date).setVisibility(
						View.VISIBLE);
				findViewById(R.id.default_text_fulltime_very_small)
						.setVisibility(View.GONE);
			} else {
				findViewById(R.id.default_text_clock).setVisibility(
						View.VISIBLE);
				findViewById(R.id.default_text_clock_hour).setVisibility(
						View.GONE);
				findViewById(R.id.default_text_clock_date).setVisibility(
						View.GONE);
				findViewById(R.id.default_text_fulltime_very_small)
						.setVisibility(View.GONE);
			}
		}
	}

	/**
	 * If we have a media app widget and media is playing or headphones are
	 * connected then display that, otherwise if we have a default app widget to
	 * use then display that, if not then display our default clock screen
	 * (which is part of the default layout so will show anyway) will do this
	 * simply by setting the widgetType
	 */
	public void setWidgetContent() {
		if (!(mStateManager.getPreference().getBoolean("pref_media_widget",
				false) || mStateManager.getPreference().getBoolean(
				"pref_default_widget", false))) {
			defaultWidgetAreaVG.setVisibility(View.GONE);
			changeTimeDateDisplay();
			return;
		}
		String widgetType = "default";
		if (mStateManager.getPreference()
				.getBoolean("pref_media_widget", false)
				&& ((mStateManager.getAudioManager().isWiredHeadsetOn() || mStateManager
						.getAudioManager().isMusicActive()))) {
			widgetType = "media";
		}

		// get the widget
		AppWidgetHostView hostView = mStateManager.getHMAppWidgetManager()
				.getAppWidgetHostViewByType(widgetType);

		String mDate = DateFormat.getDateInstance(DateFormat.MEDIUM).format(
				new Date());
		String mTime = DateFormat.getTimeInstance(DateFormat.SHORT).format(
				new Date());

		// add the required widget
		if (hostView != null) {
			((RelativeLayout) defaultWidgetAreaVG).removeAllViews();

			// if the widget host view already has a parent then we need to
			// detach it
			ViewGroup parent = (ViewGroup) hostView.getParent();
			if (parent != null) {
				Log.d(LOG_TAG + daId + ".sWC",
						"hostView had already been added to a group, detaching it.");
				parent.removeView(hostView);
			}

			// add the widget to the view
			((RelativeLayout) defaultWidgetAreaVG).addView(hostView);

			((TextView) findViewById(R.id.default_text_fulltime_very_small))
					.setText(mTime + " " + mDate);
			changeTimeDateDisplay(true);
			defaultWidgetAreaVG.setVisibility(View.VISIBLE);
			findViewById(R.id.default_text_fulltime_very_small).setVisibility(
					View.VISIBLE);
		} else {
			((TextView) findViewById(R.id.default_text_clock_date))
					.setText(mDate);
			defaultWidgetAreaVG.setVisibility(View.GONE);
			findViewById(R.id.default_text_fulltime_very_small).setVisibility(
					View.GONE);
			changeTimeDateDisplay();
		}
	}

	/** Called when the user touches the snooze button */
	public void sendSnooze(View view) {
		Log.d(LOG_TAG + daId, "Alarm button: snooze alarm");
		Intent alarmSnooze = new Intent(this, CoreService.class);
		alarmSnooze.putExtra(CoreApp.CS_EXTRA_TASK,
				CoreApp.CS_TASK_SNOOZE_ALARM);
		mStateManager.sendToCoreService(alarmSnooze);
	}

	/** Called when the user touches the dismiss button */
	public void sendDismiss(View view) {
		Log.d(LOG_TAG + daId, "Alarm button: dismiss... I am wake");
		Intent alarmDismiss = new Intent(this, CoreService.class);
		alarmDismiss.putExtra(CoreApp.CS_EXTRA_TASK,
				CoreApp.CS_TASK_SNOOZE_ALARM);
		mStateManager.sendToCoreService(alarmDismiss);
	}

	/** Called when the user touches the hangup button */
	public void sendHangUp(View view) {
		Intent hangUpCallIntent = new Intent(this, CoreService.class);
		hangUpCallIntent.putExtra(CoreApp.CS_EXTRA_TASK,
				CoreApp.CS_TASK_HANGUP_CALL);
		mStateManager.sendToCoreService(hangUpCallIntent);
	}

	/** Called when the user touches the pickup button */
	public void sendPickUp(View view) {
		Intent pickUpCallIntent = new Intent(this, CoreService.class);
		pickUpCallIntent.putExtra(CoreApp.CS_EXTRA_TASK,
				CoreApp.CS_TASK_PICKUP_CALL);
		mStateManager.sendToCoreService(pickUpCallIntent);
	}

	/** Called when the user touches the torch button */
	public void sendToggleTorch(View view) {
		Intent torchIntent = new Intent(this, CoreService.class);
		torchIntent.putExtra(CoreApp.CS_EXTRA_TASK,
				CoreApp.CS_TASK_TORCH_TOGGLE);
		mStateManager.sendToCoreService(torchIntent);
	}

	// from
	// http://stackoverflow.com/questions/3712112/search-contact-by-phone-number
	private String getContactName(Context ctx, String number) {

		if (number.equals("")) {
			return "";
		}

		Log.d(LOG_TAG + daId + ".contact", "looking up " + number + "...");

		Uri uri = Uri.withAppendedPath(
				ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
				Uri.encode(number));
		String name = number;

		ContentResolver contentResolver = ctx.getContentResolver();
		Cursor contactLookup = contentResolver.query(uri, new String[] {
				BaseColumns._ID, ContactsContract.PhoneLookup.DISPLAY_NAME },
				null, null, null);

		try {
			if (contactLookup != null && contactLookup.getCount() > 0) {
				contactLookup.moveToNext();
				name = contactLookup.getString(contactLookup
						.getColumnIndex(ContactsContract.Data.DISPLAY_NAME));
				// String contactId =
				// contactLookup.getString(contactLookup.getColumnIndex(BaseColumns._ID));
			}
		} finally {
			if (contactLookup != null) {
				contactLookup.close();
			}
		}

		Log.d(LOG_TAG + daId + ".contact", "...result is " + name);
		return name;
	}

	// fire up the camera
	public void startCamera(View view) {
		startCamera();
	}

	public void startCamera() {
		Log.d(LOG_TAG, "starting camera");
		mStateManager.setCameraUp(true);
		mStateManager.setBlackScreenTime(0);
		getWindow().addFlags(
				WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
						| WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		if (mStateManager.getTorchOn()
				&& mStateManager.getPreference().getBoolean(
						"pref_flash_controls_alternative", false)) {
			mStateManager.turnOffFlash();
			torchButton.setImageResource(R.drawable.ic_appwidget_torch_off);
		}
		findViewById(R.id.default_camera).setVisibility(View.VISIBLE);
		displayCamera();
		Log.d(LOG_TAG, "started camera");
	}

	public void captureCamera(View view) {
		Log.d(LOG_TAG, "camera say cheese");
		((CameraPreview) findViewById(R.id.default_camera)).capture();
	}

	public void stopCamera(View view) {
		stopCamera();
		Log.d(LOG_TAG, "closed camera");
	}

	public void stopCamera() {
		mStateManager.setCameraUp(false);
		refreshDisplay();
		findViewById(R.id.default_camera).setVisibility(View.INVISIBLE);
		Intent mIntent = new Intent(this, CoreService.class);
		mIntent.putExtra(CoreApp.CS_EXTRA_TASK,
				CoreApp.CS_TASK_AUTO_BLACKSCREEN);
		mStateManager.sendToCoreService(mIntent);
	}

	private void setRealFullscreen() {
		if (mStateManager.getPreference().getBoolean("pref_realfullscreen",
				false)) {
			// Remove notification bar
			allLayoutParams |= WindowManager.LayoutParams.FLAG_FULLSCREEN;

			// Remove navigation bar
			View decorView = getWindow().getDecorView();
			decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
					| View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
					| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
					| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
					| View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
					| View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
		}
	}

	/*
	 * private void reload() { set_real_fullscreen(); choose_layout();
	 * torchButton = (ImageButton) findViewById(R.id.torchbutton); cameraButton
	 * = (ImageButton) findViewById(R.id.camerabutton); choose_call_input();
	 * choose_alarm_input(); choose_torch_input();
	 * 
	 * changeColor(); visibilityTorchButton(); visibilityCameraButton();
	 * setWidgetContent();
	 * 
	 * findViewById(R.id.default_main).setLongClickable(true);
	 * findViewById(R.id.default_main).setOnTouchListener( new
	 * SwipeTouchListener(this,
	 * SwipeTouchListener.ActionMode.MODE_NOTHINGTRUE));
	 * findViewById(R.id.default_undercover).setLongClickable(true);
	 * findViewById(R.id.default_undercover).setOnTouchListener( new
	 * SwipeTouchListener(this,
	 * SwipeTouchListener.ActionMode.MODE_NOTHINGTRUE)); }
	 */

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		daId = CoreStateManager.createID();
		Log.d(LOG_TAG + daId + ".onCreate", "creating");

		mStateManager = ((CoreApp) getApplicationContext()).getStateManager();

		mStateManager.setDefaultActivityStarting(true);

		// pass a reference back to the state manager
		if (!mStateManager.setDefaultActivity(this)) {
			Log.w(LOG_TAG + daId, "Warning already default activity set!!!!");
			finish();
			return;
		}

		mStateManager.acquireCPUDA();

		mStateManager.closeConfigurationActivity();

		// Remove title bar
		requestWindowFeature(Window.FEATURE_NO_TITLE);

		// Display in fullscreen
		setRealFullscreen();

		if (mStateManager.getHardwareAccelerated()) {
			allLayoutParams |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;

		}

		// Keep screen on during display
		allLayoutParams |= WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
				| WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
				// Display before lock screen
				| WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
				// Enable multitouch started outside view
				| WindowManager.LayoutParams.FLAG_SPLIT_TOUCH;

		getWindow().addFlags(allLayoutParams);
		setMainLayout();

		// get the views we need
		torchButton = (ImageButton) findViewById(R.id.torchbutton);
		cameraButton = (ImageButton) findViewById(R.id.camerabutton);
		defaultWidgetAreaVG = (ViewGroup) findViewById(R.id.default_widget_area);

		// home key hack
		homeKeyLocker = new HomeKeyLocker();

		setCallInput();
		setAlarmInput();
		setNormalInput();

		changeColor();
		visibilityTorchButton();
		visibilityCameraButton();
		setWidgetContent();

		findViewById(R.id.default_main).setLongClickable(true);
		findViewById(R.id.default_main).setOnTouchListener(
				new SwipeTouchListener(this,
						SwipeTouchListener.ActionMode.MODE_NOTHINGTRUE));
		findViewById(R.id.default_undercover).setLongClickable(true);
		findViewById(R.id.default_undercover).setOnTouchListener(
				new SwipeTouchListener(this,
						SwipeTouchListener.ActionMode.MODE_NOTHINGTRUE));
		mMessageReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				String action = intent.getAction();
				if (action.equals(CoreApp.DA_ACTION_TORCH_STATE_CHANGED)) {
					if (intent.getBooleanExtra(CoreApp.DA_EXTRA_STATE, false)) {
						runOnUiThread(new Runnable() {
							@Override
							public void run() {
								torchButton
										.setImageResource(R.drawable.ic_appwidget_torch_on);
							}
						});
					} else {
						runOnUiThread(new Runnable() {
							@Override
							public void run() {
								torchButton
										.setImageResource(R.drawable.ic_appwidget_torch_off);
							}
						});
					}

				} else if (action.equals(CoreApp.DA_ACTION_WIDGET_REFRESH)) {
					setWidgetContent();

				} else if (action.equals(CoreApp.DA_ACTION_BATTERY_REFRESH)) {
					setBatteryIcon();

				} else if (action
						.equals(CoreApp.DA_ACTION_NOTIFICATION_REFRESH)) {
					refreshNotifications();

				} else if (action.equals(CoreApp.DA_ACTION_START_CAMERA)) {
					startCamera();

				} else if (action.equals(CoreApp.DA_ACTION_STATE_CHANGED)) {
					switch (intent.getIntExtra(CoreApp.DA_EXTRA_STATE, 0)) {
					case CoreApp.DA_EXTRA_STATE_NORMAL:
						displayNormal();
						break;
					case CoreApp.DA_EXTRA_STATE_ALARM:
						displayAlarm();
						break;
					case CoreApp.DA_EXTRA_STATE_PHONE:
						displayPhone();
						break;
					case CoreApp.DA_EXTRA_STATE_CAMERA:
						displayCamera();
						break;
					}

				} else if (action.equals(CoreApp.DA_ACTION_SEND_TO_BACKGROUND)) {
					Log.d(LOG_TAG + daId, "Send to background");
					moveTaskToBack(true);

				} else if (action.equals(CoreApp.DA_ACTION_FREE_SCREEN)) {
					Log.d(LOG_TAG + daId, "Call to finish");
					getWindow()
							.clearFlags(
									WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
											| WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

				} else if (action.equals(CoreApp.DA_ACTION_FINISH)) {
					if (mStateManager.getDefaultActivityStarting()) {
						Log.w(LOG_TAG + daId, "Starting, could not finish");
					} else {
						Log.d(LOG_TAG + daId, "Call to finish");
						finish();
					}
				}
			}
		};

		IntentFilter mIntentFilter = new IntentFilter();
		mIntentFilter.addAction(CoreApp.DA_ACTION_BATTERY_REFRESH);
		mIntentFilter.addAction(CoreApp.DA_ACTION_FINISH);
		mIntentFilter.addAction(CoreApp.DA_ACTION_FREE_SCREEN);
		mIntentFilter.addAction(CoreApp.DA_ACTION_NOTIFICATION_REFRESH);
		mIntentFilter.addAction(CoreApp.DA_ACTION_SEND_TO_BACKGROUND);
		mIntentFilter.addAction(CoreApp.DA_ACTION_START_CAMERA);
		mIntentFilter.addAction(CoreApp.DA_ACTION_STATE_CHANGED);
		mIntentFilter.addAction(CoreApp.DA_ACTION_TORCH_STATE_CHANGED);
		mIntentFilter.addAction(CoreApp.DA_ACTION_WIDGET_REFRESH);
		LocalBroadcastManager.getInstance(this).registerReceiver(
				mMessageReceiver, mIntentFilter);
	}

	@Override
	public void onWindowFocusChanged(boolean hasWindowFocus) {
		super.onWindowFocusChanged(hasWindowFocus);

		if (hasWindowFocus) {
			mStateManager.acquireCPUDA();
			Log.d(LOG_TAG + daId + ".onWFC", "Get focus.");
		} else {
			mStateManager.releaseCPUDA();
			Log.d(LOG_TAG + daId + ".onWFC", "No focus.");
		}

	}

	@Override
	protected void onStart() {
		mStateManager.acquireCPUDA();
		Log.d(LOG_TAG + daId + ".onStart", "starting");
		mStateManager.setDefaultActivityStarting(true);

		super.onStart();
	}

	@Override
	protected void onResume() {
		mStateManager.acquireCPUDA();
		Log.d(LOG_TAG + daId + ".onResume", "resuming");
		mStateManager.setDefaultActivityStarting(true);

		// Keep screen on during display
		getWindow().addFlags(
				WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
						| WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		if (NotificationService.that != null) {
			// notification listener service is running, show the current
			// notifications
			setupNotifications();
			refreshNotifications();
		}

		refreshDisplay();

		/*
		 * // check preview (extras are configured in xml) if
		 * (getIntent().getExtras() != null &&
		 * !getIntent().getExtras().getString("preview", "").equals("")) {
		 * String preview = getIntent().getExtras().getString("preview");
		 * 
		 * if (preview.equals("phoneWidget")) { new AlertDialog.Builder(this)
		 * .setMessage(
		 * "search AlertDialog in DefaultActivity to place your code for preview of '"
		 * + preview + "' there!") .setPositiveButton(android.R.string.yes, new
		 * DialogInterface.OnClickListener() { public void
		 * onClick(DialogInterface dialog, int which) { finish(); } }) .show();
		 * } }
		 */
		if (mStateManager.getPreference().getBoolean("pref_disable_home", true)) {
			homeKeyLocker.lock(this);
		}

		Intent touchCoverIntent = new Intent(this, CoreService.class);
		touchCoverIntent.putExtra(CoreApp.CS_EXTRA_TASK,
				CoreApp.CS_TASK_CHANGE_TOUCHCOVER);
		touchCoverIntent.putExtra(CoreApp.CS_EXTRA_STATE, true);
		mStateManager.sendToCoreService(touchCoverIntent);

		mainView.requestLayout();
		mainView.requestFocus();

		super.onResume();

		mStateManager.releaseCPUDA();
	}

	@Override
	protected void onPause() {
		Log.d(LOG_TAG + daId + ".onPause", "pausing");

		Intent mIntent = new Intent(this, CoreService.class);
		mIntent.putExtra(CoreApp.CS_EXTRA_TASK,
				CoreApp.CS_TASK_CHANGE_TOUCHCOVER);
		mStateManager.sendToCoreService(mIntent);
		homeKeyLocker.unlock();

		mStateManager.releaseCPUDA();
		super.onPause();
	}

	@Override
	protected void onStop() {
		Log.d(LOG_TAG + daId + ".onStop", "stopping");

		if (mStateManager.getPreference().getBoolean("pref_keyguard", true)) {
			getWindow().addFlags(
					WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
		}
		if (mStateManager.getTorchOn()
				&& mStateManager.getPreference().getBoolean(
						"pref_flash_controls_alternative", false)) {
			Intent torchIntent = new Intent(this, CoreService.class);
			torchIntent.putExtra(CoreApp.CS_EXTRA_TASK,
					CoreApp.CS_TASK_TORCH_TOGGLE);
			mStateManager.sendToCoreService(torchIntent);
		}
		if (mStateManager.getCameraUp()) {
			mStateManager.setCameraUp(false);
		}
		super.onStop();
	}

	@Override
	protected void onDestroy() {
		Log.d(LOG_TAG + daId + ".onDestroy", "detroying");
		mStateManager.setDefaultActivityStarting(false);

		LocalBroadcastManager.getInstance(this).unregisterReceiver(
				mMessageReceiver);
		mStateManager.releaseCPUDA();
		mStateManager.setDefaultActivity(null);
		super.onDestroy();
	}
}
