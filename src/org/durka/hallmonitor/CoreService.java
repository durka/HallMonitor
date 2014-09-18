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

import android.app.ActivityManager;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.KeyEvent;
import eu.chainfire.libsuperuser.Shell;

public class CoreService extends Service {
	private final String LOG_TAG = "Hall.CS";

	private CoreStateManager mStateManager;

	private Looper mTouchCoverLooper;
	private TouchCoverHandler mTouchCoverHandler;
	private Boolean lastTouchCoverRequest;
	private LocalBroadcastManager mLocalBroadcastManager;

	@Override
	public void onCreate() {
		Log.d(LOG_TAG + ".oC", "Core service creating");

		mStateManager = ((CoreApp) getApplicationContext()).getStateManager();

		Log.d(LOG_TAG + ".oC", "Register special actions");

		mStateManager.registerCoreReceiver();

		mLocalBroadcastManager = LocalBroadcastManager.getInstance(this);

		HandlerThread thread = new HandlerThread("ServiceStartArguments",
				Process.THREAD_PRIORITY_BACKGROUND);
		thread.start();

		// Get the HandlerThread's Looper and use it for our Handler
		mTouchCoverLooper = thread.getLooper();
		mTouchCoverHandler = new TouchCoverHandler(mTouchCoverLooper);
		lastTouchCoverRequest = mStateManager.getCoverClosed();
	}

	@Override
	public IBinder onBind(Intent intent) {
		// We don't provide binding, so return null
		return null;
	}

	@Override
	public void onDestroy() {
		mStateManager.unregisterCoreReceiver();

		Log.d(LOG_TAG + ".oD", "Core service stopped");

		super.onDestroy();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (!mStateManager.getMainLaunched()
				&& mStateManager.getPreference().getBoolean("pref_enabled",
						false)) {
			Message msg = Message.obtain();
			msg.arg1 = startId;
			msg.obj = this;
			msg.what = CoreApp.CS_TASK_MAINLAUNCH;
			ServiceThread svcThread = new ServiceThread(msg);
			svcThread.start();
		}

		if (intent != null && intent.hasExtra(CoreApp.CS_EXTRA_TASK)) {
			int requestedTaskMode = intent
					.getIntExtra(CoreApp.CS_EXTRA_TASK, 0);
			if (requestedTaskMode > 0) {
				int msgArg2 = 0;
				switch (requestedTaskMode) {
				case CoreApp.CS_TASK_CHANGE_TOUCHCOVER:
					boolean sendTouchCoverRequest = intent.getBooleanExtra(
							CoreApp.CS_EXTRA_STATE, false);
					if (sendTouchCoverRequest != lastTouchCoverRequest) {
						lastTouchCoverRequest = sendTouchCoverRequest;
						Message msgTCH = mTouchCoverHandler.obtainMessage();
						msgTCH.arg1 = startId;
						if (sendTouchCoverRequest) {
							msgTCH.arg2 = 1;
						} else {
							msgTCH.arg2 = 0;
						}
						mTouchCoverHandler.sendMessage(msgTCH);
					}
					return START_STICKY;
				case CoreApp.CS_TASK_AUTO_BLACKSCREEN:
					if (mStateManager.getBlackScreenTime() > 0) {
						Log.d(LOG_TAG + ".handler",
								"Blackscreen already requested");
						return START_STICKY;
					}
					break;
				case CoreApp.CS_TASK_LAUNCH_ACTIVITY:
					if (intent.getBooleanExtra(CoreApp.CS_EXTRA_STATE, false)) {
						msgArg2 = 1;
					}
					break;
				case CoreApp.CS_TASK_TORCH_STATE:
					if (intent.getBooleanExtra(CoreApp.CS_EXTRA_STATE, false)) {
						msgArg2 = 1;
					}
					break;
				}
				Log.d(LOG_TAG + ".oSC", "Request starting: "
						+ requestedTaskMode);
				Message msg = Message.obtain();
				msg.arg1 = startId;
				msg.arg2 = msgArg2;
				msg.obj = this;
				msg.what = requestedTaskMode;
				ServiceThread svcThread = new ServiceThread(msg);
				svcThread.start();
			}
		}
		// If we get killed, after returning from here, restart
		return START_STICKY;
	}

