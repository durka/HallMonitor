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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.admin.DevicePolicyManager;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.os.UserHandle;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import eu.chainfire.libsuperuser.Shell;

public class CoreStateManager {
	private final String LOG_TAG = "Hall.CSM";

	private static Context mAppContext;
	private static boolean init = false;

	// All we need for alternative torch
	private Camera camera;
	// private final boolean flashIsOn = false;
	private boolean deviceHasFlash;

	// Class that handles interaction with 3rd party App Widgets
	public HMAppWidgetManager hmAppWidgetManager;

	private DefaultActivity defaultActivity;
	private Configuration configurationActivity;

	private final SharedPreferences preference_all;

	private final boolean systemApp;
	private boolean adminApp;
	private boolean rootApp;
	private boolean osPowerManagement;
	private boolean internalPowerManagement;
	private final boolean hardwareAccelerated;

	// audio manager to detect media state
	private AudioManager audioManager;

	private boolean lockMode;

	private boolean notification_settings_ongoing = false;
	private boolean widget_settings_ongoing = false;

	// states for alarm and phone
	private boolean alarm_firing = false;
	private boolean phone_ringing = false;
	private boolean torch_on = false;
	private boolean camera_up = false;
	private String call_from = "";
	private boolean cover_closed = false;
	private boolean forceCheckCoverState = false;

	private boolean mainLaunched = false;
	private boolean defaultActivityStarting = false;

	private CoreReceiver mCoreReceiver;
	private CoreService mCoreService;
	private Method startCoreServiceAsUser;

	private static long blackscreen_time = 0;

	private String actionCover = CoreReceiver.ACTION_LID_STATE_CHANGED;

	private final PowerManager mPowerManager;
	private final WakeLock daPartialWakeLock;
	private final WakeLock globalPartialWakeLock;
	private int globalPartialWakeLockCount;

	private static AtomicInteger idCounter = new AtomicInteger();

