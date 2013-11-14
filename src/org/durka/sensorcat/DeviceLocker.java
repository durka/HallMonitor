package org.durka.sensorcat;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;


public class DeviceLocker extends DeviceAdminReceiver {
	
	@Override
	public void onEnabled(Context context, Intent intent) {
		Toast.makeText(context, "SensorCat device admin enabled!", Toast.LENGTH_SHORT).show();
	}
	
	@Override
	public void onDisabled(Context context, Intent intent) {
		Toast.makeText(context, "SensorCat device admin disabled!", Toast.LENGTH_SHORT).show();
	}

}
