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

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

public class AdminReceiver extends DeviceAdminReceiver {
	private final String LOG_TAG = "Hall.AR";

	@Override
	public void onEnabled(Context context, Intent intent) {
		Log.d(LOG_TAG + ".status",
				"Device admin status called with admin status: " + true);

		Toast.makeText(context, context.getString(R.string.admin_granted),
				Toast.LENGTH_SHORT).show();
	}

	@Override
	public void onDisabled(Context context, Intent intent) {
		Log.d(LOG_TAG + ".status",
				"Device admin status called with admin status: " + false);

		Toast.makeText(context, context.getString(R.string.admin_revoked),
				Toast.LENGTH_SHORT).show();
	}
}
