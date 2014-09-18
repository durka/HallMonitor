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

import java.lang.reflect.Constructor;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

public class Configuration extends PreferenceActivity {
	private final String LOG_TAG = "Configuration";

	private CoreStateManager mStateManager;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.d(LOG_TAG + ".onCreate", "creating");

		mStateManager = ((CoreApp) getApplicationContext()).getStateManager();

		// pass a reference back to the state manager
		if (!mStateManager.setConfigurationActivity(this)) {
			this.finish();
			return;
		}

		PreferenceFragment preferenceFragment = new PreferenceFragmentLoader();

		// add extra resource to load xml
		Bundle arguments = new Bundle(1);
		arguments.putCharSequence("resource", "preferences");
		preferenceFragment.setArguments(arguments);

		getFragmentManager().beginTransaction()
				.replace(android.R.id.content, preferenceFragment).commit();
	}

	@Override
	protected void onStart() {
		Log.d(LOG_TAG + ".onStart", "starting");

		super.onStart();
	}

	@Override
	protected void onResume() {
		super.onResume();
		Log.d(LOG_TAG + ".onResume", "resuming");

		if (mStateManager.getDefaultActivityRunning()) {
			mStateManager.closeDefaultActivity();
		}

		mStateManager.requestAdmin();
	}

	@Override
	protected void onPause() {
		Log.d(LOG_TAG + ".onPause", "pausing");

		super.onPause();
	}

	@Override
	protected void onStop() {
		Log.d(LOG_TAG + ".onStop", "stopping");

		super.onStop();
	}

	@Override
	protected void onDestroy() {
		Log.d(LOG_TAG + ".onDestroy", "detroying");

		mStateManager.setConfigurationActivity(null);
		super.onDestroy();
	}

	@Override
	public void onActivityResult(int request, int result, Intent data) {
		super.onActivityResult(request, result, data);
		activity_result(this, request, result, data);
	}

	@Override
	public boolean onPreferenceStartFragment(PreferenceFragment caller,
			Preference preference) {
		try {
			// create new instance of class
			Class<?> c = Class.forName(preference.getFragment());
			Constructor<?> cons = c.getConstructor();
			Object object = cons.newInstance();

			// add preference fragment
			if (object instanceof PreferenceFragment) {
				PreferenceFragment preferenceFragment = (PreferenceFragment) object;
				preferenceFragment.setArguments(preference.getExtras());

				getFragmentManager().beginTransaction()
						.replace(android.R.id.content, preferenceFragment)
						.addToBackStack((String) getActionBar().getTitle())
						.commit();
				getFragmentManager().executePendingTransactions();

				// update action bar
				updateHeaderTitle(preference.getExtras().getString("title"));
			} else {
				Log_d(LOG_TAG,
						"onPreferenceStartFragment: given class is not a PreferenceFragment");
			}
		} catch (Exception e) {
			Log_d(LOG_TAG, "onPreferenceStartFragment: exception occurred! "
					+ e.getMessage());
		}

		return true; // the default implementation returns true. documentation
						// is silent. FIXME
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		// Respond to the action bar's Up/Home button
		case android.R.id.home:
			onBackPressed();
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onBackPressed() {
		CharSequence title = getTitle();
		int idx;

		if ((idx = getFragmentManager().getBackStackEntryCount()) > 0) {
			title = getFragmentManager().getBackStackEntryAt(idx - 1).getName();
			getFragmentManager().popBackStackImmediate(); // TODO test back
															// stack
		} else {
			super.onBackPressed();
		}

		updateHeaderTitle(title);
	}

	private void updateHeaderTitle(CharSequence title) {
		getActionBar().setTitle(title);
		getActionBar().setDisplayHomeAsUpEnabled(
				(getFragmentManager().getBackStackEntryCount() > 0));
	}

	/**
	 * The Configuration activity acts as the main activity for the app. Any
	 * events received into its onActivityReceived method are passed on to be
	 * handled here.
	 * 
	 * @param ctx
	 *            Application context.
	 * @param request
	 *            Request Activity ID.
	 * @param result
	 *            Result Activity ID.
	 * @param data
	 *            Intent that holds the data received.
	 */
	public void activity_result(Context ctx, int request, int result,
			Intent data) {
		Log.d(LOG_TAG + ".Evt.activity_result",
				"Activity result received: request="
						+ Integer.toString(request) + ", result="
						+ Integer.toString(result));
		switch (request) {
		// call back for admin access request
		case CoreApp.DEVICE_ADMIN_WAITING:
			if (result == Activity.RESULT_OK) {
				mStateManager.refreshAdminApp();
			} else {
				mStateManager.refreshAdminApp();
				// we asked to be an admin and the user clicked Cancel
				// (why?)
				// complain, and un-check pref_enabled
				Toast.makeText(ctx, ctx.getString(R.string.admin_refused),
						Toast.LENGTH_SHORT).show();
			}
			break;
		// call back for appwidget pick
		case CoreApp.REQUEST_PICK_APPWIDGET:
			// widget picked
			mStateManager.setWidgetSettingsOngoing(true);
			if (result == Activity.RESULT_OK) {
				// widget chosen so launch configurator
				mStateManager.hmAppWidgetManager
						.configureWidget(data
								.getStringExtra(CoreApp.EXTRA_APPWIDGET_TYPE),
								data);
			} else {
				// choose dialog cancelled so clean up
				int appWidgetId = data.getIntExtra(
						AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
				if (appWidgetId != -1) {
					mStateManager.hmAppWidgetManager
							.deleteAppWidgetId(appWidgetId);
					mStateManager
							.getPreference()
							.edit()
							.putBoolean(
									"pref_"
											+ data.getStringExtra(CoreApp.EXTRA_APPWIDGET_TYPE)
											+ "_widget", false) // FIXME
																// this is a
																// huge hack
							.commit();
				}
			}
			break;

		// call back for appwidget configure
		case CoreApp.REQUEST_CONFIGURE_APPWIDGET:
			mStateManager.setWidgetSettingsOngoing(false);
			// widget configured
			if (result == Activity.RESULT_OK) {
				// widget configured successfully so create it
				mStateManager.hmAppWidgetManager
						.createWidget(data
								.getStringExtra(CoreApp.EXTRA_APPWIDGET_TYPE),
								data);
			} else {
				// configure dialog cancelled so clean up
				if (data != null) {
					int appWidgetId = data.getIntExtra(
							AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
					if (appWidgetId != -1) {
						mStateManager.hmAppWidgetManager
								.deleteAppWidgetId(appWidgetId);
						mStateManager
								.getPreference()
								.edit()
								.putBoolean(
										"pref_"
												+ data.getStringExtra(CoreApp.EXTRA_APPWIDGET_TYPE)
												+ "_widget", false) // FIXME
																	// this
																	// is a
																	// huge
																	// hack
								.commit();
					}
				}
			}
			break;

		case CoreApp.NOTIFICATION_LISTENER_ON:
			Log.d(LOG_TAG + ".Evt.activity_result",
					"return from checking the box");
			mStateManager.setNotificationSettingsOngoing(false);
			if (!mStateManager.getServiceRunning(NotificationService.class)) {
				Toast.makeText(ctx,
						ctx.getString(R.string.notif_left_unchecked),
						Toast.LENGTH_SHORT).show();
				mStateManager.getPreference().edit()
						.putBoolean("pref_do_notifications", false).commit();
			}
			break;
		case CoreApp.NOTIFICATION_LISTENER_OFF:
			Log.d(LOG_TAG + ".Evt.activity_result",
					"return from unchecking the box");
			mStateManager.setNotificationSettingsOngoing(false);
			if (mStateManager.getServiceRunning(NotificationService.class)) {
				Toast.makeText(ctx, ctx.getString(R.string.notif_left_checked),
						Toast.LENGTH_SHORT).show();
				mStateManager.getPreference().edit()
						.putBoolean("pref_do_notifications", true).commit();
			}
			break;
		}
	}

	private void Log_d(String tag, String msg) {
		if (mStateManager.getPreference().getBoolean("pref_dev_opts_debug",
				false)) {
			Log.d(tag, msg);
		}
	}
}