	CoreStateManager(Context context) {
		mAppContext = context;
		mPowerManager = (PowerManager) mAppContext
				.getSystemService(Context.POWER_SERVICE);
		daPartialWakeLock = mPowerManager.newWakeLock(
				PowerManager.PARTIAL_WAKE_LOCK, "CoreStateManager");
		daPartialWakeLock.setReferenceCounted(false);
		globalPartialWakeLock = mPowerManager.newWakeLock(
				PowerManager.PARTIAL_WAKE_LOCK, "CoreReceiver");
		globalPartialWakeLock.setReferenceCounted(true);

		preference_all = PreferenceManager
				.getDefaultSharedPreferences(mAppContext);

		// Enable access to sleep mode
		systemApp = (mAppContext.getApplicationInfo().flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
		if (systemApp) {
			Log.d(LOG_TAG, "We are a system app.");
		} else {
			Log.d(LOG_TAG, "We are not a system app.");
			preference_all.edit()
					.putBoolean("pref_internal_power_management", false)
					.commit();
		}

		refreshAdminApp();
		refreshRootApp();

		refreshLockMode();
		refreshOsPowerManagement();
		refreshInternalPowerManagement();

		refreshInternalService();

		if (preference_all.getBoolean("pref_proximity", false)) {
			forceCheckCoverState = true;
		}

		hmAppWidgetManager = new HMAppWidgetManager(this);

		if (preference_all.getBoolean("pref_default_widget", false)) {
			int widgetId = preference_all.getInt("default_widget_id", -1);
			if (widgetId == -1) {
				registerWidget("default");
			} else {
				createWidget("default");
			}
		}

		if (preference_all.getBoolean("pref_media_widget", false)) {
			audioManager = (AudioManager) mAppContext
					.getSystemService(Context.AUDIO_SERVICE);

			int widgetId = preference_all.getInt("media_widget_id", -1);
			if (widgetId == -1) {
				registerWidget("media");
			} else {
				createWidget("media");
			}
		}

		this.hardwareAccelerated = preference_all.getBoolean(
				"pref_hardwareAccelerated", false);

		// we might have missed a phone-state revelation
		phone_ringing = ((TelephonyManager) mAppContext
				.getSystemService(Context.TELEPHONY_SERVICE)).getCallState() == TelephonyManager.CALL_STATE_RINGING;
		// we might have missed an alarm alert
		// TODO: find a way
		// alarm_firing =
		// ((TelephonyManager)
		// mAppContext.getSystemService(Context.TELEPHONY_SERVICE)).getCallState()
		// == TelephonyManager.CALL_STATE_RINGING;
		Intent stateIntent = mAppContext.registerReceiver(null,
				new IntentFilter(CoreReceiver.TORCH_STATE_CHANGED));
		torch_on = stateIntent != null
				&& stateIntent.getIntExtra("state", 0) != 0;

		init = true;
	}

	public static boolean getInitialized() {
		return init;
	}

	public static Context getContext() {
		return mAppContext;
	}

	public boolean getPhoneRinging() {
		return phone_ringing;
	}

	public void setPhoneRinging(boolean enable) {
		phone_ringing = enable;
	}

	public boolean getAlarmFiring() {
		return alarm_firing;
	}

	public void setAlarmFiring(boolean enable) {
		alarm_firing = enable;
	}

	public boolean getDefaultActivityStarting() {
		return defaultActivityStarting;
	}

	public void setDefaultActivityStarting(boolean enable) {
		defaultActivityStarting = enable;
	}

	public boolean getTorchOn() {
		return torch_on;
	}

	public void setTorchOn(boolean enable) {
		torch_on = enable;
	}

	public boolean getCameraUp() {
		return camera_up;
	}

	public void setCameraUp(boolean enable) {
		camera_up = enable;
	}

	public boolean getCoverClosed() {
		return getCoverClosed(forceCheckCoverState);
	}

	public boolean getCoverClosed(boolean forceCheck) {
		if (forceCheck) {
			String status = "";
			try {
				Scanner sc = new Scanner(new File(
						mAppContext.getString(R.string.hall_file)));
				status = sc.nextLine();
				sc.close();
			} catch (FileNotFoundException e) {
				Log.e(mAppContext.getString(R.string.app_name),
						"Hall effect sensor device file not found!");
			}
			boolean isClosed = (status.compareTo("CLOSE") == 0);
			Log.d(LOG_TAG, "Cover closed state is: " + isClosed);
			return isClosed;
		} else {
			Log.d(LOG_TAG, "Cover closed state is: " + cover_closed);
			return cover_closed;
		}
	}

	public void setCoverClosed(boolean enable) {
		cover_closed = enable;
	}

	public boolean getMainLaunched() {
		return mainLaunched;
	}

	public void setMainLaunched(boolean enable) {
		mainLaunched = enable;
	}

	public boolean getOsPowerManagement() {
		return osPowerManagement;
	}

	public void refreshOsPowerManagement() {
		osPowerManagement = preference_all.getBoolean(
				"pref_os_power_management", false);
	}

	public boolean getInternalPowerManagement() {
		return internalPowerManagement;
	}

	public void refreshInternalPowerManagement() {
		internalPowerManagement = preference_all.getBoolean(
				"pref_internal_power_management", false);
	}

	public PowerManager getPowerManager() {
		return mPowerManager;
	}

	public String getActionCover() {
		return actionCover;
	}

	public void setActionCover(String mString) {
		actionCover = mString;
	}

	public boolean getWidgetSettingsOngoing() {
		return widget_settings_ongoing;
	}

	public void setWidgetSettingsOngoing(boolean enable) {
		widget_settings_ongoing = enable;
	}

	public boolean getNotificationSettingsOngoing() {
		return notification_settings_ongoing;
	}

	public void setNotificationSettingsOngoing(boolean enable) {
		notification_settings_ongoing = enable;
	}

	public boolean getSystemApp() {
		return systemApp;
	}

	public boolean getAdminApp() {
		return adminApp;
	}

	public void refreshAdminApp() {
		final DevicePolicyManager dpm = (DevicePolicyManager) mAppContext
				.getSystemService(Context.DEVICE_POLICY_SERVICE);
		ComponentName me = new ComponentName(mAppContext, AdminReceiver.class);
		adminApp = dpm.isAdminActive(me);
		if (adminApp) {
			Log.d(LOG_TAG, "We are an admin.");
		} else {
			Log.d(LOG_TAG, "We are not an admin so cannot do anything.");
		}
		refreshLockMode();
	}

	public boolean getRootApp() {
		return rootApp;
	}

	public void refreshRootApp() {
		if (preference_all.getBoolean("pref_runasroot", false)) {
			AsyncSuAvailable localSuAvailable = new AsyncSuAvailable();
			boolean rootAppResult = false;
			try {
				rootAppResult = localSuAvailable.execute().get();
			} catch (InterruptedException e) {
			} catch (ExecutionException e) {
			}
			rootApp = rootAppResult;
		} else {
			rootApp = false;
			preference_all.edit().putBoolean("pref_runasroot", false).commit();
		}
		if (rootApp) {
			Log.d(LOG_TAG, "We are root.");
		} else {
			Log.d(LOG_TAG, "We are not root.");
		}
	}

	public void refreshInternalService() {
		if (preference_all.getBoolean("pref_internalservice", false)) {
			actionCover = CoreReceiver.ACTION_INTERNAL_LID_STATE_CHANGED;
		} else {
			actionCover = CoreReceiver.ACTION_LID_STATE_CHANGED;
		}
		restartServices();
	}

	public boolean getLockMode() {
		return lockMode;
	}

	public void refreshLockMode() {
		if (preference_all.getBoolean("pref_lockmode", false) && adminApp) {
			lockMode = true;
		} else {
			lockMode = false;
			preference_all.edit().putBoolean("pref_lockmode", false).commit();
		}
	}

	public long getBlackScreenTime() {
		return blackscreen_time;
	}

	public void setBlackScreenTime(long time) {
		blackscreen_time = time;
	}

	public String getCallFrom() {
		return call_from;
	}

	public void setCallFrom(String num) {
		call_from = num;
	}

	public SharedPreferences getPreference() {
		return preference_all;
	}

	public void registerCoreReceiver() {
		if (mCoreReceiver == null) {
			/*
			 * HEADSET_PLUG, SCREEN_ON and SCREEN_OFF only available through
			 * registerReceiver function
			 */
			mCoreReceiver = new CoreReceiver();
			IntentFilter intfil = new IntentFilter();
			intfil.setPriority(990);
			intfil.addAction(Intent.ACTION_HEADSET_PLUG);
			intfil.addAction(Intent.ACTION_SCREEN_ON);
			intfil.addAction(Intent.ACTION_SCREEN_OFF);

			if (preference_all.getBoolean("pref_internalservice", false)) {
				IntentFilter mIntentFilter = new IntentFilter();
				mIntentFilter.addAction(getActionCover());
				LocalBroadcastManager.getInstance(mAppContext)
						.registerReceiver(mCoreReceiver, mIntentFilter);
			} else {
				intfil.addAction(CoreReceiver.ACTION_LID_STATE_CHANGED);
			}

			mAppContext.registerReceiver(mCoreReceiver, intfil);
		}
	}

	public void unregisterCoreReceiver() {
		if (mCoreReceiver != null) {
			mAppContext.unregisterReceiver(mCoreReceiver);
			mCoreReceiver = null;
		}
	}

	public void registerCoreService(CoreService mService) {
		mCoreService = mService;
		try {
			startCoreServiceAsUser = ((ContextWrapper) mCoreService).getClass()
					.getMethod("startServiceAsUser", Intent.class,
							UserHandle.class);
			Log.d(LOG_TAG, "CoreService registred");
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		}
	}

	public void unregisterCoreService() {
		mCoreService = null;
		startCoreServiceAsUser = null;
		Log.d(LOG_TAG, "CoreService unregistred");
	}

	public synchronized boolean setDefaultActivity(
			DefaultActivity activityInstance) {
		if (defaultActivity == null) {
			defaultActivity = activityInstance;
			return true;
		} else if (activityInstance == null) {
			defaultActivity = null;
			return true;
		} else {
			Log.w(LOG_TAG, "Warning already default activity set!!!!");
			return false;
		}
	}

	public synchronized boolean setConfigurationActivity(
			Configuration activityInstance) {
		if (configurationActivity == null) {
			configurationActivity = activityInstance;
			return true;
		} else if (activityInstance == null) {
			configurationActivity = null;
			return true;
		} else {
			Log.w(LOG_TAG, "Warning already configuration activity set!!!!");
			return false;
		}
	}

	public synchronized DefaultActivity getDefaultActivity() {
		return defaultActivity;
	}

	public synchronized Configuration getConfigurationActivity() {
		return configurationActivity;
	}

	public boolean getDefaultActivityRunning() {
		try {
			ActivityInfo[] list = mAppContext.getPackageManager()
					.getPackageInfo(mAppContext.getPackageName(),
							PackageManager.GET_ACTIVITIES).activities;
			for (int i = 0; i < list.length; i++) {
				if (list[i].name == "org.durka.hallmonitor.DefaultActivity") {
					return true;
				}
			}
		} catch (NameNotFoundException e1) {
		}
		return false;
	}

	public boolean getConfigurationActivityRunning() {
		try {
			ActivityInfo[] list = mAppContext.getPackageManager()
					.getPackageInfo(mAppContext.getPackageName(),
							PackageManager.GET_ACTIVITIES).activities;
			for (int i = 0; i < list.length; i++) {
				if (list[i].name == "org.durka.hallmonitor.DefaultActivity") {
					return true;
				}
			}
		} catch (NameNotFoundException e1) {
		}
		return false;
	}

	public void closeDefaultActivity() {
		Log.w(LOG_TAG, "Send close default activity");
		setDefaultActivityStarting(false);
		Intent finishDAIntent = new Intent(CoreApp.DA_ACTION_FINISH);
		LocalBroadcastManager.getInstance(mAppContext).sendBroadcastSync(
				finishDAIntent);
	}

	public void closeConfigurationActivity() {
		if (configurationActivity != null) {
			configurationActivity.finish();
		}
	}

	public void closeAllActivity() {
		Log.w(LOG_TAG, "Try close all activity");
		closeDefaultActivity();
		closeConfigurationActivity();
	}

	public void freeDevice() {
		setBlackScreenTime(0);
		closeAllActivity();
	}

	public AudioManager getAudioManager() {
		return audioManager;
	}

	public void createWidget(String widgetType) {
		int widgetId = preference_all.getInt(widgetType + "_widget_id", -1);
		if (widgetId != -1) {
			if (!hmAppWidgetManager.doesWidgetExist(widgetType)) {
				Log.d(LOG_TAG, "creating " + widgetType + " widget with id="
						+ widgetId);
				Intent data = new Intent();
				data.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);

				hmAppWidgetManager.createWidget(widgetType, data);
			}
		}
	}

