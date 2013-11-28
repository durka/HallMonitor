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

import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.util.Log;


public class ViewCoverService extends Service implements SensorEventListener {
	
	private SensorManager       mSensorManager;
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d("VCS.onStartCommand", "View cover service started");
		
		if (Functions.Is.cover_closed(this)) {
			Functions.Actions.close_cover(this);
		} else {
			Functions.Actions.open_cover(this);
		}
		
		mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		
		mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY), SensorManager.SENSOR_DELAY_NORMAL);

		return START_STICKY;
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	@Override
	public void onDestroy() {
		Log.d("VCS.onStartCommand", "View cover service stopped");
		
		//unregisterReceiver(receiver);
		mSensorManager.unregisterListener(this);
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// I don't care
		Log.d("VCS.onAccuracyChanged", "OnAccuracyChanged: Sensor=" + sensor.getName() + ", accuracy=" + accuracy);
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		
		if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {	
			Log.d("VCS.onSensorChanged", "Proximity sensor changed, value=" + event.values[0]);
			Functions.Events.proximity(this, event.values[0]);
		}
	}

}
