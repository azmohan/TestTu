package com.freeme.camera;

import android.app.Application;

import org.lasque.tusdk.core.TuSdk;

/**
 * Created by azmohan on 17-9-11.
 */

public class CameraApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        TuSdk.enableDebugLog(true);
        TuSdk.init(getApplicationContext(), "d38f9eb36a291fc2-00-7h9fr1");
    }
}
