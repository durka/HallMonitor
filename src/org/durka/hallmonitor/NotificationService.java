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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.util.Log;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;


public class NotificationService extends NotificationListenerService {
	
	public static NotificationService that = null;
	
	private final List<String> blacklist = new ArrayList<String>() {{
			add("net.cactii.flash2"); // we have our own flashlight UI
			add("android");           // this covers the keyboard selection notification, but does it clobber others too? TODO
	}};
	
	@Override
	public void onCreate() {
		Log.d("NS-oC", "ohai");
		that = this;
	}
	
	@Override
	public void onDestroy() {
		Log.d("NS-oD", "kthxbai");
		that = null;
	}
	
	@Override
	public void onNotificationPosted(StatusBarNotification sbn) {
		Log.d("NS-oNP", "notification posted: " + sbn.toString());
		if (DefaultActivity.on_screen) {
			Functions.Actions.refresh_notifications();
		}
	}

	@Override
	public void onNotificationRemoved(StatusBarNotification sbn) {
		Log.d("NS-oNR", "notification removed: " + sbn.toString());
		if (DefaultActivity.on_screen) {
			Functions.Actions.refresh_notifications();
		}
	}
	
	@Override
	public StatusBarNotification[] getActiveNotifications() {
		StatusBarNotification[] notifs = super.getActiveNotifications();
		
		List<StatusBarNotification> acc = new ArrayList<StatusBarNotification>(notifs.length);
		for (StatusBarNotification sbn : notifs) {
			Log.d("NS-gAN", sbn.getPackageName());
			if (!blacklist.contains(sbn.getPackageName())) {
				acc.add(sbn);
			}
		}
		return acc.toArray(new StatusBarNotification[acc.size()]);
	}

}
