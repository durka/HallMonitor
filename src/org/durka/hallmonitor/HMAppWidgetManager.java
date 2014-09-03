package org.durka.hallmonitor;

import java.util.HashMap;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/**
 * The purpose of this Class is to provide management capabilities for the app
 * widgets that get added to the HallMonitor application.
 * 
 * @author nwalters
 * 
 */
public class HMAppWidgetManager {
	private static final String LOG_TAG = "Hall.HMAWM";

	private final CoreStateManager mStateManager;

	// map of our selected widgets
	private final HashMap<String, AppWidgetHostView> widgetsMap = new HashMap<String, AppWidgetHostView>();

	// app widget management classes we need
	private AppWidgetManager mAppWidgetManager;
	private AppWidgetHost mAppWidgetHost;

	// default constructor
	public HMAppWidgetManager(CoreStateManager stateManager) {
		mStateManager = stateManager;

		if (mAppWidgetManager == null) {
			mAppWidgetManager = AppWidgetManager.getInstance(mStateManager
					.getContext());
		} else {
			Log.e(LOG_TAG + ".constructor",
					"HMAppWidgetManager uneable to get AppWidgetManager.");
		}
		if (mAppWidgetHost == null) {
			mAppWidgetHost = new AppWidgetHost(mStateManager.getContext(),
					R.id.APPWIDGET_HOST_ID);
		} else {
			Log.e(LOG_TAG + ".constructor",
					"HMAppWidgetManager uneable to get AppWidgetHost.");
		}

		Log.d(LOG_TAG + ".constructor", "HMAppWidgetManager instantiated.");
	}

	/**
	 * Kick off the widget picker dialog
	 * 
	 * @param act
	 *            The Activity to use as the context for these actions
	 * @param widgetType
	 *            The type of widget (e.g. 'default', 'media', 'notification'
	 *            etc.)
	 */
	public void registerWidget(String widgetType) {

		Log.d(LOG_TAG + ".register_widget",
				"Register widget called with type: " + widgetType);

		// get an id for our app widget
		int appWidgetId = mAppWidgetHost.allocateAppWidgetId();
		mStateManager.getPreference().edit()
				.putInt(widgetType + "_widget_id", appWidgetId).commit();

		Log.d(LOG_TAG + ".register_widget", "appWidgetId allocated: "
				+ appWidgetId);

		// kick off the widget picker, the call back will be picked up in
		mStateManager.setWidgetSettingsOngoing(true);
		mStateManager.getContext().startActivity(
				new Intent(mStateManager.getContext(), Configuration.class)
						.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
								| Intent.FLAG_ACTIVITY_NO_ANIMATION
								| Intent.FLAG_ACTIVITY_CLEAR_TOP
								| Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS));
		Log.d(LOG_TAG + ".register_widget", "Started configuration activity.");

		// create an intent to allow us to fire up the widget picker
		Intent pickIntent = new Intent(AppWidgetManager.ACTION_APPWIDGET_PICK);
		pickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
		pickIntent.putExtra(CoreApp.EXTRA_APPWIDGET_TYPE, widgetType);