	private final class TouchCoverHandler extends Handler {
		public TouchCoverHandler(Looper looper) {
			super(looper);
		}

		@Override
		public void handleMessage(Message msg) {
			Boolean enable = (msg.arg2 == 1);
			// if we are running in root enabled mode then lets up the
			// sensitivity on the view screen
			// so we can use the screen through the window
			if (mStateManager.getRootApp()) {
				if (enable) {
					Log.d(LOG_TAG + ".enableCoverTouch",
							"We're root enabled so lets boost the sensitivity...");
					if (Build.DEVICE.equals(CoreApp.DEV_SERRANO_LTE_CM10)
							|| Build.DEVICE
									.equals(CoreApp.DEV_SERRANO_LTE_CM11)
							|| Build.DEVICE.equals(CoreApp.DEV_SERRANO_DS_CM10)
							|| Build.DEVICE.equals(CoreApp.DEV_SERRANO_DS_CM11)
							|| Build.DEVICE.equals(CoreApp.DEV_SERRANO_3G_CM11)) {
						Shell.SU.run(new String[] {
								"echo module_on_master > /sys/class/sec/tsp/cmd && cat /sys/class/sec/tsp/cmd_result",
								"echo clear_cover_mode,3 > /sys/class/sec/tsp/cmd && cat /sys/class/sec/tsp/cmd_result" });
					} else { // others devices
						Shell.SU.run(new String[] { "echo clear_cover_mode,1 > /sys/class/sec/tsp/cmd" });
					}
					Log.d(LOG_TAG + ".enableCoverTouch",
							"...Sensitivity boosted, hold onto your hats!");
				} else {
					Log.d(LOG_TAG + ".enableCoverTouch",
							"We're root enabled so lets revert the sensitivity...");
					Shell.SU.run(new String[] { "echo clear_cover_mode,0 > /sys/class/sec/tsp/cmd && cat /sys/class/sec/tsp/cmd_result" });
					Log.d(LOG_TAG + ".enableCoverTouch",
							"...Sensitivity reverted, sanity is restored!");
				}
			}
		}
	}

	private class ServiceThread extends Thread {
		private final Message msg;

		public ServiceThread(Message msgSend) {
			super();
			this.msg = msgSend;
		}

