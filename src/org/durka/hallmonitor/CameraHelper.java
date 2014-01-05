package org.durka.hallmonitor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.Size;
import android.media.CameraProfile;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageButton;
import android.widget.RelativeLayout;

public class CameraHelper {

	public static final int MEDIA_TYPE_IMAGE = 1;

	public static final String KEY_PICTURE_SIZE = "pref_camera_picturesize_key";

	/** A safe way to get an instance of the Camera object. */
	public static Camera getCameraInstance(){
		Camera c = null;
		try {
			c = Camera.open(); // attempt to get a Camera instance
		}
		catch (Exception e){
			// Camera is not available (in use or does not exist)
			Log.e("ch.getCI","Failed to get camera!", e);
		}
		return c; // returns null if camera is unavailable
	}

	public static void initialCameraPictureSize(
			Context context, Parameters parameters) {
		// When launching the camera app first time, we will set the picture
		// size to the first one in the list defined in "arrays.xml" and is also
		// supported by the driver.
		List<Size> supported = parameters.getSupportedPictureSizes();
		if (supported == null) return;
		for (String candidate : context.getResources().getStringArray(
				R.array.pref_camera_picturesize_entryvalues)) {
			if (setCameraPictureSize(candidate, supported, parameters)) {
				return;
			}
		}
		Log.e("ch.initCPS", "No supported picture size found");
	}

	public static boolean setCameraPictureSize(
			String candidate, List<Size> supported, Parameters parameters) {
		int index = candidate.indexOf('x');
		if (index == -1) return false;
		int width = Integer.parseInt(candidate.substring(0, index));
		int height = Integer.parseInt(candidate.substring(index + 1));
		for (Size size : supported) {
			if (size.width == width && size.height == height) {
				parameters.setPictureSize(width, height);
				return true;
			}
		}
		return false;
	}


	public static void updateCameraParametersPreference(Context ctx, Camera cam) {


		// get Camera parameters
		Camera.Parameters params = cam.getParameters();

		List<String> focusModes = params.getSupportedFocusModes();
		if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
			// set the focus mode
			params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
		}

		//set the camera picture size
		initialCameraPictureSize(ctx, params);

		// Set JPEG quality.
		int jpegQuality = CameraProfile.getJpegEncodingQualityParameter(0,
				CameraProfile.QUALITY_HIGH);
		params.setJpegQuality(jpegQuality);

		List<String> flashModes = params.getSupportedFlashModes();
		if (flashModes.contains(Camera.Parameters.FLASH_MODE_AUTO)) {
			// set the focus mode
			params.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
		}

		List<String> whiteBalanceModes = params.getSupportedWhiteBalance();
		if (whiteBalanceModes.contains(Camera.Parameters.WHITE_BALANCE_AUTO)) {
			// set the focus mode
			params.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
		}

		// set Camera parameters
		cam.setParameters(params);

	}


}


