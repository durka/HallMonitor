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

import java.lang.Thread;

import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import com.manusfreedom.android.Events;
import com.manusfreedom.android.Events.InputDevice;
import eu.chainfire.libsuperuser.Shell;

public class ViewCoverHallService extends Service implements Runnable {
	
	private HeadsetReceiver		mHeadset;
	private Thread				getevent;
	private Boolean				serviceStarted;
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d("VCHS.onStartCommand", "View cover Hall service started");

        //We don't want to do this - almost by definition the cover can't be closed, and we don't actually want to do any open cover functionality
		//until the cover is closed and then opened again
		/*if (Functions.Is.cover_closed(this)) {
			Functions.Actions.close_cover(this);
		} else {
			Functions.Actions.open_cover(this);
		} */
		
		mHeadset = new HeadsetReceiver();
		IntentFilter intfil = new IntentFilter();
		intfil.addAction("android.intent.action.HEADSET_PLUG");
		registerReceiver(mHeadset, intfil);
		
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		if (prefs.getBoolean("pref_default_widget", false)
				&& !Functions.hmAppWidgetManager.doesWidgetExist("default")) {
			
			int id = prefs.getInt("default_widget_id", -1);
			if (id != -1) {
				Log.d("VCHS-oSC", "creating default widget with id=" + id);
				
				Intent data = new Intent();
				data.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
				
				Functions.hmAppWidgetManager.currentWidgetType = "default";
				Functions.hmAppWidgetManager.createWidget(data, this);
			}
		}
		if (prefs.getBoolean("pref_default_widget", false)
				&& !Functions.hmAppWidgetManager.doesWidgetExist("media")) {
			
			int id = prefs.getInt("media_widget_id", -1);
			if (id != -1) {
				Log.d("VCHS-oSC", "creating media widget with id=" + id);
				
				Intent data = new Intent();
				data.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
				
				Functions.hmAppWidgetManager.currentWidgetType = "media";
				Functions.hmAppWidgetManager.createWidget(data, this);
			}
		}
		serviceStarted = true;
		
		getevent = new Thread(this);
		getevent.start();

		return START_STICKY;
	}
	
	@Override
	public void run() {
		String neededDevice = "gpio-keys";
		Events events = new Events();
		Log.d("VCHS-oSC", "Request root");
		Shell.SU.available();
		
		events.AddAllDevices();
		String neededDevicePath = "";

		Log.e("VCHS-oSC", "Number of device found:" + events.m_Devs.size());

		Log.d("VCHS-oSC", "Scan device");
		for (InputDevice idev:events.m_Devs) {
			if(!idev.getOpen())
				idev.Open(true);
			if(idev.getOpen()) {
				Log.d("VCHS-oSC", " Open: " + idev.getPath() + " / Name: " + idev.getName() + " / Version: " + idev.getVersion() + " / Location: " + idev.getLocation() + " / IdStr: " + idev.getIdStr() + " / Result: " + idev.getOpen());
				if(idev.getName().equals(neededDevice)){
					Log.d("VCHS-oSC", "Device " + neededDevice + " found");
					neededDevicePath = idev.getPath();
					break;
				}
			}
		}
		events.Release();
		events = null;
		System.gc();
		
		events = new Events();
		events.AddDevice(neededDevicePath);

		Log.e("VCHS-oSC", "Number of device found:" + events.m_Devs.size());
		
		InputDevice currentInputDevice = null;
		for (InputDevice idev:events.m_Devs) {
			if(!idev.getOpen())
				idev.Open(true);
			currentInputDevice = idev;
			Log.d("VCHS-oSC", "Open: " + currentInputDevice.getPath() + " / Name: " + currentInputDevice.getName() + " / Version: " + currentInputDevice.getVersion() + " / Location: " + currentInputDevice.getLocation() + " / IdStr: " + currentInputDevice.getIdStr() + " / Result: " + currentInputDevice.getOpen());				
		}
		
		if(currentInputDevice == null)
		{
			Log.d("VCHS-oSC", "No device");
			return;
		}				

		Log.d("VCHS-oSC", "Start read command");
		while (serviceStarted) {
			if(currentInputDevice.getOpen() && (0 == currentInputDevice.getPollingEvent())) {
				Log.d("VCHS-oSC", "Reading command: " + currentInputDevice.getSuccessfulPollingType() + "/" + currentInputDevice.getSuccessfulPollingCode() + "/" + currentInputDevice.getSuccessfulPollingValue());
				if(currentInputDevice.getSuccessfulPollingCode() == 21 && currentInputDevice.getSuccessfulPollingValue() == 0){					
					Log.i("VCHS-oSC", "Cover closed");
					Functions.Actions.close_cover(this);
				}
				else if(currentInputDevice.getSuccessfulPollingCode() == 21 && currentInputDevice.getSuccessfulPollingValue() == 1){
					Log.i("VCHS-oSC", "Cover open");
					Functions.Actions.open_cover(this);
				}
			}
		}
		Log.d("VCHS-oSC", "Stop read command");
		events.Release();
		events = null;
		Log.d("VCHS-oSC", "Memory cleaned");
		System.gc();
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	@Override
	public void onDestroy() {
		Log.d("VCHS.onStartCommand", "View cover Hall service stopped");
		serviceStarted = false;
		unregisterReceiver(mHeadset);
		System.gc();
		
		super.onDestroy();
	}

}
