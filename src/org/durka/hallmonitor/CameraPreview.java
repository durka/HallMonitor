package org.durka.hallmonitor;

import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.ImageColumns;
import android.util.AttributeSet;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/** A basic Camera preview class */
public class CameraPreview extends SurfaceView implements
		SurfaceHolder.Callback {
	private final String LOG_TAG = "Hall.camera";

	private final SurfaceHolder mHolder;
	private Camera mCamera = null;
	private final OrientationEventListener mOrientationListener;
	private int mOrientation;
	private final CoreStateManager mStateManager;
	public static final int MEDIA_TYPE_IMAGE = 1;

	public static final String DCIM = Environment
			.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
			.toString();

	public static final String DIRECTORY = DCIM + "/Camera";

	final PictureCallback mPicture = new PictureCallback() {

		@Override
		@SuppressLint("SimpleDateFormat")
		public void onPictureTaken(byte[] data, Camera camera) {

			Log.d(LOG_TAG, "saving picture to gallery");

			// Create a media file name
			String title = "IMG_"
					+ new SimpleDateFormat("yyyyMMdd_HHmmss")
							.format(new Date());

			String path = DIRECTORY + '/' + title + ".jpg";

			FileOutputStream out = null;
			try {
				out = new FileOutputStream(path);
				out.write(data);
			} catch (Exception e) {
				Log.e("hm-cam", "Failed to write data", e);
			} finally {
				try {
					out.close();
				} catch (Exception e) {
					Log.e("hm-cam", "Failed to close file after write", e);
				}
			}

			// Insert into MediaStore.
			ContentValues values = new ContentValues(5);
			values.put(ImageColumns.TITLE, title);
			values.put(ImageColumns.DISPLAY_NAME, title + ".jpg");
			values.put(ImageColumns.DATE_TAKEN, System.currentTimeMillis());
			values.put(ImageColumns.DATA, path);
			// Clockwise rotation in degrees. 0, 90, 180, or 270.
			Log.d(LOG_TAG, "saving photo with orientation=" + mOrientation);
			values.put(ImageColumns.ORIENTATION, mOrientation);

			@SuppressWarnings("unused")
			Uri uri = null;
			try {
				uri = mStateManager.getDefaultActivity().getContentResolver()
						.insert(Images.Media.EXTERNAL_CONTENT_URI, values);
			} catch (Throwable th) {
				// This can happen when the external volume is already mounted,
				// but
				// MediaScanner has not notify MediaProvider to add that volume.
				// The picture is still safe and MediaScanner will find it and
				// insert it into MediaProvider. The only problem is that the
				// user
				// cannot click the thumbnail to review the picture.
				Log.e("cp.pictureTaken", "Failed to write MediaStore" + th);
			}

			mStateManager.getDefaultActivity().stopCamera();
			mStateManager.getDefaultActivity().startCamera();
		}
	};

	public CameraPreview(Context ctx) {
		this(ctx, null, 0);
	}

	public CameraPreview(Context ctx, AttributeSet as) {
		this(ctx, as, 0);
	}

	public CameraPreview(Context ctx, AttributeSet as, int defStyle) {
		super(ctx, as, defStyle);

		Log.d(LOG_TAG, "context/attributeset/defstyle constructor");

		mStateManager = ((CoreApp) ctx.getApplicationContext())
				.getStateManager();

		mHolder = getHolder();
		mHolder.addCallback(this);

		mOrientationListener = new OrientationEventListener(ctx,
				SensorManager.SENSOR_DELAY_NORMAL) {

			@Override
			public void onOrientationChanged(int angle) {
				mOrientation = ((int) (Math.round(angle / 90.0) * 90) + 90) % 360;
			}

		};
	}

	public void capture() {
		Log.d(LOG_TAG, "capture");
		mCamera.takePicture(null, null, mPicture);
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		// The Surface has been created, now tell the camera where to draw the
		// preview.
		Log.d(LOG_TAG, "surface created!");

		mOrientationListener.enable();

		// Create an instance of Camera
		if (mCamera == null) {
			mCamera = CameraHelper.getCameraInstance();
			CameraHelper.updateCameraParametersPreference(
					mStateManager.getDefaultActivity(), mCamera);

			try {
				mCamera.setPreviewDisplay(holder);
				mCamera.startPreview();
			} catch (IOException e) {
				Log.e(LOG_TAG,
						"Error setting camera preview: " + e.getMessage());
			}
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		// empty. Take care of releasing the Camera preview in your activity.
		Log.d(LOG_TAG, "surface destroyed!");

		mOrientationListener.disable();

		if (mCamera != null) {
			mCamera.release(); // release the camera for other applications
			mCamera = null;
		}
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
		// If your preview can change or rotate, take care of those events here.
		// Make sure to stop the preview before resizing or reformatting it.

		Log.d(LOG_TAG, "surface changed!");

		if (mHolder.getSurface() == null
				|| mStateManager.getDefaultActivity() == null) {
			// preview surface does not exist
			return;
		}

		// stop preview before making changes
		try {
			mCamera.stopPreview();
		} catch (Exception e) {
			// ignore: tried to stop a non-existent preview
		}

		// set preview size and make any resize, rotate or
		// reformatting changes here
		setCameraDisplayOrientation(mStateManager.getDefaultActivity(), 0,
				mCamera);

		// start preview with new settings
		try {
			mCamera.setPreviewDisplay(mHolder);
			mCamera.startPreview();
		} catch (Exception e) {
			Log.d(LOG_TAG, "Error restarting camera preview: " + e.getMessage());
		}
	}

	public static void setCameraDisplayOrientation(Activity activity,
			int cameraId, android.hardware.Camera camera) {
		android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
		android.hardware.Camera.getCameraInfo(cameraId, info);
		int rotation = activity.getWindowManager().getDefaultDisplay()
				.getRotation();
		int degrees = 0;
		switch (rotation) {
		case Surface.ROTATION_0:
			degrees = 0;
			break;
		case Surface.ROTATION_90:
			degrees = 90;
			break;
		case Surface.ROTATION_180:
			degrees = 180;
			break;
		case Surface.ROTATION_270:
			degrees = 270;
			break;
		}

		int result;
		if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
			result = (info.orientation + degrees) % 360;
			result = (360 - result) % 360; // compensate the mirror
		} else { // back-facing
			result = (info.orientation - degrees + 360) % 360;
		}
		camera.setDisplayOrientation(result);
	}

}