package org.durka.sensorcat;

import java.io.IOException;
import java.util.LinkedList;

import android.hardware.SensorEventListener;
import android.os.UEventObserver;
import android.util.Log;

/* Note that Eclipse thinks this extends the src/android/os/UEventObserver.java class in the current project
 * but at runtime the real (hidden) android.os.UEventObserver is automatically used instead
 */
public class HallMonitor extends UEventObserver {
	
	private static final String TAG = "HallMonitor";
	
	private static HallMonitor me;
	private LinkedList<SensorEventListener> mListeners;
	
	// call get()
	private HallMonitor() {
		mListeners = new LinkedList<SensorEventListener>();
		
		try {
			Runtime.getRuntime().exec("su");
		} catch (IOException e) {
			Log.e(TAG, "root required");
		}
	}
	
	public static synchronized HallMonitor get()
	{
		if (me == null) {
			me = new HallMonitor();
		}
		
		return me;
	}

	public void register(SensorEventListener listener) {
		if (mListeners.isEmpty()) {
			startObserving("SUBSYSTEM");
		}
		
		mListeners.add(listener);
	}
	
	public void unregister(SensorEventListener listener) {
		mListeners.remove(listener);
		
		if (mListeners.isEmpty()) {
			stopObserving();
		}
	}

	@Override
	public void onUEvent(UEvent event) {
		Log.d(TAG, "received event " + event.toString());
	}
	
}
