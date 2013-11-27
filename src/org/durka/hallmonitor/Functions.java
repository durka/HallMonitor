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

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

public class Functions {
	
	public static final int DEVICE_ADMIN_WAITING = 42;
	
	//needed for the call backs from the widget picker
	//pick is the call back after picking a widget, configure is the call back after
	//widget configuration
	public static final int REQUEST_PICK_APPWIDGET = 9;
	public static final int REQUEST_CONFIGURE_APPWIDGET = 5;
	
	//Class that handles interaction with 3rd party App Widgets
	public static final HMAppWidgetManager hmAppWidgetManager = new HMAppWidgetManager();
	
	public static class Actions {
		
		private static Handler handler = new Handler();
		
		public static void close_cover(Context ctx) {
			
			Log.d("nw", "COVER CLOSE EVENT");
			
			Events.set_cover(true);
			
			KeyguardManager           kgm = (KeyguardManager)     ctx.getSystemService(Context.KEYGUARD_SERVICE);
			PowerManager              pm  = (PowerManager)        ctx.getSystemService(Context.POWER_SERVICE);
			final DevicePolicyManager dpm = (DevicePolicyManager) ctx.getSystemService(Context.DEVICE_POLICY_SERVICE);
			
			ComponentName me = new ComponentName(ctx, AdminReceiver.class);
			if (!dpm.isAdminActive(me)) {
				// if we're not an admin, we can't do anything
				return;
			}
			
		    // step 1: bring up the default activity window
            ctx.startActivity(new Intent(ctx, DefaultActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_NO_ANIMATION));
		    
		    
			// lock the screen, but keep it on for two seconds (but not if it was already off)
			if (pm.isScreenOn()) {
				
				/*// step 1: lock the screen (and turn it off, FIXME seemingly unavoidable side effect)
				if (!kgm.isKeyguardLocked()) {
					dpm.lockNow();
				}*/

				// are we supposed to show the lock screen?
				if (kgm.isKeyguardSecure()) {
					int delay = PreferenceManager.getDefaultSharedPreferences(ctx).getInt("pref_delay", 0);
					if (delay > 0) {
					/*
						// step 2: turn the screen back on (optionally with low brightness)
						int flags = PowerManager.ACQUIRE_CAUSES_WAKEUP;
						if (PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean("pref_dim", false)) {
							flags |= PowerManager.SCREEN_DIM_WAKE_LOCK;
						} else {
							flags |= PowerManager.SCREEN_BRIGHT_WAKE_LOCK;
						}
						PowerManager.WakeLock wl = pm.newWakeLock(flags, ctx.getString(R.string.app_name));
						wl.acquire(delay);
						*/
					
						// step 3: after the delay, if the cover is still closed, turn the screen off again
						handler.postDelayed(new Runnable() {
							@Override
							public void run() {	
								dpm.lockNow();	
							}
						}, delay);
					
					}
				}
			}
		}
		
		public static void open_cover(Context ctx) {
			Events.set_cover(false);
			
			PowerManager pm  = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
			
			// step 1: if we were going to turn the screen off, cancel that
			handler.removeCallbacksAndMessages(null);
			
			// step 2: wake the screen
			PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, ctx.getString(R.string.app_name));
	        wl.acquire();
	        wl.release();
		}

