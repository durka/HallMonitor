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

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Scanner;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.admin.DevicePolicyManager;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.WindowManager;
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
			
			Log.d("F.Act.close_cover", "Close cover event receieved.");
			
			//save the cover state
			Events.set_cover(true);
			
			//if we are running in root enabled mode then lets up the sensitivity on the view screen
			//so we can use the screen through the window
			 if (Functions.Events.rootEnabled) {
				 Log.d("F.Act.close_cover", "We're root enabled so lets boost the sensitivity...");
				 run_commands_as_root(new String[]{"cd /sys/class/sec/tsp", "echo clear_cover_mode,1 > cmd"});
				 Log.d("F.Act.close_cover", "...Sensitivity boosted, hold onto your hats!");
			 }
			
			//need this to let us lock the phone
			final DevicePolicyManager dpm = (DevicePolicyManager) ctx.getSystemService(Context.DEVICE_POLICY_SERVICE);
			//final PowerManager pm = (PowerManager)ctx.getSystemService(Context.POWER_SERVICE);
			
			ComponentName me = new ComponentName(ctx, AdminReceiver.class);
			if (!dpm.isAdminActive(me)) {
				// if we're not an admin, we can't do anything
				Log.d("F.Act.close_cover", "We are not an admin so cannot do anything.");
				return;
			}
			
		    // step 1: bring up the default activity window
			//we are using the show when locked flag as we'll re-use this method to show the screen on power button press
            ctx.startActivity(new Intent(ctx, DefaultActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_NO_ANIMATION
                            | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED));
            
            //step 2: wait for the delay period and turn the screen off
            int delay = PreferenceManager.getDefaultSharedPreferences(ctx).getInt("pref_delay", 2000);
            
            Log.d("F.Act.close_cover", "Delay set to: " + delay);
            
			handler.postDelayed(new Runnable() {
				@Override
				public void run() {	
					Log.d("F.Act.close_cover", "Locking screen now.");
					dpm.lockNow();
					//FIXME Would it be better to turn the screen off rather than actually locking
					//presumably then it will auto lock as per phone configuration
					//I can't work out how to do it though!
				}
			}, delay);
            
		}
		
		public static void open_cover(Context ctx) {
			
			Log.d("F.Act.open_cover", "Open cover event receieved.");
			
			//save the cover state
			Events.set_cover(false);
			
			//if we are running in root enabled mode then lets revert the sensitivity on the view screen
			//so we can use the device as normal
			 if (Functions.Events.rootEnabled) {
				 Log.d("F.Act.close_cover", "We're root enabled so lets revert the sensitivity...");
				 run_commands_as_root(new String[]{"cd /sys/class/sec/tsp", "echo clear_cover_mode,0 > cmd"});
				 Log.d("F.Act.close_cover", "...Sensitivity reverted, sanity is restored!");
			 }
			
			//needed to let us wake the screen
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
		
		
		/**
		 * Execute shell commands
		 * @param cmds Commands to execute
		 */
		public static void run_commands_as_root(String[] cmds) {
	        try {
	        	Process p = Runtime.getRuntime().exec("su");
	        	
	        	//create output stream for running commands
	            DataOutputStream os = new DataOutputStream(p.getOutputStream());  
	            
	            //tap into the output
	            InputStream is = p.getInputStream();
	            BufferedReader isBr = new BufferedReader(new InputStreamReader(is));
	            
	            //tap into the error output
	            InputStream es = p.getErrorStream();
	            BufferedReader esBr = new BufferedReader(new InputStreamReader(es));
	            
	            //use this for collating the output
	            String currentLine;
	            
	            //run commands
	            for (String tmpCmd : cmds) {
	            	Log.d("F.Act.run_comm_as_root", "Running command: " + tmpCmd);
                    os.writeBytes(tmpCmd+"\n");
	            }      
	            os.writeBytes("exit\n");  
	            os.flush();
	            
	            //log out the output
	            String output = "";
	            while ((currentLine = isBr.readLine()) != null) {
	              output += currentLine + "\n";
	            } 
	            Log.d("F.Act.run_comm_as_root", "Have output: " + output);
           
	            //log out the error output
	            String error = "";
	            currentLine = "";
	            while ((currentLine = esBr.readLine()) != null) {
	              error += currentLine + "\n";
	            }	           
	            Log.d("F.Act.run_comm_as_root", "Have error: " + error);

	        } catch (IOException ioe) {
	        	Log.e("F.Act.run_comm_as_root","Failed to run command!", ioe);
	        }
		}
		
		

	}

	public static class Events {
		
		private static boolean cover_closed;
		public static boolean rootEnabled = false;
		
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
			
			boolean widgetEnabled = Functions.hmAppWidgetManager.doesWidgetExist("default");
			
			Log.d("F.Is.def_wid_enabled","Default widget enabled state is: " + widgetEnabled);
			
			return widgetEnabled;
		}
		
		
	}

}
