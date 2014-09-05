/* Copyright 2013 Alex Burka

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.durka.hallmonitor;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.telephony.TelephonyManager;
import android.util.Log;

public class CoreReceiver extends BroadcastReceiver {

	private final String LOG_TAG = "Hall.CR";

	private CoreStateManager mStateManager;

	// Action fired when cover state change
	public static final String ACTION_LID_STATE_CHANGED = "android.intent.action.LID_STATE_CHANGED";
	public static final String ACTION_INTERNAL_LID_STATE_CHANGED = "org.durka.hallmonitor.LID_STATE_CHANGED";
	public static final String EXTRA_LID_STATE = "state";
	public static final int LID_ABSENT = -1;
	public static final int LID_CLOSED = 0;
	public static final int LID_OPEN = 1;
	// Action fired when alarm goes on
	public static final String ALARM_ALERT_ACTION = "com.android.deskclock.ALARM_ALERT";
	// Action to trigger snooze of the alarm
	public static final String ALARM_SNOOZE_ACTION = "com.android.deskclock.ALARM_SNOOZE";
	// Action to trigger dismiss of the alarm
	public static final String ALARM_DISMISS_ACTION = "com.android.deskclock.ALARM_DISMISS";
	// Action should let us know if the alarm has been killed by another app
	public static final String ALARM_DONE_ACTION = "com.android.deskclock.ALARM_DONE";
	// Action to toggle flashlight
	public static final String TOGGLE_FLASHLIGHT = "net.cactii.flash2.TOGGLE_FLASHLIGHT";
	// Action when toggle state change
	public static final String TORCH_STATE_CHANGED = "net.cactii.flash2.TORCH_STATE_CHANGED";
	// QUICKBOOT_POWERON
	public static final String QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON";
	// QUICKBOOT_POWERON special HTC
	public static final String HTC_QUICKBOOT_POWERON = "com.htc.intent.action.QUICKBOOT_POWERON";

	@Override
	public void onReceive(Context context, Intent intent) {
		mStateManager = ((CoreApp) context.getApplicationContext())
				.getStateManager();

		if (!mStateManager.getPreference().getBoolean("pref_enabled", false)) {
			return;
		}

		if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)
				|| intent.getAction().equals(QUICKBOOT_POWERON)
				|| intent.getAction().equals(HTC_QUICKBOOT_POWERON)) {
			Log.d(LOG_TAG + ".boot", "Boot called.");
			mStateManager.startServices();

		} else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {

			Log.d(LOG_TAG + ".screen", "Screen on event received.");

			if (mStateManager.getCoverClosed()) {
				Log.d(LOG_TAG + ".onReceive.screen",
						"Cover is closed, display Default Activity.");
				Intent mIntent = new Intent(context, CoreService.class);
				mIntent.putExtra(CoreApp.CS_EXTRA_TASK,
						CoreApp.CS_TASK_LAUNCH_ACTIVITY);
				context.startService(mIntent);
			} else {
				Log.d(LOG_TAG + ".onReceive.screen",
						"Cover is open, free everything.");

				mStateManager.freeDevice();
			}

		} else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
			mStateManager.freeDevice();

		} else if (intent.getAction().equals(Intent.ACTION_POWER_CONNECTED)) {
			Intent batteryDAIntent = new Intent(
					CoreApp.DA_ACTION_BATTERY_REFRESH);
			LocalBroadcastManager mLocalBroadcastManager = LocalBroadcastManager
					.getInstance(context);
			mLocalBroadcastManager.sendBroadcast(batteryDAIntent);

			Intent mIntent = new Intent(context, CoreService.class);
			mIntent.putExtra(CoreApp.CS_EXTRA_TASK,
					CoreApp.CS_TASK_WAKEUP_DEVICE);
			context.startService(mIntent);

		} else if (intent.getAction().equals(Intent.ACTION_POWER_DISCONNECTED)) {
			Intent batteryDAIntent = new Intent(
					CoreApp.DA_ACTION_BATTERY_REFRESH);
			LocalBroadcastManager mLocalBroadcastManager = LocalBroadcastManager
					.getInstance(context);
			mLocalBroadcastManager.sendBroadcast(batteryDAIntent);

			Intent mIntent = new Intent(context, CoreService.class);
			mIntent.putExtra(CoreApp.CS_EXTRA_TASK,
					CoreApp.CS_TASK_WAKEUP_DEVICE);
			context.startService(mIntent);

		} else if (intent.getAction().equals(ALARM_ALERT_ACTION)) {

			Log.d(LOG_TAG + ".onReceive.alarm", "Alarm on event received.");

			// only take action if alarm controls are enabled
			if (mStateManager.getPreference().getBoolean("pref_alarm_controls",
					false)) {

				Log.d(LOG_TAG + ".onReceive.alarm",
						"Alarm controls are enabled, taking action.");
				mStateManager.setAlarmFiring(true);

				Intent mIntent = new Intent(context, CoreService.class);
				mIntent.putExtra(CoreApp.CS_EXTRA_TASK,
						CoreApp.CS_TASK_INCOMMING_ALARM);
				context.startService(mIntent);
			} else {
				Log.d(LOG_TAG + ".onReceive.alarm",
						"Alarm controls are not enabled.");
			}

		} else if (intent.getAction().equals(ALARM_DONE_ACTION)) {

			Log.d(LOG_TAG + ".alarm", "Alarm done event received.");

			// only take action if alarm controls are enabled
			if (mStateManager.getPreference().getBoolean("pref_alarm_controls",
					false)) {
				Log.d(mStateManager.getPreference() + ".alarm",
						"alarm is over, cleaning up");
				mStateManager.setAlarmFiring(false);

				if (mStateManager.getCoverClosed()) {
					Intent mIntent = new Intent(context, CoreService.class);
					mIntent.putExtra(CoreApp.CS_EXTRA_TASK,
							CoreApp.CS_TASK_LAUNCH_ACTIVITY);
					context.startService(mIntent);
				}
			}
		} else if (intent.getAction().equals(
				TelephonyManager.ACTION_PHONE_STATE_CHANGED)) {

			if (mStateManager.getPreference().getBoolean("pref_phone_controls",
					false)) {
				String state = intent
						.getStringExtra(TelephonyManager.EXTRA_STATE);
				Log.d(LOG_TAG + ".phone", "phone state changed to " + state);
				if (state.equals(TelephonyManager.EXTRA_STATE_RINGING)) {
					Intent mIntent;
					mStateManager.setPhoneRinging(true);
					mStateManager
							.setCallFrom(intent
									.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER));
					Log.d(LOG_TAG, "call from " + mStateManager.getCallFrom());
					mIntent = new Intent(mStateManager.getContext(),
							CoreService.class);
					mIntent.putExtra(CoreApp.CS_EXTRA_TASK,
							CoreApp.CS_TASK_INCOMMING_CALL);
					mStateManager.getContext().startService(mIntent);
				} else if (state.equals(TelephonyManager.EXTRA_STATE_IDLE)) {
					Intent mIntent;
					mStateManager.setPhoneRinging(false);
					Log.d(LOG_TAG, "call is over, cleaning up");
					if (mStateManager.getCoverClosed()) {
						mIntent = new Intent(mStateManager.getContext(),
								CoreService.class);
						mIntent.putExtra(CoreApp.CS_EXTRA_TASK,
								CoreApp.CS_TASK_LAUNCH_ACTIVITY);
						mStateManager.getContext().startService(mIntent);
					}
				} else if (state.equals(TelephonyManager.EXTRA_STATE_OFFHOOK)) {
				}
			} else {
				Log.d(LOG_TAG + ".phone", "phone controls are not enabled");
			}
		} else if (intent.getAction().equals(mStateManager.getActionCover())) {
			int state = intent.getIntExtra(EXTRA_LID_STATE, LID_ABSENT);
			Log.d(LOG_TAG + ".onReceive.cover", "cover state changed to "
					+ state);
			if (state == LID_CLOSED) {
				Log.d(LOG_TAG + ".onReceive.cover",
						"Cover is close, enable Default Activity.");
				mStateManager.setCoverClosed(true);
				Intent mIntent = new Intent(context, CoreService.class);
				mIntent.putExtra(CoreApp.CS_EXTRA_TASK,
						CoreApp.CS_TASK_LAUNCH_ACTIVITY);
				context.startService(mIntent);
			} else if (state == LID_OPEN) {
				Log.d(LOG_TAG + ".onReceive.cover",
						"Cover is open, stopping Default Activity.");
				mStateManager.setCoverClosed(false);
				Intent mIntent = new Intent(context, CoreService.class);
				mIntent.putExtra(CoreApp.CS_EXTRA_TASK,
						CoreApp.CS_TASK_WAKEUP_DEVICE);
				context.startService(mIntent);
			}

		} else if (intent.getAction().equals(TORCH_STATE_CHANGED)) {
			if (mStateManager.getPreference().getBoolean("pref_flash_controls",
					false)) {
				Log.d(LOG_TAG + ".onReceive.torch", "torch state changed");
				Intent mIntent = new Intent(context, CoreService.class);
				mIntent.putExtra(CoreApp.CS_EXTRA_TASK,
						CoreApp.CS_TASK_TORCH_STATE);
				if (intent.getIntExtra("state", 0) != 0) {
					mIntent.putExtra(CoreApp.CS_EXTRA_STATE, true);
				} else {
					mIntent.putExtra(CoreApp.CS_EXTRA_STATE, false);
				}
				context.startService(mIntent);
			} else {
				Log.d(LOG_TAG + ".onReceive.torch",
						"torch controls are not enabled.");
			}

		} else if (intent.getAction().equals(Intent.ACTION_HEADSET_PLUG)) {
			int state = intent.getExtras().getInt("state");
			Log.d(LOG_TAG + ".onReceive.headset", "headset is "
					+ (state == 0 ? "gone" : "here") + "!");
			Intent mIntent = new Intent(context, CoreService.class);
			mIntent.putExtra(CoreApp.CS_EXTRA_TASK,
					CoreApp.CS_TASK_HEADSET_PLUG);
			context.startService(mIntent);

		} else if (intent.getAction().equals("org.durka.hallmonitor.debug")) {
			Log.d(LOG_TAG + ".onReceive", "received debug intent");
			// test intent to show/hide a notification
			boolean showhide = false;
			switch (intent.getIntExtra("notif", 0)) {
			case 1:
				showhide = true;
				break;
			case 2:
				showhide = false;
				break;
			}
			if (showhide) {
				Notification.Builder mBuilder = new Notification.Builder(
						context).setSmallIcon(R.drawable.ic_launcher)
						.setContentTitle("Hall Monitor")
						.setContentText("Debugging is fun!");

				NotificationManager mNotificationManager = (NotificationManager) context
						.getSystemService(Context.NOTIFICATION_SERVICE);
				mNotificationManager.notify(42, mBuilder.build());
			} else {
				NotificationManager mNotificationManager = (NotificationManager) context
						.getSystemService(Context.NOTIFICATION_SERVICE);
				mNotificationManager.cancel(42);
			}
		}
	}
}