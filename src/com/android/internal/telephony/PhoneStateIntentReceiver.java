package com.android.internal.telephony;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;

public class PhoneStateIntentReceiver extends BroadcastReceiver {

	public PhoneStateIntentReceiver(Context ctx, Handler h) {
		
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		
	}

	public void notifySignalStrength(int i) {
		
	}

	public void notifyServiceState(int i) {
		
	}

	public int getSignalStrengthDbm() {
		return 0;
	}

	public int getSignalStrengthLevelAsu() {
		return 0;
	}

	public void registerIntent() {
		
	}

	public void unregisterIntent() {
		
	}
	

}
