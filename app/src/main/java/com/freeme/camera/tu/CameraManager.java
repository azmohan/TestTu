package com.freeme.camera.tu;

import android.hardware.Camera;

/**
 * Created by azmohan on 17-9-15.
 */

public class CameraManager {
    private static CameraManager sCameraManager;
    private Camera mCamera;
    private int mCameraId = -1;

    public static CameraManager getInstance() {
        if (sCameraManager == null) {
            sCameraManager = new CameraManager();
        }
        return sCameraManager;
    }

    private CameraManager() {

    }

    public void openCamera(int cameraId) {
        if (mCameraId == cameraId && mCamera != null) {
            return;
        }
        mCamera = Camera.open();
    }

    public void closeCamera() {

    }
}