		@Override
		public void run() {
			Log.d(LOG_TAG + ".handler", "Thread " + msg.arg1 + ": started");

			Context ctx;
			switch (msg.what) {
			case CoreApp.CS_TASK_TORCH_STATE:
				ctx = (Context) msg.obj;
				Intent torchDAIntent = new Intent(
						CoreApp.DA_ACTION_TORCH_STATE_CHANGED);
				if (msg.arg2 == 1) {
					mStateManager.setTorchOn(true);
					torchDAIntent.putExtra(CoreApp.DA_EXTRA_STATE, true);
					if (mStateManager.getCoverClosed()) {
						bringDefaultActivityToFront(ctx, true);
					}
				} else {
					mStateManager.setTorchOn(false);
					torchDAIntent.putExtra(CoreApp.DA_EXTRA_STATE, false);
					if (mStateManager.getCoverClosed()) {
						bringDefaultActivityToFront(ctx, false);
					}
				}
				mLocalBroadcastManager.sendBroadcast(torchDAIntent);
				break;
			case CoreApp.CS_TASK_TORCH_TOGGLE:
				ctx = (Context) msg.obj;
				if (mStateManager.getPreference().getBoolean(
						"pref_flash_controls", false)) {
					Intent intent = new Intent(CoreReceiver.TOGGLE_FLASHLIGHT);
					intent.putExtra("strobe", false);
					intent.putExtra("period", 100);
					intent.putExtra("bright", false);
					sendBroadcastAsUser(intent,
							android.os.Process.myUserHandle());
				} else if (mStateManager.getPreference().getBoolean(
						"pref_flash_controls_alternative", false)) {
					if (!mStateManager.getTorchOn()) {
						mStateManager.turnOnFlash();
						Intent mIntent = new Intent(ctx, CoreService.class);
						mIntent.putExtra(CoreApp.CS_EXTRA_TASK,
								CoreApp.CS_TASK_TORCH_STATE);
						mIntent.putExtra(CoreApp.CS_EXTRA_STATE, true);
						startService(mIntent);
					} else {
						mStateManager.turnOffFlash();
						Intent mIntent = new Intent(ctx, CoreService.class);
						mIntent.putExtra(CoreApp.CS_EXTRA_TASK,
								CoreApp.CS_TASK_TORCH_STATE);
						mIntent.putExtra(CoreApp.CS_EXTRA_STATE, false);
						startService(mIntent);
					}
				}
				break;
			case CoreApp.CS_TASK_HEADSET_PLUG:
				ctx = (Context) msg.obj;
				Intent headSetIntent = new Intent(
						CoreApp.DA_ACTION_WIDGET_REFRESH);
				mLocalBroadcastManager.sendBroadcast(headSetIntent);
				if (mStateManager.getCoverClosed()) {
					bringDefaultActivityToFront(ctx, false);
				}
				break;
			case CoreApp.CS_TASK_HANGUP_CALL:
				Log.d(LOG_TAG + ".handler", "Thread " + msg.arg1
						+ ": hanging up! goodbye");

				KeyEvent keyHangup = new KeyEvent(KeyEvent.ACTION_UP,
						KeyEvent.KEYCODE_HEADSETHOOK);
				keyHangup = KeyEvent.changeTimeRepeat(keyHangup,
						System.currentTimeMillis(), 1, keyHangup.getFlags()
								| KeyEvent.FLAG_LONG_PRESS);
				keyHangup = KeyEvent.changeFlags(keyHangup,
						keyHangup.getFlags() | KeyEvent.FLAG_LONG_PRESS);
				Intent pressHangUp = new Intent(Intent.ACTION_MEDIA_BUTTON);
				pressHangUp.putExtra(Intent.EXTRA_KEY_EVENT, keyHangup);
				sendOrderedBroadcast(pressHangUp,
						"android.permission.CALL_PRIVILEGED");
				break;
			case CoreApp.CS_TASK_PICKUP_CALL:
				Log.d(LOG_TAG + ".handler", "Thread " + msg.arg1
						+ ": picking up! hello");
				Intent pressPickupCall = new Intent(Intent.ACTION_MEDIA_BUTTON);
				pressPickupCall.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(
						KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HEADSETHOOK));
				sendOrderedBroadcast(pressPickupCall,
						"android.permission.CALL_PRIVILEGED");
				break;
			case CoreApp.CS_TASK_INCOMMING_CALL:
				ctx = (Context) msg.obj;
				wait_package_front_launched(ctx, CoreApp.PACKAGE_PHONE_APP);
				if (mStateManager.getCoverClosed()) {
					Log.d(LOG_TAG + ".handler", "Thread " + msg.arg1
							+ ": the screen is closed. screen my calls");

					bringDefaultActivityToFront(ctx, true);
				}
				break;
			case CoreApp.CS_TASK_SNOOZE_ALARM:
				ctx = (Context) msg.obj;
				// Broadcast alarm snooze event
				Intent alarmSnooze = new Intent(
						CoreReceiver.ALARM_SNOOZE_ACTION);
				sendBroadcastAsUser(alarmSnooze,
						android.os.Process.myUserHandle());
				break;
			case CoreApp.CS_TASK_DISMISS_ALARM:
				ctx = (Context) msg.obj;
				// Broadcast alarm Dismiss event
				Intent alarmDismiss = new Intent(
						CoreReceiver.ALARM_DISMISS_ACTION);
				sendBroadcastAsUser(alarmDismiss,
						android.os.Process.myUserHandle());
				break;
			case CoreApp.CS_TASK_INCOMMING_ALARM:
				ctx = (Context) msg.obj;
				wait_package_front_launched(ctx, CoreApp.PACKAGE_ALARM_APP);
				if (mStateManager.getCoverClosed()) {
					Log.d(LOG_TAG + ".handler", "Thread " + msg.arg1
							+ ": the screen is closed. screen alarm");

					bringDefaultActivityToFront(ctx, true);
				}
				break;
			case CoreApp.CS_TASK_LAUNCH_ACTIVITY:
				ctx = (Context) msg.obj;
				boolean noBlackScreen = false;
				if (msg.arg2 == 1) {
					noBlackScreen = true;
				}
				Log.d(LOG_TAG + ".handler", "Thread " + msg.arg1
						+ ": Launch activity / " + noBlackScreen);
				bringDefaultActivityToFront(ctx, noBlackScreen);
				break;
			case CoreApp.CS_TASK_AUTO_BLACKSCREEN:
				ctx = (Context) msg.obj;
				// already request running
				if (mStateManager.getBlackScreenTime() > 0) {
					Log.d(LOG_TAG + ".handler", "Thread " + msg.arg1
							+ ": Blackscreen already requested");
					break;
				}
				mStateManager.setBlackScreenTime(System.currentTimeMillis()
						+ mStateManager.getPreference().getInt("pref_delay",
								10000));
				Log.d(LOG_TAG + ".handler",
						"Thread " + msg.arg1 + ": Blackscreen time set to: "
								+ mStateManager.getBlackScreenTime());
				while (System.currentTimeMillis() < mStateManager
						.getBlackScreenTime()) {
					try {
						Thread.sleep(100);
					} catch (InterruptedException e1) {
					}
				}
				if (mStateManager.getBlackScreenTime() > 0) {
					Log.d(LOG_TAG + ".handler",
							"Thread " + msg.arg1 + ": Launch blackscreen at: "
									+ mStateManager.getBlackScreenTime());
					launchBlackScreen(ctx);
					mStateManager.setBlackScreenTime(0);
				} else {
					Log.d(LOG_TAG + ".handler", "Thread " + msg.arg1
							+ ": Blackscreen canceled");
				}
				break;
			case CoreApp.CS_TASK_WAKEUP_DEVICE:
				Log.d(LOG_TAG + ".handler", "Thread " + msg.arg1
						+ ": Launch wakeup");
				ctx = (Context) msg.obj;
				wakeUpDevice(ctx);
				break;
			case CoreApp.CS_TASK_MAINLAUNCH:
				if (!mStateManager.getMainLaunched()) {
					mStateManager.setMainLaunched(true);
					mStateManager.startServices();
					Log.d(LOG_TAG + ".handler", "Thread " + msg.arg1
							+ ": Mainthread launched");
					while (mStateManager.getMainLaunched()
							&& mStateManager.getPreference().getBoolean(
									"pref_enabled", false)) {
						try {
							Thread.sleep(10000);
						} catch (InterruptedException e1) {
						}
					}
					mStateManager.setMainLaunched(false);
					stopSelf();
					Log.d(LOG_TAG + ".handler", "Thread " + msg.arg1
							+ ": Mainthread stopped");
					Log.d(LOG_TAG + ".handler", "Thread " + msg.arg1
							+ ": Core Service stopping");
				} else {
					Log.d(LOG_TAG + ".handler", "Thread " + msg.arg1
							+ ": Mainthread already launched");
				}
				break;
			}

