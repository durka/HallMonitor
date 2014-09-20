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
import android.os.Build;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.manusfreedom.android.Events;
import com.manusfreedom.android.Events.InputDevice;

public class ViewCoverHallService extends Service implements Runnable {
	private final String LOG_TAG = "Hall.VCHS";

	private Thread getevent;
	private Boolean serviceStarted;

	private static final String DEV_SERRANO_LTE_CM10 = "serranolte"; // GT-I9195
																		// CM10.x
	private static final String DEV_SERRANO_LTE_CM11 = "serranoltexx"; // GT-I9195
																		// CM11.x

	private CoreStateManager mStateManager;
	private LocalBroadcastManager mLocalBroadcastManager;

	@Override
	public void onCreate() {
		Log.d(LOG_TAG + ".oC", "Core service creating");

		mStateManager = ((CoreApp) getApplicationContext()).getStateManager();
		mLocalBroadcastManager = LocalBroadcastManager.getInstance(this);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d(LOG_TAG + ".oSC", "View cover Hall service started");

		serviceStarted = true;

		getevent = new Thread(this);
		getevent.start();

		return START_STICKY;
	}

	@Override
	public void run() {
		if (!mStateManager.getRootApp()) {
			Log.e(LOG_TAG + ".r", "Required root to use Real Hall service");

			return;
		}

		String neededDevice = "gpio-keys";
		if (Build.DEVICE.equals(DEV_SERRANO_LTE_CM10)
				|| Build.DEVICE.equals(DEV_SERRANO_LTE_CM11)) {
			neededDevice = "sec_keys";
		}
		Events events = new Events();

		events.AddAllDevices();
		String neededDevicePath = "";

		Log.d(LOG_TAG + ".r", "Number of device found:" + events.m_Devs.size());

		Log.d(LOG_TAG + ".r", "Scan device");
		for (InputDevice idev : events.m_Devs) {
			if (!idev.getOpen()) {
				idev.Open(true, mStateManager.getRootApp());
			}
			if (idev.getOpen()) {
				Log.d(LOG_TAG + ".r", " Open: " + idev.getPath() + " / Name: "
						+ idev.getName() + " / Version: " + idev.getVersion()
						+ " / Location: " + idev.getLocation() + " / IdStr: "
						+ idev.getIdStr() + " / Result: " + idev.getOpen());
				if (idev.getName().equals(neededDevice)) {
					Log.d(LOG_TAG + ".r", "Device " + neededDevice + " found");
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

		Log.e(LOG_TAG + ".r", "Number of device found:" + events.m_Devs.size());

		InputDevice currentInputDevice = null;
		for (InputDevice idev : events.m_Devs) {
			if (!idev.getOpen()) {
				idev.Open(true, mStateManager.getRootApp());
			}
			currentInputDevice = idev;
			Log.d(LOG_TAG + ".r", "Open: " + currentInputDevice.getPath()
					+ " / Name: " + currentInputDevice.getName()
					+ " / Version: " + currentInputDevice.getVersion()
					+ " / Location: " + currentInputDevice.getLocation()
					+ " / IdStr: " + currentInputDevice.getIdStr()
					+ " / Result: " + currentInputDevice.getOpen());
		}

		if (currentInputDevice == null) {
			Log.d(LOG_TAG + ".r", "No device");
			return;
		}

		Log.d(LOG_TAG + ".r", "Start read command");
		while (serviceStarted) {
			if (currentInputDevice.getOpen()
					&& (0 == currentInputDevice.getPollingEvent())) {
				Log.d(LOG_TAG + ".r",
						"Reading command: "
								+ currentInputDevice.getSuccessfulPollingType()
								+ "/"
								+ currentInputDevice.getSuccessfulPollingCode()
								+ "/"
								+ currentInputDevice
										.getSuccessfulPollingValue());
				if (currentInputDevice.getSuccessfulPollingCode() == 21
						&& currentInputDevice.getSuccessfulPollingValue() == 0) {
					Log.i(LOG_TAG + ".r", "Cover closed");
					Intent intent = new Intent(mStateManager.getActionCover());
					intent.putExtra(CoreReceiver.EXTRA_LID_STATE,
							CoreReceiver.LID_CLOSED);
					this.mLocalBroadcastManager.sendBroadcastSync(intent);
				} else if (currentInputDevice.getSuccessfulPollingCode() == 21
						&& currentInputDevice.getSuccessfulPollingValue() == 1) {
					Log.i(LOG_TAG + ".r", "Cover open");
					Intent intent = new Intent(mStateManager.getActionCover());
					intent.putExtra(CoreReceiver.EXTRA_LID_STATE,
							CoreReceiver.LID_OPEN);
					this.mLocalBroadcastManager.sendBroadcastSync(intent);
				}
			}
		}
		Log.d(LOG_TAG + ".r", "Stop read command");
		events.Release();
		events = null;
		Log.d(LOG_TAG + ".r", "Memory cleaned");
		System.gc();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onDestroy() {
		Log.d(LOG_TAG + ".oD", "View cover Hall service stopped");
		serviceStarted = false;
		System.gc();

		super.onDestroy();
	}

}
