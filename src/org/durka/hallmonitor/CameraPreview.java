package org.durka.hallmonitor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup.LayoutParams;
import android.widget.RelativeLayout;

/** A basic Camera preview class */
public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {
    private SurfaceHolder mHolder;
    private Camera mCamera = null;
    public static final int MEDIA_TYPE_IMAGE = 1;
    
    final PictureCallback mPicture = new PictureCallback() {

        public void onPictureTaken(byte[] data, Camera camera) {

            Log.d("hm-cam", "saving picture to gallery");

            MediaStore.Images.Media.insertImage(Functions.defaultActivity.getContentResolver(),
            									BitmapFactory.decodeByteArray(data, 0, data.length),
            									null, null);
            
            Functions.Actions.end_camera(Functions.defaultActivity);
          }
        };
    
    public CameraPreview(Context ctx, AttributeSet as) {
    	super(ctx, as);
    	
    	Log.d("hm-cam", "context/attributeset constructor");
    	
    	mHolder = getHolder();
    	mHolder.addCallback(this);
    }
    
    public void capture() {
    	Log.d("hm-cam", "capture");
    	mCamera.takePicture(null, null, mPicture);
    }

    public void surfaceCreated(SurfaceHolder holder) {
        // The Surface has been created, now tell the camera where to draw the preview.
    	Log.d("hm-cam", "surface created!");
    	
    	// Create an instance of Camera
		if (mCamera == null) {
			mCamera = CameraHelper.getCameraInstance();
			CameraHelper.updateCameraParametersPreference(Functions.defaultActivity, mCamera);
			
			try {
	            mCamera.setPreviewDisplay(holder);
	            mCamera.startPreview();
	        } catch (IOException e) {
	            Log.d("hm-cam", "Error setting camera preview: " + e.getMessage());
	        }
		}
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        // empty. Take care of releasing the Camera preview in your activity.
    	Log.d("hm-cam", "surface destroyed!");
    	
    	if (mCamera != null){
	        mCamera.release();        // release the camera for other applications
	        mCamera = null;
	    }
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.

    	Log.d("hm-cam", "surface changed!");
    	
        if (mHolder.getSurface() == null || Functions.defaultActivity == null) {
          // preview surface does not exist
          return;
        }

        // stop preview before making changes
        try {
            mCamera.stopPreview();
        } catch (Exception e){
          // ignore: tried to stop a non-existent preview
        }

        // set preview size and make any resize, rotate or
        // reformatting changes here
        setCameraDisplayOrientation(Functions.defaultActivity, 0, mCamera);

        // start preview with new settings
        try {
            mCamera.setPreviewDisplay(mHolder);
            mCamera.startPreview();
        } catch (Exception e){
            Log.d("hm-cam", "Error restarting camera preview: " + e.getMessage());
        }
    }
    
    
    public static void setCameraDisplayOrientation(Activity activity,
            int cameraId, android.hardware.Camera camera) {
        android.hardware.Camera.CameraInfo info =
                new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(cameraId, info);
        int rotation = activity.getWindowManager().getDefaultDisplay()
                .getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0: degrees = 0; break;
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        camera.setDisplayOrientation(result);
    }

    
}