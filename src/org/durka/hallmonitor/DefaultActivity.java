package org.durka.hallmonitor;

import android.app.Activity;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.RelativeLayout;

/**
 * 
 */
public class DefaultActivity extends Activity {
	private HMAppWidgetManager hmAppWidgetManager = Functions.hmAppWidgetManager;
	
	private final BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
				if (!Functions.Is.cover_closed(context)) {
				
					// when the cover opens, the fullscreen activity goes poof				
					unregisterReceiver(receiver);
					finish();
				}
			} 
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		//Remove title bar
		this.requestWindowFeature(Window.FEATURE_NO_TITLE);

		//Remove notification bar
		this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

		setContentView(R.layout.activity_default);
		
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
		IntentFilter filter = new IntentFilter();
		filter.addAction(Intent.ACTION_SCREEN_ON);
		registerReceiver(receiver, filter);

	    RelativeLayout contentView = (RelativeLayout)findViewById(R.id.default_content);
	        
	    if (contentView == null) Log.d("nw", "Content View is null!");
	    
	    AppWidgetHostView hostView = hmAppWidgetManager.getAppWidgetHostViewByType("default");
	    
	    if (hostView == null) Log.d("nw", "Host View is null!");
	    
	    contentView.addView(hostView);
	        

		}  

	@Override
	protected void onStart() {
	    super.onStart();
	    hmAppWidgetManager.mAppWidgetHost.startListening();
	}
	@Override
	protected void onStop() {
	    super.onStop();
	    hmAppWidgetManager.mAppWidgetHost.stopListening();
	}
}
