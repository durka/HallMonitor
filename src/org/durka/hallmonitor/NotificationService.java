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
import java.util.List;

import android.content.Intent;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

public class NotificationService extends NotificationListenerService {

	private final String LOG_TAG = "Hall.NS";
	public static NotificationService that = null;
	private LocalBroadcastManager mLocalBroadcastManager;

	@SuppressWarnings("serial")
	private final List<String> blacklist = new ArrayList<String>() {
		{
			add("net.cactii.flash2"); // we have our own flashlight UI
			add("android"); // this covers the keyboard selection notification,
							// but does it clobber others too? TODO
		}
	};

	@Override
	public void onCreate() {
		Log.d(LOG_TAG + ".oC", "ohai");
		that = this;
		mLocalBroadcastManager = LocalBroadcastManager.getInstance(this);
	}

	@Override
	public void onDestroy() {
		Log.d(LOG_TAG + ".oD", "kthxbai");
		that = null;
	}

	@Override
	public void onNotificationPosted(StatusBarNotification sbn) {
		Log.d(LOG_TAG + ".oNP", "notification posted: " + sbn.toString());
		Intent mIntent = new Intent(CoreApp.DA_ACTION_NOTIFICATION_REFRESH);
		mLocalBroadcastManager.sendBroadcastSync(mIntent);
	}

	@Override
	public void onNotificationRemoved(StatusBarNotification sbn) {
		Log.d(LOG_TAG + ".oNR", "notification removed: " + sbn.toString());
		Intent mIntent = new Intent(CoreApp.DA_ACTION_NOTIFICATION_REFRESH);
		mLocalBroadcastManager.sendBroadcastSync(mIntent);
	}

	@Override
	public StatusBarNotification[] getActiveNotifications() {
		StatusBarNotification[] notifs = super.getActiveNotifications();

		List<StatusBarNotification> acc = new ArrayList<StatusBarNotification>(
				notifs.length);
		for (StatusBarNotification sbn : notifs) {
			Log.d(LOG_TAG + ".gAN", sbn.getPackageName());
			if (!blacklist.contains(sbn.getPackageName())) {
				acc.add(sbn);
			}
		}
		return acc.toArray(new StatusBarNotification[acc.size()]);
	}

}