		mStateManager.getConfigurationActivity().startActivityForResult(
				pickIntent, CoreApp.REQUEST_PICK_APPWIDGET);
	}

	/**
	 * Launch into configuration dialog if required
	 * 
	 * @param data
	 *            Intent payload, needed for getting app widget id
	 * @param ctx
	 *            Calling context
	 */
	public void configureWidget(String widgetType, Intent data) {
		// get the app widget id from the call back
		Bundle extras = data.getExtras();
		int appWidgetId = extras
				.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);

		Log.d(LOG_TAG + ".configureWidget", "Configure widget called with id: "
				+ appWidgetId);

		// use the app widget id to get the app widget info
		AppWidgetProviderInfo appWidgetInfo = mAppWidgetManager
				.getAppWidgetInfo(appWidgetId);

		// check if we need to configure
		if (appWidgetInfo.configure != null) {

			Log.d(LOG_TAG + ".configureWidget",
					"This is a configurable widget, launching widget configuraiton activity");

			mStateManager.setWidgetSettingsOngoing(true);
			mStateManager
					.getContext()
					.startActivity(
							new Intent(mStateManager.getContext(),
									Configuration.class)
									.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
											| Intent.FLAG_ACTIVITY_NO_ANIMATION
											| Intent.FLAG_ACTIVITY_CLEAR_TOP
											| Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS));
			Log.d(LOG_TAG + ".register_widget",
					"Started configuration activity.");

			// we do so launch into configuration dialog
			Intent intent = new Intent(
					AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
			intent.setComponent(appWidgetInfo.configure);
			intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
			intent.putExtra(CoreApp.EXTRA_APPWIDGET_TYPE, widgetType);

			mStateManager.getConfigurationActivity().startActivityForResult(
					intent, CoreApp.REQUEST_CONFIGURE_APPWIDGET);
		} else {

			Log.d(LOG_TAG + ".configureWidget",
					"This is NOT a configurable widget.");

			// we don't, just create it already
			createWidget(widgetType, data);
		}
	}

	/**
	 * Create the AppWidgetHostView representing our widget and store in our map
	 * 
	 * @param data
	 *            Intent payload, needed for getting app widget id
	 * @param ctx
	 *            Calling context
	 */
	public void createWidget(String widgetType, Intent data) {
		// get the app widget id from the call back
		Bundle extras = data.getExtras();
		int appWidgetId = extras
				.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);

		Log.d(LOG_TAG + ".createWidget", "Create widget called with id: "
				+ appWidgetId);

		// use the app widget id to get the app widget info
		AppWidgetProviderInfo appWidgetInfo = mAppWidgetManager
				.getAppWidgetInfo(appWidgetId);

		// debug out all the info about the widget
		if (Log.isLoggable(LOG_TAG + ".createWidget", Log.DEBUG)) {
			Log.d(LOG_TAG + ".createWidget", "appWidgetInfo Label: "
					+ appWidgetInfo.label);
			Log.d(LOG_TAG + ".createWidget", "appWidgetInfo minHeight: "
					+ appWidgetInfo.minHeight);
			Log.d(LOG_TAG + ".createWidget", "appWidgetInfo minResizeWidth: "
					+ appWidgetInfo.minResizeHeight);
			Log.d(LOG_TAG + ".createWidget", "appWidgetInfo minWidth: "
					+ appWidgetInfo.minWidth);
			Log.d(LOG_TAG + ".createWidget", "appWidgetInfo minResizeWidth: "
					+ appWidgetInfo.minResizeWidth);
			Log.d(LOG_TAG + ".createWidget", "appWidgetInfo resizeMode: "
					+ appWidgetInfo.resizeMode);
			Log.d(LOG_TAG + ".createWidget",
					"appWidgetInfo updatePeriodMillis: "
							+ appWidgetInfo.updatePeriodMillis);
			Log.d(LOG_TAG + ".createWidget", "appWidgetInfo widgetCategory: "
					+ appWidgetInfo.widgetCategory);
		}

		// create the hostView - this effectively represents our widget
		AppWidgetHostView hostView = mAppWidgetHost.createView(
				mStateManager.getContext(), appWidgetId, appWidgetInfo);
		// bizarrely this is needed to tell the hostView about the widget
		// (again!)
		hostView.setAppWidget(appWidgetId, appWidgetInfo);

		// store hostView into our widgets map for access later
		widgetsMap.put(widgetType, hostView);

		// start the widget listening
		mAppWidgetHost.startListening();

		Log.d(LOG_TAG + ".createWidget", "Widget created and stored of type: "
				+ widgetType);
	}

	/**
	 * Remove the app widget ID as the user has cancelled the process at some
	 * point
	 * 
	 * @param appWidgetId
	 *            The app widget ID to remove
	 */
	public void deleteAppWidgetId(int appWidgetId) {

		Log.d(LOG_TAG + ".deleteAppWidgetId", "Deleting widget id: "
				+ appWidgetId);

		// stop the widget listening
		mAppWidgetHost.stopListening();

		mAppWidgetHost.deleteAppWidgetId(appWidgetId);
	}

	/**
	 * The user has decided they don't want to use the custom widget so let's
	 * get rid of it
	 * 
	 * @param ctx
	 *            The calling context
	 * @param widgetType
	 *            The type of widget they are done with
	 */
	public void unregisterWidget(String widgetType) {

		Log.d(LOG_TAG + ".unregister_widget",
				"Unregister widget called with type: " + widgetType);

		widgetsMap.remove(widgetType);

		// FIXME: Should we also clear up the app widget ID?
	}

	/**
	 * Get the specified widget from the map
	 * 
	 * @param widgetType
	 *            The type of the widget to get
	 * @return The stored widget
	 */
	public AppWidgetHostView getAppWidgetHostViewByType(String widgetType) {

		Log.d(LOG_TAG + ".getAppWidgetHostViewByType",
				"Widget requested of type: " + widgetType);

		AppWidgetHostView thisWidget = widgetsMap.get(widgetType);
		;

		if (thisWidget == null) {
			Log.w(LOG_TAG + ".getAppWidgetHostViewByType",
					"Widget type does not exist in widget Map: " + widgetType);
		}

		return thisWidget;
	}

	/**
	 * Get the specified widget from the map
	 * 
	 * @param widgetType
	 *            The type of the widget to get
	 * @return The stored widget
	 */
	public boolean doesWidgetExist(String widgetType) {

		Log.d(LOG_TAG + ".doesWidgetExist", "Checking for Widget of type: "
				+ widgetType);

		return (widgetsMap.get(widgetType) != null);
	}

}