	/**
	 * Hand off to the HMAppWidgetManager to deal with registering new app
	 * widget.
	 * 
	 * @param act
	 *            The Activity to use as the context for these actions
	 * @param widgetType
	 *            The type of widget (e.g. 'default', 'media', 'notification'
	 *            etc.)
	 */
	public void registerWidget(String widgetType) {

		Log.d(LOG_TAG, "Register widget called for type: " + widgetType);
		// hand off to the HM App Widget Manager for processing
		if (widget_settings_ongoing) {
			Log.d(LOG_TAG, "skipping, already inflight");
		} else {
			hmAppWidgetManager.registerWidget(widgetType);
		}
	}

	/**
	 * Hand off to the HMAppWidgetManager to deal with unregistering existing
	 * app widget.
	 * 
	 * @param act
	 *            The Activity to use as the context for these actions
	 * @param widgetType
	 *            The type of widget (e.g. 'default', 'media', 'notification'
	 *            etc.)
	 */
	public void unregisterWidget(String widgetType) {

		Log.d(LOG_TAG, "unregister widget called for type: " + widgetType);
		// hand off to the HM App Widget Manager for processing
		hmAppWidgetManager.unregisterWidget(widgetType);
	}

	public HMAppWidgetManager getHMAppWidgetManager() {
		return hmAppWidgetManager;
	}