			Log.d(LOG_TAG + ".handler", "Thread " + msg.arg1 + ": ended");
			// Stop the service using the startId, so that we don't stop
			// the service in the middle of handling another job
		}

		private void wait_package_front_launched(Context ctx, String pacakgeName) {
			Log.d(LOG_TAG + ".wpl", "Wait launch of " + pacakgeName);
			ActivityManager am = (ActivityManager) ctx
					.getSystemService(Context.ACTIVITY_SERVICE);
			long maxWaitTime = System.currentTimeMillis() + 10 * 1000;
			while (System.currentTimeMillis() < maxWaitTime) {
				// The first in the list of RunningTasks is always the
				// foreground task.
				if (am.getRunningTasks(1).get(0).topActivity.getPackageName()
						.equalsIgnoreCase(pacakgeName)) {
					Log.d(LOG_TAG + ".wpl", pacakgeName + " detected");
					break;
				}
				try {
					Thread.sleep(100);
				} catch (InterruptedException e1) {
				}
			}
		}

		private void launchBlackScreen(Context ctx) {
			if (mStateManager.getCoverClosed()) {
				Log.d(LOG_TAG + ".lBS", "Cover closed.");
				if (mStateManager.getLockMode()) {
					final DevicePolicyManager dpm = (DevicePolicyManager) ctx
							.getSystemService(Context.DEVICE_POLICY_SERVICE);
					Log.d(LOG_TAG + ".lBS", "Lock now.");
					dpm.lockNow();
					Intent freeScreenDAIntent = new Intent(
							CoreApp.DA_ACTION_FREE_SCREEN);
					LocalBroadcastManager.getInstance(ctx).sendBroadcast(
							freeScreenDAIntent);
				} else if (mStateManager.getOsPowerManagement()) {
					Log.d(LOG_TAG + ".lBS", "OS must manage screen off.");
					Intent freeScreenDAIntent = new Intent(
							CoreApp.DA_ACTION_FREE_SCREEN);
					LocalBroadcastManager.getInstance(ctx).sendBroadcast(
							freeScreenDAIntent);
				} else if (mStateManager.getInternalPowerManagement()) {
					if (mStateManager.getPowerManager().isScreenOn()) {
						Log.d(LOG_TAG + ".lBS", "Go to sleep now.");
						mStateManager.getPowerManager().goToSleep(
								SystemClock.uptimeMillis());
					} else {
						Log.d(LOG_TAG + ".lBS", "Screen already off.");
					}
				}
			} else {
				Log.d(LOG_TAG + ".lBS", "Cover open???.");
			}
		}

