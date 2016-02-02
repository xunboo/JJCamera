/*
 * Copyright 2015 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jjcamera.apps.iosched.camera;

import com.jjcamera.apps.iosched.AppApplication;
import com.jjcamera.apps.iosched.R;
import com.jjcamera.apps.iosched.streaming.exceptions.CameraInUseException;
import com.jjcamera.apps.iosched.streaming.mp4.MP4Muxer;
import com.jjcamera.apps.iosched.streaming.rtsp.UriParser;
import com.jjcamera.apps.iosched.ui.BaseActivity;
import com.jjcamera.apps.iosched.ui.widget.DrawShadowFrameLayout;
import com.jjcamera.apps.iosched.util.UIUtils;
import com.jjcamera.apps.iosched.camera.util.CameraHelper;
import com.jjcamera.apps.iosched.streaming.MediaStream;
import com.jjcamera.apps.iosched.streaming.Session;
import com.jjcamera.apps.iosched.streaming.SessionBuilder;
import com.jjcamera.apps.iosched.streaming.audio.AudioQuality;
import com.jjcamera.apps.iosched.streaming.video.VideoQuality;
import com.jjcamera.apps.iosched.streaming.gl.SurfaceView;
import com.jjcamera.apps.iosched.streaming.video.H264Stream;


import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.hardware.Camera;
import android.os.Build;
import android.os.Handler;
import android.view.SurfaceHolder;
//import android.view.SurfaceView;
import android.graphics.PixelFormat;


import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Semaphore;

import static com.jjcamera.apps.iosched.util.LogUtils.LOGD;
import static com.jjcamera.apps.iosched.util.LogUtils.LOGE;
import static com.jjcamera.apps.iosched.util.LogUtils.LOGI;
import static com.jjcamera.apps.iosched.util.LogUtils.LOGV;
import static com.jjcamera.apps.iosched.util.LogUtils.makeLogTag;


public class CameraActivity extends BaseActivity {

    private static final String TAG = makeLogTag(CameraActivity.class);

    private static final String SCREEN_LABEL = "CAMERA";

    private static final int MIN_PREVIEW_PIXELS = 480 * 320;
    private static final double MAX_ASPECT_DISTORTION = 0.15;

    private static CameraHelper mCameraHelper;
    private static int mCurrentCameraId = 0;
    private static Camera.Parameters parameters = null;
    private static Camera cameraInst = null;
    private boolean sCameraRunning = false;

	private static H264Stream mh264Inst;

    static final int FOCUS = 1;
    static final int ZOOM = 2;
    private int mode;
    private float dist;
    private int PHOTO_SIZE = 2000;

    private Handler handler = new Handler();

    private static Camera.Size adapterSize = null;
    private static Camera.Size previewSize = null;

    private View rootView;
    private SurfaceView surfaceView;
	
	private Thread mCameraThread;
	private Looper mCameraLooper;

    private View.OnClickListener mOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.camera_switch:
                    switchCamera();
                    //MP4Muxer.muxerFileDebug();
                    break;
                case R.id.camera_record:
                    sCameraRunning = !sCameraRunning;

					if(sCameraRunning)
						StartRecordVideo();
					else
						StopRecordVideo();
					
                    RefreshMonitorText();
                    break;
            }
        }
    };

    private void RefreshMonitorText(){
        TextView body = (TextView) rootView.findViewById(R.id.camera_main);
        body.setText(sCameraRunning ? R.string.record_start : R.string.record_stop);
    }

    private void recordHelper() {
        new Thread() {
            @Override
            public void run() {
                try {
					int test = 0;
					if(test == 1){
	                    mh264Inst = new H264Stream();
						mh264Inst.enableDebug();
						mh264Inst.setSurfaceView(surfaceView);
						mh264Inst.setCameraInuse(cameraInst);					
	                    mh264Inst.start();
					}

					Session session = UriParser.easyparse();
				 	session.syncConfigure();
					session.syncStart();			
                } catch (Exception e) {
                    e.printStackTrace();
                }

            };
        }.start();
	};

	private void StartRecordVideo(){
		recordHelper();
	}

	private void StopRecordVideo(){
		//mh264Inst.stop();

		Session session = UriParser.getSession();
		session.syncStop();
		session.release();

		UriParser.clearSession();
	}
	

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        rootView = findViewById(R.id.camera_container);
        surfaceView = (SurfaceView)findViewById(R.id.surfaceView);

        findViewById(R.id.masking).setVisibility(View.GONE);

        mCameraHelper = new CameraHelper(this);

		UriParser.clearSession();

        SessionBuilder.getInstance().setSurfaceView(surfaceView);
		SessionBuilder.getInstance().setPreviewOrientation(getRotationDegree());

        RefreshMonitorText();

        rootView.findViewById(R.id.camera_switch).setOnClickListener(mOnClickListener);
        rootView.findViewById(R.id.camera_record).setOnClickListener(mOnClickListener);

        overridePendingTransition(0, 0);

        initView();
    }

    private void initView() {
        SurfaceHolder surfaceHolder = surfaceView.getHolder();
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        surfaceHolder.setKeepScreenOn(true);
        surfaceView.setFocusable(true);
        surfaceView.setBackgroundColor(TRIM_MEMORY_BACKGROUND);
        surfaceView.getHolder().addCallback(new SurfaceCallback());

        boolean canSwitch = false;
        try {
            canSwitch = mCameraHelper.hasFrontCamera() && mCameraHelper.hasBackCamera();
        } catch (Exception e) {
            LOGD(TAG, "mCameraHelper error");
        }
    }
	

    @Override
    protected int getSelfNavDrawerItem() {
        return NAVDRAWER_ITEM_MONITOR;
    }

    private void setContentTopClearance(int clearance) {
        if (rootView != null) {
            rootView.setPadding(rootView.getPaddingLeft(), clearance,
                    rootView.getPaddingRight(), rootView.getPaddingBottom());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        int actionBarSize = UIUtils.calculateActionBarSize(this);
        DrawShadowFrameLayout drawShadowFrameLayout =
                (DrawShadowFrameLayout) findViewById(R.id.main_content);
        if (drawShadowFrameLayout != null) {
            drawShadowFrameLayout.setShadowTopOffset(actionBarSize);
        }
        setContentTopClearance(actionBarSize);

		//StartRecordVideo();
    }

    private void openUrl(String url) {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    private void initCamera() {
        parameters = cameraInst.getParameters();
        parameters.setPictureFormat(PixelFormat.JPEG);

		VideoQuality mQuality = VideoQuality.determineClosestSupportedResolution(parameters, UriParser.videoQuality);
		if(mQuality != UriParser.videoQuality) UriParser.videoQuality = mQuality;

		parameters.setPreviewFpsRange(UriParser.videoQuality.framerate*1000,UriParser.videoQuality.framerate*1000);
				
        //if (adapterSize == null) {
        setUpPicSize(parameters);
        setUpPreviewSize(parameters);
        //}
        if (adapterSize != null) {
			LOGI(TAG, "adapterSize: " + adapterSize.width + "x" + adapterSize.height);
            parameters.setPictureSize(adapterSize.width, adapterSize.height);
        }
        if (previewSize != null) {
			LOGI(TAG, "previewSize: " + previewSize.width + "x" + previewSize.height);
            parameters.setPreviewSize(previewSize.width, previewSize.height);

		//	surfaceView.getHolder().setFixedSize(previewSize.width, previewSize.height);
        }


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);   //focus
        } else {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        }
        setDispaly(parameters, cameraInst);
        try {
            cameraInst.setParameters(parameters);
        } catch (Exception e) {
            e.printStackTrace();
        }
        cameraInst.startPreview();
        cameraInst.cancelAutoFocus();
    }

    private void setUpPicSize(Camera.Parameters parameters) {
        if (adapterSize != null) {
            return;
        } else {
            adapterSize = findBestPictureResolution();
        }
    }

    private void setUpPreviewSize(Camera.Parameters parameters) {
        if (previewSize != null) {
            return;
        } else {
            previewSize = findBestPreviewResolution();
        }
    }

    private Camera.Size findBestPreviewResolution() {
        Camera.Parameters cameraParameters = cameraInst.getParameters();
        Camera.Size defaultPreviewResolution = cameraParameters.getPreviewSize();

        List<Camera.Size> rawSupportedSizes = cameraParameters.getSupportedPreviewSizes();
        if (rawSupportedSizes == null) {
            return defaultPreviewResolution;
        }

        // sort by resolotion for small to big
        List<Camera.Size> supportedPreviewResolutions = new ArrayList<Camera.Size>(rawSupportedSizes);
        Collections.sort(supportedPreviewResolutions, new Comparator<Camera.Size>() {
            @Override
            public int compare(Camera.Size a, Camera.Size b) {
                int aPixels = a.height * a.width;
                int bPixels = b.height * b.width;
                if (bPixels < aPixels) {
                    return -1;
                }
                if (bPixels > aPixels) {
                    return 1;
                }
                return 0;
            }
        });

        StringBuilder previewResolutionSb = new StringBuilder();
        for (Camera.Size supportedPreviewResolution : supportedPreviewResolutions) {
            previewResolutionSb.append(supportedPreviewResolution.width).append('x').append(supportedPreviewResolution.height)
                    .append(' ');
        }
        LOGV(TAG, "Supported preview resolutions: " + previewResolutionSb);


        double screenAspectRatio = (double) AppApplication.getApp().getScreenWidth()
                / (double) AppApplication.getApp().getScreenHeight();
        Iterator<Camera.Size> it = supportedPreviewResolutions.iterator();
        while (it.hasNext()) {
            Camera.Size supportedPreviewResolution = it.next();
            int width = supportedPreviewResolution.width;
            int height = supportedPreviewResolution.height;

            if (width * height < MIN_PREVIEW_PIXELS) {
                it.remove();
                continue;
            }

            boolean isCandidatePortrait = width > height;
            int maybeFlippedWidth = isCandidatePortrait ? height : width;
            int maybeFlippedHeight = isCandidatePortrait ? width : height;
            double aspectRatio = (double) maybeFlippedWidth / (double) maybeFlippedHeight;
            double distortion = Math.abs(aspectRatio - screenAspectRatio);
            if (distortion > MAX_ASPECT_DISTORTION) {
                it.remove();
                continue;
            }

            if (maybeFlippedWidth == AppApplication.getApp().getScreenWidth()
                    && maybeFlippedHeight == AppApplication.getApp().getScreenHeight()) {
                return supportedPreviewResolution;
            }
        }

        if (!supportedPreviewResolutions.isEmpty()) {
            Camera.Size largestPreview = supportedPreviewResolutions.get(0);
            return largestPreview;
        }

        return defaultPreviewResolution;
    }

    private Camera.Size findBestPictureResolution() {
        Camera.Parameters cameraParameters = cameraInst.getParameters();
        List<Camera.Size> supportedPicResolutions = cameraParameters.getSupportedPictureSizes();

        StringBuilder picResolutionSb = new StringBuilder();
        for (Camera.Size supportedPicResolution : supportedPicResolutions) {
            picResolutionSb.append(supportedPicResolution.width).append('x')
                    .append(supportedPicResolution.height).append(" ");
        }
		LOGD(TAG, "Supported picture resolutions: " + picResolutionSb);

        Camera.Size defaultPictureResolution = cameraParameters.getPictureSize();
        LOGD(TAG, "default picture resolution " + defaultPictureResolution.width + "x"
                + defaultPictureResolution.height);

        List<Camera.Size> sortedSupportedPicResolutions = new ArrayList<Camera.Size>(
                supportedPicResolutions);
        Collections.sort(sortedSupportedPicResolutions, new Comparator<Camera.Size>() {
            @Override
            public int compare(Camera.Size a, Camera.Size b) {
                int aPixels = a.height * a.width;
                int bPixels = b.height * b.width;
                if (bPixels < aPixels) {
                    return -1;
                }
                if (bPixels > aPixels) {
                    return 1;
                }
                return 0;
            }
        });

        double screenAspectRatio = (double) AppApplication.getApp().getScreenWidth()
                / (double) AppApplication.getApp().getScreenHeight();
        Iterator<Camera.Size> it = sortedSupportedPicResolutions.iterator();
        while (it.hasNext()) {
            Camera.Size supportedPreviewResolution = it.next();
            int width = supportedPreviewResolution.width;
            int height = supportedPreviewResolution.height;

            boolean isCandidatePortrait = width > height;
            int maybeFlippedWidth = isCandidatePortrait ? height : width;
            int maybeFlippedHeight = isCandidatePortrait ? width : height;
            double aspectRatio = (double) maybeFlippedWidth / (double) maybeFlippedHeight;
            double distortion = Math.abs(aspectRatio - screenAspectRatio);
            if (distortion > MAX_ASPECT_DISTORTION) {
                it.remove();
                continue;
            }
        }

        if (!sortedSupportedPicResolutions.isEmpty()) {
            return sortedSupportedPicResolutions.get(0);
        }

        return defaultPictureResolution;
    }

	private int getRotationDegree(){
		int rotation = this.getWindowManager().getDefaultDisplay().getRotation();
		int degrees = 0;
		switch (rotation) {
		    case Surface.ROTATION_0:
		        degrees = 90;
		        break;
		    case Surface.ROTATION_90:
		        degrees = 0;
		        break;
		    case Surface.ROTATION_180:
		        degrees = 90;
		        break;
		    case Surface.ROTATION_270:
		        degrees = 180;
		        break;
		}	
			
		return degrees;
	}

    private void setDispaly(Camera.Parameters parameters, Camera camera) {
		setDisplayOrientation(camera, getRotationDegree());
    }

    private void setDisplayOrientation(Camera camera, int i) {
        Method downPolymorphic;
        try {
            downPolymorphic = camera.getClass().getMethod("setDisplayOrientation",
                    new Class[]{int.class});
            if (downPolymorphic != null) {
                downPolymorphic.invoke(camera, new Object[]{i});
            }
        } catch (Exception e) {
            LOGE(TAG, "setDisplayOrientation error");
        }
    }

	/**
	 * Opens the camera in a new Looper thread so that the preview callback is not called from the main thread
	 * If an exception is thrown in this Looper thread, we bring it back into the main thread.
	 * @throws RuntimeException Might happen if another app is already using the camera.
	 */
	private void openCamera(final SurfaceHolder holder) throws RuntimeException {
		final Semaphore lock = new Semaphore(0);
		mCameraThread = new Thread(new Runnable() {
			@Override
			public void run() {
				Looper.prepare();
				mCameraLooper = Looper.myLooper();
				try {
					cameraInst = Camera.open();
                    cameraInst.setPreviewDisplay(holder);
                    initCamera();
                    cameraInst.startPreview();	

					SessionBuilder.getInstance().setCameraInst(cameraInst);
				} catch (Throwable e) {
					e.printStackTrace();
				} finally {
					lock.release();
					Looper.loop();
				}
			}
		});
		mCameraThread.start();
		lock.acquireUninterruptibly();
	}

    /*SurfaceCallback*/
    private final class SurfaceCallback implements SurfaceHolder.Callback {

        public void surfaceDestroyed(SurfaceHolder holder) {
            try {
                if (cameraInst != null) {
                    cameraInst.stopPreview();
                    cameraInst.release();
                }
            } catch (Exception e) {
            }
			finally{
		        cameraInst = null;
				mCameraLooper.quit();		
			}
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            if (null == cameraInst) {

				openCamera(holder);
				
            }
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            //autoFocus();
        }
    }

    private void autoFocus() {
        new Thread() {
            @Override
            public void run() {
                try {
                    sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (cameraInst == null) {
                    return;
                }
                cameraInst.autoFocus(new Camera.AutoFocusCallback() {
                    @Override
                    public void onAutoFocus(boolean success, Camera camera) {
                        if (success) {
                            initCamera();
                        }
                    }
                });
            }
        };
    }

    private void switchCamera() {
        mCurrentCameraId = (mCurrentCameraId + 1) % mCameraHelper.getNumberOfCameras();
        releaseCamera();
        LOGD(TAG, "mCurrentCameraId" + mCurrentCameraId);
        setUpCamera(mCurrentCameraId);
    }

    private void releaseCamera() {
        if (cameraInst != null) {
			try {
            	cameraInst.setPreviewCallback(null);
           		cameraInst.release();
			} catch (Exception e) {
            }
			finally{
		        cameraInst = null;
				mCameraLooper.quit();		
			}
        }
        adapterSize = null;
        previewSize = null;
    }

    /**
     * @param mCurrentCameraId2
     */
    private void setUpCamera(int mCurrentCameraId2) {
        cameraInst = getCameraInstance(mCurrentCameraId2);
        if (cameraInst != null) {
            try {
                cameraInst.setPreviewDisplay(surfaceView.getHolder());
                initCamera();
                cameraInst.startPreview();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {

        }
    }

    private Camera getCameraInstance(final int id) {
        Camera c = null;
        try {
            c = mCameraHelper.openCamera(id);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return c;
    }

	public static Camera getCurrentCameraInst(){
		return cameraInst;
	}

}
