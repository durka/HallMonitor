package org.durka.hallmonitor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.PreviewCallback;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;

public class CameraHelper {

	private Camera mCamera;
	public static final int MEDIA_TYPE_IMAGE = 1;
	private static final int IMAGE_W = 310;
	private static final int IMAGE_H = 125;
	private final DefaultActivity defaultActivity;
	private Button captureButton = null;
	
    private RelativeLayout preview = null;
    private CameraPreview mPreview = null;
	

public CameraHelper(final DefaultActivity theDefaultActivity) {
    
	defaultActivity = theDefaultActivity;
	
    captureButton = new Button(theDefaultActivity.getApplicationContext());
    Log.d("ch.constructor", "CameraHelper Starting");

    final PictureCallback mPicture = new PictureCallback() {

        public void onPictureTaken(byte[] data, Camera camera) {

            File pictureFile = getOutputMediaFile(MEDIA_TYPE_IMAGE);

            if (pictureFile == null){
                return;
            }

            try {
                FileOutputStream fos = new FileOutputStream(pictureFile);
                fos.write(data);
                fos.close();
                MediaStore.Images.Media.insertImage(defaultActivity.getContentResolver(), pictureFile.getAbsolutePath(), pictureFile.getName(), pictureFile.getName());
            } catch (FileNotFoundException e) {

            } catch (IOException e) {

            }
          }
        };


        //Add a listener to the Capture button
        captureButton.setOnClickListener(

            new View.OnClickListener() {

                public void onClick(View v) {
                    // get an image from the camera   
                    System.out.println("Photo Taking!");
                    mCamera.takePicture(null, null, mPicture);
                    //revert to the standard display
                    preview.removeView(mPreview);
                    preview.removeView(captureButton);
                    
                    //set the lock timer back to the default
                    Functions.Actions.setLockTimer(defaultActivity.getApplicationContext());
                   
                }
            }
        );
	}
	
	public void startPreview() {
		
		 Log.d("ch.startPreview", "Starting camera preview");
		 
		 // Create an instance of Camera
		 mCamera = getCameraInstance();
		 
		 if (mCamera != null) {
			 
			 Log.d("ch.startPreview", "Camera retrieved.");

		     // Create our Preview view and set it as the content of our activity.
		     mPreview = new CameraPreview(defaultActivity.getApplicationContext(), mCamera);
             
             //Create our Preview view and set it as the content of our activity.
     	     preview = (RelativeLayout) defaultActivity.findViewById(R.id.default_content);
     	     //preview.removeAllViews();
     	     preview.addView(mPreview);
     	     preview.addView(captureButton);
     	    
     	     Log.d("ch.startPreview", "Preview added to view.");
         }
	}


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



	public void releaseCamera(){
	    if (mCamera != null){
	        mCamera.release();        // release the camera for other applications
	        mCamera = null;
	    }
	}



	/** Create a File for saving an image or video */
	private  File getOutputMediaFile(int type){
	    // To be safe, you should check that the SDCard is mounted
	    // using Environment.getExternalStorageState() before doing this.
	
	    File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
	              Environment.DIRECTORY_PICTURES), "MyCameraApp");
	
	
	    // This location works best if you want the created images to be shared
	    // between applications and persist after your app has been uninstalled.
	
	    // Create the storage directory if it does not exist
	    if (! mediaStorageDir.exists()){
	        if (! mediaStorageDir.mkdirs()){
	            return null;
	        }
	    }
	
	    // Create a media file name
	    String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
	    File mediaFile;
	    if (type == MEDIA_TYPE_IMAGE){
	        mediaFile = new File(mediaStorageDir.getPath() + File.separator +
	        "IMG_"+ timeStamp + ".jpg");
	    } else {
	        return null;
	    }
	
	    return mediaFile;
	}
}