		private void wakeUpDevice(Context ctx) {
			if (mStateManager.getInternalPowerManagement()) {
				if (!mStateManager.getPowerManager().isScreenOn()) {
					Log.d(LOG_TAG + ".wUD", "WakeUp device.");
					mStateManager.getPowerManager().wakeUp(
							SystemClock.uptimeMillis());
				} else {
					Log.d(LOG_TAG + ".wUD", "Screen already on.");
				}
			} else {
				// FIXME Would be nice to remove the deprecated FULL_WAKE_LOCK
				// if possible
				Log.d(LOG_TAG + ".wUD", "aww why can't I hit snooze");
				@SuppressWarnings("deprecation")
				PowerManager.WakeLock wl = mStateManager.getPowerManager()
						.newWakeLock(
								PowerManager.FULL_WAKE_LOCK
										| PowerManager.ACQUIRE_CAUSES_WAKEUP,
								ctx.getString(R.string.app_name));
				wl.acquire();
				wl.release();
			}
		}

		private void bringDefaultActivityToFront(Context ctx,
				boolean noBlackScreen) {

			Log.d(LOG_TAG + ".bDATF", "Launching default activity");

			if (noBlackScreen) {
				mStateManager.setBlackScreenTime(0);
			}

			// if (!mStateManager.getDefaultActivityStarting()) {
			// bring up the default activity window
			// we are using the show when locked flag as we'll re-use this
			// method to show the screen on power button press
			ctx.startActivity(new Intent(ctx, DefaultActivity.class)
					.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
							| Intent.FLAG_ACTIVITY_NO_ANIMATION
							| Intent.FLAG_ACTIVITY_CLEAR_TOP
							| Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS));
			Log.d(LOG_TAG + ".bDATF", "Started activity.");
			// } else {
			// Log.d(LOG_TAG + ".bDATF", "Activity already starting.");
			// }

			if (!noBlackScreen) {
				// step 2: wait for the delay period and turn the screen off
				Intent mIntent = new Intent(ctx, CoreService.class);
				mIntent.putExtra(CoreApp.CS_EXTRA_TASK,
						CoreApp.CS_TASK_AUTO_BLACKSCREEN);
				ctx.startService(mIntent);
			}
		}
	}
}