	public void sendToCoreService(Intent mIntent) {
		if (startCoreServiceAsUser != null) {
			try {
				startCoreServiceAsUser.invoke(mCoreService, mIntent,
						android.os.Process.myUserHandle());
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				e.printStackTrace();
			}
		} else {
			Log.w(LOG_TAG, "No CoreService registred");
		}
	}

	/**
	 * Starts the HallMonitor services.
	 * 
	 */
	public void startServices() {
		Log.d(LOG_TAG, "Start all services called.");

		acquireCPUGlobal();

		mAppContext.startService(new Intent(mAppContext, CoreService.class));
		if (preference_all.getBoolean("pref_internalservice", false)) {
			if (preference_all.getBoolean("pref_realhall", false)) {
				mAppContext.startService(new Intent(mAppContext,
						ViewCoverHallService.class));
			} else if (preference_all.getBoolean("pref_proximity", false)) {
				mAppContext.startService(new Intent(mAppContext,
						ViewCoverProximityService.class));
			}
		}
		if (preference_all.getBoolean("pref_do_notifications", false)) {
			mAppContext.startService(new Intent(mAppContext,
					NotificationService.class));
		}
		if (getCoverClosed(true)) {
			Intent mIntent = new Intent(mAppContext, CoreService.class);
			mIntent.putExtra(CoreApp.CS_EXTRA_TASK,
					CoreApp.CS_TASK_LAUNCH_ACTIVITY);
			mAppContext.startService(mIntent);
		}

		releaseCPUGlobal();
	}

