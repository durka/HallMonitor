package org.durka.hallmonitor;

import java.util.ArrayList;
import java.util.HashMap;

import android.app.Activity;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/**
 * The purpose of this Class is to provide management capabilities for the app widgets
 * that get added to the HallMonitor application.
 * @author nwalters
 *
 */
public class HMAppWidgetManager {

	//map of our selected widgets
	private HashMap<String, AppWidgetHostView> widgetsMap = new HashMap<String, AppWidgetHostView>();
	
	//track which widget we are currently dealing with - holds state across the events firing
	//this is a bit clunky, but there's no need to worry about thread safety so should be fine
	public String currentWidgetType;
	
	//app widget management classes we need
	public AppWidgetManager mAppWidgetManager;
	public AppWidgetHost mAppWidgetHost;
  
    //default constructor
    public HMAppWidgetManager() {
    	//nothing to do
    	Log.d("HMAWM.constructor","HMAppWidgetManager instantiated.");
    }
    
    /**
     * Kick off the widget picker dialog
     * @param act The Activity to use as the context for these actions
     * @param widgetType The type of widget (e.g. 'default', 'media', 'notification' etc.)
     */
	public void register_widget(Activity act, String widgetType) {
	
		Log.d("HMAWM.register_widget","Register widget called with type: " + widgetType);
		
		//if we haven't yet created an app widget manager and app widget host instance then do so
		if (mAppWidgetManager == null) mAppWidgetManager = AppWidgetManager.getInstance(act);
		if (mAppWidgetHost == null) mAppWidgetHost = new AppWidgetHost(act, R.id.APPWIDGET_HOST_ID);
		
		//get an id for our app widget
		int appWidgetId = mAppWidgetHost.allocateAppWidgetId();	
		
		Log.d("HMAWM.register_widget","appWidgetId allocated: " + appWidgetId);
				
		//create an intent to allow us to fire up the widget picker
	    Intent pickIntent = new Intent(AppWidgetManager.ACTION_APPWIDGET_PICK);
	    pickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
	    
	    //have to add the next 4 lines to avoid an android bug according to this guy: http://coderender.blogspot.co.uk/2012/01/hosting-android-widgets-my.html
	    //FIXME check if this is till required and remove if not
	    ArrayList<AppWidgetProviderInfo> customInfo = new ArrayList<AppWidgetProviderInfo>();
	    pickIntent.putParcelableArrayListExtra(AppWidgetManager.EXTRA_CUSTOM_INFO, customInfo);
	    ArrayList<Bundle> customExtras = new ArrayList<Bundle>();
	    pickIntent.putParcelableArrayListExtra(AppWidgetManager.EXTRA_CUSTOM_EXTRAS, customExtras);

	    //store our widgetType so we know what we are doing when the call back comes back
	    currentWidgetType = widgetType;
	    
	    //kick off the widget picker, the call back will be picked up in Functions.Events
	    Functions.widget_settings_ongoing = true;
	    act.startActivityForResult(pickIntent, Functions.REQUEST_PICK_APPWIDGET);
		
	}
    
