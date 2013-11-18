package org.durka.hallmonitor;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;


public class AdminReceiver extends DeviceAdminReceiver {
	
	@Override
	public void onEnabled(Context context, Intent intent) {
		Functions.Events.device_admin_status(context, true);
	}
	
	@Override
	public void onDisabled(Context context, Intent intent) {
		Functions.Events.device_admin_status(context, false);
	}

}
