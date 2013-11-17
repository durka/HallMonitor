package org.durka.sensorcat;

import android.app.KeyguardManager;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;


public class ViewCoverService extends Service implements SensorEventListener {
	
	private SensorManager       mSensorManager;
	
	private final BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
				if (Functions.Is.cover_closed(context)) {
					
					// if the screen is turned on with the cover closed, treat it as a close event
					// (mainly, turn off after 2 seconds instead of waiting the full timeout)
					
					Functions.Actions.close_cover(context);
				}
			} 
		}
	};
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d(getString(R.string.app_name), "service started");
		
		Functions.Events.set_cover(Functions.Is.cover_closed(this));
		
		mSensorManager       = (SensorManager)       getSystemService(SENSOR_SERVICE);
		
		mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY), SensorManager.SENSOR_DELAY_NORMAL);
		
		IntentFilter filter = new IntentFilter();
		filter.addAction(Intent.ACTION_SCREEN_ON);
		registerReceiver(receiver, filter);
		
		return START_STICKY;
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	@Override
	public void onDestroy() {
		Log.d(getString(R.string.app_name), "service stopped");
		
		unregisterReceiver(receiver);
		mSensorManager.unregisterListener(this);
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// I don't care
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		
		if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {	
			Functions.Events.proximity(this, event.values[0]);
		}
	}

}