	/**
	 * Launch into configuration dialog if required
	 * @param data Intent payload, needed for getting app widget id
	 * @param ctx Calling context
	 */
	public void configureWidget(Intent data, Context ctx) {
		//get the app widget id from the call back
	    Bundle extras = data.getExtras();
	    int appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
	    
	    Log.d("HMAWM.configureWidget","Configure widget called with id: " + appWidgetId);
	    
	    //use the app widget id to get the app widget info
	    AppWidgetProviderInfo appWidgetInfo = mAppWidgetManager.getAppWidgetInfo(appWidgetId);
	    
	    //check if we need to configure
	    if (appWidgetInfo.configure != null) {
	    	
	    	Log.d("HMAWM.configureWidget","This is a configurable widget, launching widget configuraiton activity");
	    	
	    	//we do so launch into configuration dialog
	        Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
	        intent.setComponent(appWidgetInfo.configure);
	        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
	        Functions.widget_settings_ongoing = true;
	        ((Activity)ctx).startActivityForResult(intent, Functions.REQUEST_CONFIGURE_APPWIDGET);
	    } else {
	    	
	    	Log.d("HMAWM.configureWidget","This is NOT a configurable widget.");
	    	
	    	//we don't, just create it already
	        createWidget(data, ctx);
	    }
	}
			
	
	/**
	 * Create the AppWidgetHostView representing our widget and store in our map
	 * @param data Intent payload, needed for getting app widget id
	 * @param ctx Calling context
	 */
	public void createWidget(Intent data, Context ctx) {
		//get the app widget id from the call back
	    Bundle extras = data.getExtras();
	    int appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
	    
	    Log.d("HMAWM.createWidget","Create widget called with id: " + appWidgetId);
	    
	    //use the app widget id to get the app widget info
	    AppWidgetProviderInfo appWidgetInfo = mAppWidgetManager.getAppWidgetInfo(appWidgetId);
	    
	    //debug out all the info about the widget
	    if (Log.isLoggable("HMAWM.createWidget", Log.DEBUG)){
	    	Log.d("HMAWM.createWidget", "appWidgetInfo Label: " + appWidgetInfo.label);
	    	Log.d("HMAWM.createWidget", "appWidgetInfo minHeight: " + appWidgetInfo.minHeight);
	    	Log.d("HMAWM.createWidget", "appWidgetInfo minResizeWidth: " + appWidgetInfo.minResizeHeight);
	    	Log.d("HMAWM.createWidget", "appWidgetInfo minWidth: " + appWidgetInfo.minWidth);
	    	Log.d("HMAWM.createWidget", "appWidgetInfo minResizeWidth: " + appWidgetInfo.minResizeWidth);
	    	Log.d("HMAWM.createWidget", "appWidgetInfo resizeMode: " + appWidgetInfo.resizeMode);
	    	Log.d("HMAWM.createWidget", "appWidgetInfo updatePeriodMillis: " + appWidgetInfo.updatePeriodMillis);
	    	Log.d("HMAWM.createWidget", "appWidgetInfo widgetCategory: " + appWidgetInfo.widgetCategory);
	    }
	    
	    //create the hostView - this effectively represents our widget
	    AppWidgetHostView hostView = mAppWidgetHost.createView(ctx, appWidgetId, appWidgetInfo);
	    //bizarrely this is needed to tell the hostView about the widget (again!)
	    hostView.setAppWidget(appWidgetId, appWidgetInfo);
	    
	    //store hostView into our widgets map for access later
	    widgetsMap.put(currentWidgetType, hostView);
	    
	    //start the widget listening
	    mAppWidgetHost.startListening();
	    
	    Log.d("HMAWM.createWidget","Widget created and stored of type: " + currentWidgetType);
	}	
	
	/**
	 * Remove the app widget ID as the user has cancelled the process at some point
	 * @param appWidgetId The app widget ID to remove
	 */
	public void deleteAppWidgetId(int appWidgetId) {
		
		Log.d("HMAWM.deleteAppWidgetId","Deleting widget id: " + appWidgetId);
		
		//stop the widget listening
		mAppWidgetHost.stopListening();
		
		mAppWidgetHost.deleteAppWidgetId(appWidgetId);
	}
	
	
	/**
	 * The user has decided they don't want to use the custom widget so let's get rid of it
	 * @param ctx The calling context
	 * @param widgetType The type of widget they are done with
	 */
	public void unregister_widget(Context ctx, String widgetType) {
		
		Log.d("HMAWM.unregister_widget","Unregister widget called with type: " + widgetType);
		
		widgetsMap.remove(widgetType);
		
		//FIXME: Should we also clear up the app widget ID?
	}
	
	/**
	 * Get the specified widget from the map
	 * @param widgetType The type of the widget to get
	 * @return The stored widget
	 */
	public AppWidgetHostView getAppWidgetHostViewByType(String widgetType) {
		
		Log.d("HMAWM.getAppWidgetHostViewByType","Widget requested of type: " + widgetType);
		
		AppWidgetHostView thisWidget = widgetsMap.get(widgetType);;
		
		if (thisWidget == null) Log.w("HMAWM.getAppWidgetHostViewByType","Widget type does not exist in widget Map: " + widgetType);
		
		return thisWidget;
	}
	
	
	/**
	 * Get the specified widget from the map
	 * @param widgetType The type of the widget to get
	 * @return The stored widget
	 */
	public boolean doesWidgetExist(String widgetType) {
		
		Log.d("HMAWM.doesWidgetExist","Checking for Widget of type: " + widgetType);
		
		return (widgetsMap.get(widgetType) != null);
	}
	
}
