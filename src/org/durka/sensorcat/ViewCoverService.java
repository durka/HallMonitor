package org.durka.sensorcat;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

import android.app.KeyguardManager;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.util.Log;


public class ViewCoverService extends Service implements SensorEventListener {
	
	static final String TAG = "ViewCoverService";
	
	private SensorManager       mSensorManager;
	private KeyguardManager     mKeyguardManager;
	private PowerManager        mPowerManager;
	private DevicePolicyManager mDevicePolicyManager;
	
	private final BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (action.equals(Intent.ACTION_SCREEN_ON)) {
				if (hall_is_closed()) {
					
					// if the screen is turned on with the cover closed, treat it as a close event
					// (mainly, turn off after 2 seconds instead of waiting the full timeout)
					
					coverClosed();
				}
			} 
		}
	};

	private boolean mClosed;
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d(TAG, "service started");
		
		mClosed = hall_is_closed();
		
		mSensorManager       = (SensorManager)       getSystemService(SENSOR_SERVICE);
		mKeyguardManager     = (KeyguardManager)     getSystemService(KEYGUARD_SERVICE);
		mPowerManager        = (PowerManager)        getSystemService(POWER_SERVICE);
		mDevicePolicyManager = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
		
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
		Log.d(TAG, "service stopped");
		
		unregisterReceiver(receiver);
		mSensorManager.unregisterListener(this);
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// I don't care
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		switch (event.sensor.getType()) {
		case Sensor.TYPE_PROXIMITY:
			if (event.values[0] > 0) {
				if (mClosed) {
					if (!hall_is_closed()) {
						
						coverOpened();
					}
				}
			} else {
				if (!mClosed) {
					if (hall_is_closed()) {
						
						coverClosed();
					}
				}
			}
			Log.d(TAG, String.format("mClosed = %b", mClosed));
			break;
		}
	}

	private boolean hall_is_closed() {
		
		String status = "";
		try {
			Scanner sc = new Scanner(new File(getString(R.string.hall_file)));
			status = sc.nextLine();
			sc.close();
		} catch (FileNotFoundException e) {
			Log.e(TAG, "Hall effect sensor device file not found!");
		}
		
		return status.equals("CLOSE");
	}
	
	private void coverOpened() {
		// step 1: bookkeeping
		mClosed  = false;
		
		// step 2: wake the screen
		PowerManager.WakeLock wl = mPowerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, TAG);
        wl.acquire();
        wl.release();
	}
	
	private void coverClosed() {
		// step 1: bookkeeping
		mClosed = true;
		
		// step 2: lock the screen, but keep it on for two seconds (but not if it was already off)
		if (mPowerManager.isScreenOn()) {
			
			// step 2a: lock the screen (and turn it off, FIXME seemingly unavoidable side effect)
			if (!mKeyguardManager.isKeyguardLocked()) {
				mDevicePolicyManager.lockNow();
			}

			// step 2b: turn the screen back on (with low brightness), for two seconds
			PowerManager.WakeLock wl = mPowerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, TAG);
			wl.acquire(2000);

			// step 2c: after two seconds, if the cover is still closed, turn the screen off again
			Handler h = new Handler();
			h.postDelayed(new Runnable() {
				@Override
				public void run() {
					if (mClosed) {
						mDevicePolicyManager.lockNow();
					}
				}
			}, 2000);

		}
	}

}
