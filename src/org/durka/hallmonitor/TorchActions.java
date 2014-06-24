/* Copyright 2013 Zanin Marco

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

import org.durka.hallmonitor.Functions.Actions;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.util.Log;
import android.widget.Toast;

public class TorchActions {
	
	/**
	 * With this non-CM users can use torch button in HallMonitor.
	 * Should (Hopefully) work on every device with SystemFeature FEATURE_CAMERA_FLASH
	 * This code has been tested on I9505 jflte with ParanoidAndroid 4.4 rc2
	 */
	 
    private static Camera camera;
    public static boolean flashIsOn = false;
    public static boolean deviceHasFlash;

    // Turn On Flash
    public static void turnOnFlash() {
    	camera = Camera.open();
    	Parameters p = camera.getParameters();
    	p.setFlashMode(Parameters.FLASH_MODE_TORCH);
    	camera.setParameters(p);
    	camera.startPreview();
    	flashIsOn = true;
    	Log.d("torch", "turned on!");
	}

    // Turn Off Flash
    public static void turnOffFlash() {
    	Parameters p = camera.getParameters();
    	p.setFlashMode(Parameters.FLASH_MODE_OFF);
    	camera.setParameters(p);
    	camera.stopPreview();
    	flashIsOn = false;
    	// Be sure to release the camera when the flash is turned off
    	if (camera != null) {
    		camera.release();
    		camera = null;
    		Log.d("torch", "turned off and camera released!");
    	}
	}

    public static void toggle_torch_alternative(DefaultActivity da) {
    	if (!flashIsOn) {
    		turnOnFlash();
    		da.torchButton2.setImageResource(R.drawable.ic_appwidget_torch_on);
    		if (Actions.timerTask != null) Actions.timerTask.cancel();
    } else {
    		turnOffFlash();
    		da.torchButton2.setImageResource(R.drawable.ic_appwidget_torch_off);
    		Actions.close_cover(da);
    	}
	}
    
    public static void deviceHasFlash(Context ctx) { 
    	deviceHasFlash = ctx.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);        
    }
}


