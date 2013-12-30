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
import android.widget.ImageButton;
import android.widget.RelativeLayout;

public class CameraHelper {

	private Camera mCamera;
	public static final int MEDIA_TYPE_IMAGE = 1;
	private final DefaultActivity defaultActivity;
	private ImageButton captureButton = null;
	private ImageButton backButton = null;
	
    private RelativeLayout preview = null;
    private CameraPreview mPreview = null;
    
    public static final String KEY_PICTURE_SIZE = "pref_camera_picturesize_key";
	

public CameraHelper(final DefaultActivity theDefaultActivity) {
    
	defaultActivity = theDefaultActivity;
	
    captureButton = new ImageButton(theDefaultActivity.getApplicationContext());
    captureButton.setImageResource(R.drawable.ic_notification);
    
    backButton = new ImageButton(theDefaultActivity.getApplicationContext());
    RelativeLayout.LayoutParams params = 
    	    new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT,
    	        RelativeLayout.LayoutParams.WRAP_CONTENT);
	params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE);
	backButton.setLayoutParams(params);
	backButton.setImageResource(R.drawable.ic_menu_trash_holo_light);
    
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
                    
                    CameraPreview.setCameraDisplayOrientation(defaultActivity, 0, mCamera);
                    
                    mCamera.takePicture(null, null, mPicture);
                    //revert to the standard display
                    preview.removeView(mPreview);
                    preview.removeView(captureButton);
                    preview.removeView(backButton);
                    
                    //set the lock timer back to the default
                    Functions.Actions.setLockTimer(defaultActivity.getApplicationContext());
                   
                }
            }
        );
        
      //Add a listener to the back button
        backButton.setOnClickListener(

            new View.OnClickListener() {

                public void onClick(View v) {
                    preview.removeView(mPreview);
                    preview.removeView(captureButton);
                    preview.removeView(backButton);
                    
                    //set the lock timer back to the default
                    Functions.Actions.setLockTimer(defaultActivity.getApplicationContext());
                   
                }
            }
        );
	}
	
	public void startPreview() {
		
		 Log.d("ch.startPreview", "Starting camera preview");
		 
		 // Create an instance of Camera
		 if (mCamera == null) {
			 mCamera = getCameraInstance();
			 updateCameraParametersPreference();
		 }
		 
		 if (mCamera != null) {
			 
			 Log.d("ch.startPreview", "Camera retrieved.");

		     // Create our Preview view and set it as the content of our activity.
		     mPreview = new CameraPreview(defaultActivity, mCamera);
             
             //Create our Preview view and set it as the content of our activity.
     	     preview = (RelativeLayout) defaultActivity.findViewById(R.id.default_content);
     	     preview.addView(mPreview);
     	     preview.addView(captureButton);
     	     preview.addView(backButton);
     	    
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
	
	
    private void updateCameraParametersPreference() {

    	
    	// get Camera parameters
    	Camera.Parameters params = mCamera.getParameters();

    	List<String> focusModes = params.getSupportedFocusModes();
    	if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
    		// set the focus mode
    		params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
    	}
    	
    	//set the camera picture size
    	initialCameraPictureSize(defaultActivity, params);

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
		mCamera.setParameters(params);

    }
	
	
}


