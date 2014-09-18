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

import android.app.Application;

public class CoreApp extends Application {

	// Callback identifiers for startActivityForResult, used by the preference
	// screen
	public static final int DEVICE_ADMIN_WAITING = 42;
	public static final int REQUEST_PICK_APPWIDGET = 9;
	public static final int REQUEST_CONFIGURE_APPWIDGET = 5;
	public static final int NOTIFICATION_LISTENER_ON = 0xDEAD;
	public static final int NOTIFICATION_LISTENER_OFF = 0xBEEF;

	// Supported devices
	public static final String DEV_SERRANO_LTE_CM10 = "serranolte"; // GT-I9195
																	// CM10.x
	public static final String DEV_SERRANO_LTE_CM11 = "serranoltexx"; // GT-I9195
																		// CM11.x
	public static final String DEV_SERRANO_3G_CM11 = "serrano3gxx"; // GT-I9190
																	// CM11.x
	public static final String DEV_SERRANO_DS_CM10 = "serranods"; // GT-I9192
																	// CM10.x
	public static final String DEV_SERRANO_DS_CM11 = "serranodsxx"; // GT-I9192
																	// CM11.x

	// CoreService constant
	public static final String CS_EXTRA_TASK = "task";
	public static final String CS_EXTRA_STATE = "extra";

	public static final int CS_TASK_MAINLAUNCH = 1;
	public static final int CS_TASK_NOTHING = 2;
	public static final int CS_TASK_INCOMMING_CALL = 3;
	public static final int CS_TASK_INCOMMING_ALARM = 4;
	public static final int CS_TASK_AUTO_BLACKSCREEN = 5;
	public static final int CS_TASK_LAUNCH_ACTIVITY = 7;
	public static final int CS_TASK_WAKEUP_DEVICE = 8;
	public static final int CS_TASK_CHANGE_TOUCHCOVER = 9;
	public static final int CS_TASK_TORCH_STATE = 10;
	public static final int CS_TASK_TORCH_TOGGLE = 11;
	public static final int CS_TASK_CAMERA_TOGGLE = 12;
	public static final int CS_TASK_HEADSET_PLUG = 13;
	public static final int CS_TASK_SNOOZE_ALARM = 14;
	public static final int CS_TASK_DISMISS_ALARM = 15;
	public static final int CS_TASK_HANGUP_CALL = 16;
	public static final int CS_TASK_PICKUP_CALL = 17;

	public static final String PACKAGE_PHONE_APP = "com.android.dialer";
	public static final String PACKAGE_ALARM_APP = "com.android.deskclock";

	// DefaultActivity constant
	public static final String DA_ACTION_TORCH_STATE_CHANGED = "org.durka.hallmonitor.TORCH_STATE_CHANGED";
	public static final String DA_ACTION_STATE_CHANGED = "org.durka.hallmonitor.DA_STATE_CHANGED";
	public static final String DA_ACTION_WIDGET_REFRESH = "org.durka.hallmonitor.DA_WIDGET_REFRESH";
	public static final String DA_ACTION_NOTIFICATION_REFRESH = "org.durka.hallmonitor.DA_NOTIFICATION_REFRESH";
	public static final String DA_ACTION_BATTERY_REFRESH = "org.durka.hallmonitor.DA_BATTERY_REFRESH";
	public static final String DA_ACTION_START_CAMERA = "org.durka.hallmonitor.DA_START_CAMERA";
	public static final String DA_ACTION_FINISH = "org.durka.hallmonitor.DA_FINISH";
	public static final String DA_ACTION_FREE_SCREEN = "org.durka.hallmonitor.DA_FREE_SCREEN";
	public static final String DA_ACTION_SEND_TO_BACKGROUND = "org.durka.hallmonitor.DA_SEND_TO_BACKGROUND";
	public static final String DA_EXTRA_STATE = "state";
	public static final int DA_EXTRA_STATE_NORMAL = 0;
	public static final int DA_EXTRA_STATE_ALARM = 1;
	public static final int DA_EXTRA_STATE_PHONE = 2;
	public static final int DA_EXTRA_STATE_CAMERA = 3;

	public static final String EXTRA_APPWIDGET_TYPE = "org.durka.hallmonitor.APPWIDGET_TYPE";

	private static CoreStateManager mStateManager;

	public CoreStateManager getStateManager() {
		if (mStateManager == null) {
			mStateManager = new CoreStateManager(this);
		}
		return mStateManager;
	}

	public void restart() {
		mStateManager.closeDefaultActivity();
		mStateManager.stopServices();
		mStateManager = new CoreStateManager(this);
		mStateManager.startServices();
	}
}
