package org.durka.sensorcat;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.os.Handler;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

public class Functions {

	public static class Actions {
		
		private static Handler handler = new Handler();
		
		public static void close_cover(Context ctx, PowerManager pm, KeyguardManager kgm, final DevicePolicyManager dpm) {
			Events.set_cover(true);
			
			// lock the screen, but keep it on for two seconds (but not if it was already off)
			if (pm.isScreenOn()) {
				
				// step 1: lock the screen (and turn it off, FIXME seemingly unavoidable side effect)
				if (!kgm.isKeyguardLocked()) {
					dpm.lockNow();
				}

				// step 2: turn the screen back on (with low brightness), for two seconds
				PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, ctx.getString(R.string.app_name));
				wl.acquire(2000);

				// step 3: after two seconds, if the cover is still closed, turn the screen off again
				handler.postDelayed(new Runnable() {
					@Override
					public void run() {
						dpm.lockNow();
					}
				}, 2000);

			}
		}
		
		public static void open_cover(Context ctx, PowerManager pm) {
			Events.set_cover(false);
			
			// step 1: if we were going to turn the screen off, cancel that
			handler.removeCallbacksAndMessages(null);
			
			// step 2: wake the screen
			PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, ctx.getString(R.string.app_name));
	        wl.acquire();
	        wl.release();
		}

	}

	public static class Events {
		
		private static boolean cover_closed;
		
		public static void device_admin_status(Context ctx, boolean admin) {
			Toast.makeText(ctx, ctx.getString(R.string.app_name)
					            + " admin status "
					            + (admin ? "granted" : "revoked")
					            + "!",
					       Toast.LENGTH_SHORT).show();
		}
		
		public static void set_cover(boolean closed) {
			cover_closed = closed;
		}
		
		public static void proximity(Context ctx, PowerManager pm, KeyguardManager kgm, final DevicePolicyManager dpm, float value) {
			if (value > 0) {
				if (cover_closed) {
					if (!Functions.Is.cover_closed(ctx)) {
						
						Actions.open_cover(ctx, pm);
					}
				}
			} else {
				if (!cover_closed) {
					if (Functions.Is.cover_closed(ctx)) {
						
						Actions.close_cover(ctx, pm, kgm, dpm);
					}
				}
			}
			Log.d(ctx.getString(R.string.app_name), String.format("cover_closed = %b", cover_closed));
		}
	}
	
	public static class Is {
		public static boolean cover_closed(Context ctx) {
			
			String status = "";
			try {
				Scanner sc = new Scanner(new File(ctx.getString(R.string.hall_file)));
				status = sc.nextLine();
				sc.close();
			} catch (FileNotFoundException e) {
				Log.e(ctx.getString(R.string.app_name), "Hall effect sensor device file not found!");
			}
			
			return status.equals("CLOSE");
		}
	}

}
