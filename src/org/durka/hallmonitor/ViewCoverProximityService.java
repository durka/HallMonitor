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

import java.util.Timer;
import java.util.TimerTask;

import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

public class ViewCoverProximityService extends Service implements
		SensorEventListener {
	private final String LOG_TAG = "Hall.VCPS";

	private CoreStateManager mStateManager;
	private LocalBroadcastManager mLocalBroadcastManager;

	private SensorManager mSensorManager;

	@Override
	public void onCreate() {
		Log.d(LOG_TAG + ".oC", "Core service creating");

		mStateManager = ((CoreApp) getApplicationContext()).getStateManager();
		mLocalBroadcastManager = LocalBroadcastManager.getInstance(this);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d(LOG_TAG + ".oSC", "View cover Proximity service started");

		mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

		mSensorManager.registerListener(this,
				mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY),
				SensorManager.SENSOR_DELAY_NORMAL);

		return START_STICKY;
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onDestroy() {
		Log.d(LOG_TAG + ".oD", "View cover Proximity service stopped");

		mSensorManager.unregisterListener(this);

		super.onDestroy();
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// I don't care
		Log.d(LOG_TAG + ".oAC", "Sensor=" + sensor.getName() + ", accuracy="
				+ accuracy);
	}

	@Override
	public void onSensorChanged(SensorEvent event) {

		if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
			Log.d(LOG_TAG + ".onSC", "Proximity sensor changed, value="
					+ event.values[0]);
			proximity(event.values[0]);

			// improve reliability by refiring the event 200ms afterwards
			final float val = event.values[0];
			Timer timer = new Timer();
			timer.schedule(new TimerTask() {
				@Override
				public void run() {
					proximity(val);
				}
			}, 200);

			timer.schedule(new TimerTask() {
				@Override
				public void run() {
					proximity(val);
				}
			}, 500);

		}
	}

	/**
	 * Receives the value of the proximity sensor and reacts accordingly to
	 * update the cover state.
	 * 
	 * @param ctx
	 *            Application context.
	 * @param value
	 *            Value of the proximity sensor.
	 */
	private void proximity(float value) {

		Log.d(LOG_TAG + ".proximity",
				"Proximity method called with value: " + value
						+ " ,whilst cover_closed is: "
						+ mStateManager.getCoverClosed());

		if (value > 0) {
			if (!mStateManager.getCoverClosed(true)) {
				// proximity false (>0) and cover open - take open_cover
				// action
				Intent intent = new Intent(mStateManager.getActionCover());
				intent.putExtra(CoreReceiver.EXTRA_LID_STATE,
						CoreReceiver.LID_OPEN);
				this.mLocalBroadcastManager.sendBroadcastSync(intent);
			}
		} else {
			if (mStateManager.getCoverClosed(true)) {
				// proximity true (<=0) and cover closed - take
				// close_cover action
				Intent intent = new Intent(mStateManager.getActionCover());
				intent.putExtra(CoreReceiver.EXTRA_LID_STATE,
						CoreReceiver.LID_CLOSED);
				this.mLocalBroadcastManager.sendBroadcastSync(intent);
			}
		}
		// Log.d(LOG_TAG + ".Evt.proximity",
		// String.format("cover_closed = %b", cover_closed));
	}
}