	/**
	 * Stops the HallMonitor service.
	 * 
	 */
	public void stopServices() {
		stopServices(false);
	}

	public void stopServices(boolean override_keep_admin) {

		Log.d(LOG_TAG, "Stop all services called.");

		if (getServiceRunning(ViewCoverHallService.class)) {
			mAppContext.stopService(new Intent(mAppContext,
					ViewCoverHallService.class));
		}
		if (getServiceRunning(ViewCoverProximityService.class)) {
			mAppContext.stopService(new Intent(mAppContext,
					ViewCoverProximityService.class));
		}
		if (getServiceRunning(NotificationService.class)) {
			mAppContext.stopService(new Intent(mAppContext,
					NotificationService.class));
		}
		if (getServiceRunning(CoreService.class)) {
			mAppContext.stopService(new Intent(mAppContext, CoreService.class));
		}

		// Relinquish device admin (unless asked not to)
		if (!override_keep_admin
				&& !preference_all.getBoolean("pref_keep_admin", false)) {
			DevicePolicyManager dpm = (DevicePolicyManager) mAppContext
					.getSystemService(Context.DEVICE_POLICY_SERVICE);
			ComponentName me = new ComponentName(mAppContext,
					AdminReceiver.class);
			if (dpm.isAdminActive(me)) {
				dpm.removeActiveAdmin(me);
			}
		}
	}

	public void restartServices() {
		stopServices();
		SystemClock.sleep(1000);
		startServices();
	}