		public static void start_service(Activity act) {
			// Become device admin
			DevicePolicyManager dpm = (DevicePolicyManager) act.getSystemService(Context.DEVICE_POLICY_SERVICE);
			ComponentName me = new ComponentName(act, AdminReceiver.class);
			if (!dpm.isAdminActive(me)) {
				Intent coup = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
				coup.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, me);
				coup.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, act.getString(R.string.admin_excuse));
				act.startActivityForResult(coup, DEVICE_ADMIN_WAITING);
			}
		}
		
		public static void stop_service(Context ctx) {
			ctx.stopService(new Intent(ctx, ViewCoverService.class));
			
			// Relinquish device admin
			DevicePolicyManager dpm = (DevicePolicyManager) ctx.getSystemService(Context.DEVICE_POLICY_SERVICE);
			ComponentName me = new ComponentName(ctx, AdminReceiver.class);
			if (dpm.isAdminActive(me)) dpm.removeActiveAdmin(me);
		}
		
		
		/**
		 * Hand off to the HMAppWidgetManager to deal with
		 * @param act The Activity to use as the context for these actions
		 * @param widgetType The type of widget (e.g. 'default', 'media', 'notification' etc.)
		 */
		public static void register_widget(Activity act, String widgetType) {
			//hand off to the HM App Widget Manager for processing
			hmAppWidgetManager.register_widget(act, widgetType);
		}
		
		/**
		 * Hand off to the HMAppWidgetManager to deal with
		 * @param act The Activity to use as the context for these actions
		 * @param widgetType The type of widget (e.g. 'default', 'media', 'notification' etc.)
		 */
		public static void unregister_widget(Activity act, String widgetType) {
			//hand off to the HM App Widget Manager for processing
			hmAppWidgetManager.unregister_widget(act, widgetType);
		}
		
		

	}

	public static class Events {
		
		private static boolean cover_closed;
		
		public static void boot(Context ctx) {
			if (PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean("pref_enabled", false)) {
	    		Intent startServiceIntent = new Intent(ctx, ViewCoverService.class);
	    		ctx.startService(startServiceIntent);
	    	}
		}
		
		public static void activity_result(Context ctx, int request, int result, Intent data) {
			Log.d("F-a_r", "request=" + Integer.toString(request) + ", result=" + Integer.toString(result));
			switch (request) {
			case DEVICE_ADMIN_WAITING:
				if (result == Activity.RESULT_OK) {
					// we asked to be an admin and the user clicked Activate
					// (the intent receiver takes care of showing a toast)
					// go ahead and start the service
					ctx.startService(new Intent(ctx, ViewCoverService.class));
				} else {
					// we asked to be an admin and the user clicked Cancel (why?)
					// complain, and un-check pref_enabled
					Toast.makeText(ctx, ctx.getString(R.string.admin_refused), Toast.LENGTH_SHORT).show();
					Log.d("F-a_r", "pref_enabled = " + Boolean.toString(PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean("pref_enabled", true)));
					PreferenceManager.getDefaultSharedPreferences(ctx)
							.edit()
							.putBoolean("pref_enabled", false)
							.commit();
					Log.d("F-a_r", "pref_enabled = " + Boolean.toString(PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean("pref_enabled", true)));
				}
				break;

			case REQUEST_PICK_APPWIDGET:
				//widget picked
				if (result == Activity.RESULT_OK) {
					//widget chosen so launch configurator
					hmAppWidgetManager.configureWidget(data, ctx);
				} else {
					//choose dialog cancelled so clean up
					int appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
			        if (appWidgetId != -1) {
			        	hmAppWidgetManager.deleteAppWidgetId(appWidgetId);
					}
				}
				break;		
			case REQUEST_CONFIGURE_APPWIDGET:
				//widget configured
				if (result == Activity.RESULT_OK) {
					//widget configured successfully so create it
					hmAppWidgetManager.createWidget(data, ctx);
				} else {
					//configure dialog cancelled so clean up
					int appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
			        if (appWidgetId != -1) {
			        	hmAppWidgetManager.deleteAppWidgetId(appWidgetId);
					}
				break;			
			    }
				
			}
		}
			
			
			

			
			
			
			
			
				
			
			
		
		public static void device_admin_status(Context ctx, boolean admin) {
			Toast.makeText(ctx, ctx.getString(admin ? R.string.admin_granted : R.string.admin_revoked), Toast.LENGTH_SHORT).show();
			
			if (admin) {
				if (PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean("pref_enabled", false) && Is.cover_closed(ctx)) {
					Actions.close_cover(ctx);
				}
			} else {
				Actions.stop_service(ctx);
				PreferenceManager.getDefaultSharedPreferences(ctx)
						.edit()
						.putBoolean("pref_enabled", false)
						.commit();
			}
			
			
		}
		
		public static void set_cover(boolean closed) {
			cover_closed = closed;
		}
		
		public static void proximity(Context ctx, float value) {
			if (value > 0) {
				if (cover_closed) {
					if (!Functions.Is.cover_closed(ctx)) {
						
						Actions.open_cover(ctx);
					}
				}
			} else {
				if (!cover_closed) {
					if (Functions.Is.cover_closed(ctx)) {
						
						Actions.close_cover(ctx);
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
		
		public static boolean service_running(Context ctx) {
			
			ActivityManager manager = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
			for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
				if (ViewCoverService.class.getName().equals(service.service.getClassName())) {
					// the service is running
					return true;
				}
			}
			// the service must not be running
			return false;
		}
		
		
		/**
		 * Is the default widget enabled
		 * @param ctx Application context
		 * @return True if it is, False if not
		 */
		public static boolean default_widget_enabled(Context ctx) {
			return (Functions.hmAppWidgetManager.getAppWidgetHostViewByType("default") != null);
		}
		
		
	}

}