	/**
	 * Is the service running.
	 * 
	 * @param ctx
	 *            Application context.
	 * @return Is the cover closed.
	 */
	public boolean getServiceRunning(@SuppressWarnings("rawtypes") Class svc) {

		Log.d(LOG_TAG, "Is service running called.");

		ActivityManager manager = (ActivityManager) mAppContext
				.getSystemService(Context.ACTIVITY_SERVICE);
		for (RunningServiceInfo service : manager
				.getRunningServices(Integer.MAX_VALUE)) {
			if (svc.getName().equals(service.service.getClassName())) {
				// the service is running
				Log.d(LOG_TAG, "The " + svc.getName() + " is running.");
				return true;
			}
		}
		// the service must not be running
		Log.d(LOG_TAG, "The " + svc.getName() + " service is NOT running.");
		return false;
	}

	/**
	 * With this non-CM users can use torch button in HallMonitor. Should
	 * (Hopefully) work on every device with SystemFeature FEATURE_CAMERA_FLASH
	 * This code has been tested on I9505 jflte with ParanoidAndroid 4.4 rc2
	 */

	// Turn On Flash
	public void turnOnFlash() {
		setTorchOn(true);
		camera = Camera.open();
		Parameters p = camera.getParameters();
		p.setFlashMode(Parameters.FLASH_MODE_TORCH);
		camera.setParameters(p);
		camera.startPreview();
		Log.d(LOG_TAG, "Flash turned on!");
	}

	// Turn Off Flash
	public void turnOffFlash() {
		Parameters p = camera.getParameters();
		p.setFlashMode(Parameters.FLASH_MODE_OFF);
		camera.setParameters(p);
		camera.stopPreview();
		// Be sure to release the camera when the flash is turned off
		if (camera != null) {
			camera.release();
			camera = null;
			Log.d(LOG_TAG, "Flash turned off and camera released!");
		}
		setTorchOn(false);
	}

	public boolean getDeviceHasFlash() {
		deviceHasFlash = mAppContext.getPackageManager().hasSystemFeature(
				PackageManager.FEATURE_CAMERA_FLASH);
		return deviceHasFlash;
	}

	public boolean getHardwareAccelerated() {
		return hardwareAccelerated;
	}

	public void requestAdmin() {
		if (!adminApp && preference_all.getBoolean("pref_lockmode", false)
				&& configurationActivity != null) {
			ComponentName me = new ComponentName(mAppContext,
					AdminReceiver.class);
			Log.d(LOG_TAG, "launching dpm overlay");
			mAppContext.startActivity(new Intent(getContext(),
					Configuration.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
					| Intent.FLAG_ACTIVITY_NO_ANIMATION
					| Intent.FLAG_ACTIVITY_CLEAR_TOP
					| Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS));
			Log.d(LOG_TAG, "Started configuration activity.");
			Intent coup = new Intent(
					DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
			coup.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, me);
			coup.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
					mAppContext.getString(R.string.admin_excuse));
			getConfigurationActivity().startActivityForResult(coup,
					CoreApp.DEVICE_ADMIN_WAITING);
		}
	}

	private class AsyncSuAvailable extends AsyncTask<Boolean, Boolean, Boolean> {
		@Override
		protected Boolean doInBackground(Boolean... params) {
			return Shell.SU.available();
		}
	}

	public static String createID() {
		return String.valueOf(idCounter.getAndIncrement());
	}

	public void acquireCPUDA() {
		daPartialWakeLock.acquire();
	}

	public void releaseCPUDA() {
		if (daPartialWakeLock.isHeld()) {
			daPartialWakeLock.release();
		}
	}

	public void acquireCPUGlobal() {
		globalPartialWakeLock.acquire();
		globalPartialWakeLockCount++;
		Log.d(LOG_TAG, "globalPartialWakeLockCount="
				+ globalPartialWakeLockCount);
	}

	public void releaseCPUGlobal() {
		if (globalPartialWakeLock.isHeld()) {
			globalPartialWakeLock.release();
			globalPartialWakeLockCount--;
			Log.d(LOG_TAG, "globalPartialWakeLockCount="
					+ globalPartialWakeLockCount);
		}
	}

	public boolean getInActivity() {
		if (camera_up | phone_ringing | alarm_firing) {
			return true;
		} else {
			return false;
		}
	}
}
