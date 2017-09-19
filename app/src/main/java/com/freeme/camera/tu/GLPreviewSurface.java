package com.freeme.camera.tu;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.SurfaceHolder;

import org.lasque.tusdk.api.TuSDKFilterEngine;
import org.lasque.tusdk.core.exif.ExifInterface;
import org.lasque.tusdk.core.seles.SelesParameters;
import org.lasque.tusdk.core.seles.sources.SelesOutInput;
import org.lasque.tusdk.core.struct.TuSdkSize;
import org.lasque.tusdk.core.utils.ContextUtils;
import org.lasque.tusdk.core.utils.TLog;
import org.lasque.tusdk.core.utils.TuSdkDate;
import org.lasque.tusdk.core.utils.hardware.CameraConfigs;
import org.lasque.tusdk.core.utils.hardware.CameraHelper;
import org.lasque.tusdk.core.utils.image.ExifHelper;
import org.lasque.tusdk.core.utils.image.ImageOrientation;
import org.lasque.tusdk.core.utils.sqllite.ImageSqlHelper;
import org.lasque.tusdk.core.utils.sqllite.ImageSqlInfo;

import java.io.IOException;
import java.nio.IntBuffer;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Created by azmohan on 17-9-11.
 */

public class GLPreviewSurface extends GLSurfaceView implements GLSurfaceView.Renderer,
        SurfaceTexture.OnFrameAvailableListener, SurfaceHolder.Callback {
    private int mOESTextureId;
    private SurfaceTexture mSurfaceTexture;
    private Texture2dProgram textureProgram;
    private HandlerThread mHandlerThread;
    private Handler mAsyncHandler;
    private boolean mCameraStarted;
    private Camera mCamera;
    private TuSDKFilterEngine mFilterEngine;
    private int mCameraId;
    private Camera.Size mPreviewSize;
    private final float[] mSurfaceTextureMatrix = new float[16];
    private FilerChangeListener mFilerChangedListener;
    private Context mContext;
    private long mCaptureTime;
    public ExifInterface mMetadata;
    private TuSdkDate mCaptureStartTime;

    public interface FilerChangeListener {
        public void onFilerChanged(SelesOutInput filter);
    }

    public void setFilterChangeListener(FilerChangeListener listener) {
        mFilerChangedListener = listener;
    }


    public GLPreviewSurface(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        getHolder().addCallback(this);
        setEGLContextClientVersion(2);
        setRenderer(this);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

    public void setCameraDisplayOrientation(Activity activity, int cameraId, Camera camera) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);

        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();

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

    private void startCamera() {
        if (mCamera != null) {
            return;
        }

        Camera.CameraInfo info = new Camera.CameraInfo();

        // Try to find a front-facing camera
        int numCameras = Camera.getNumberOfCameras();
        int i = 0;
        for (i = 0; i < numCameras; i++) {
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                mCamera = Camera.open(i);

                mCameraId = i;
                break;
            }
        }
        if (mCamera == null) {
            TLog.d("No front-facing camera found; opening front-back camera");
            for (i = 0; i < numCameras; i++) {
                Camera.getCameraInfo(i, info);
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    mCamera = Camera.open(i);

                    mCameraId = i;
                    break;
                }
            }
        }
        if (mCamera == null) {
            throw new RuntimeException("Unable to open camera");
        }

        Camera.Parameters params = mCamera.getParameters();
        List<Camera.Size> sizes = params.getSupportedPreviewSizes();


        TuSdkSize screenSize = ContextUtils.getScreenSize(getContext());

        // 选择和屏幕比例适配的预览尺寸
        CameraHelper.setPreviewSize(getContext(), params, screenSize.maxSide(), 1.0f);

        mPreviewSize = params.getPreviewSize();
        List<Camera.Size> pictureSizes = params.getSupportedPictureSizes();
        long max = 0;
        Camera.Size maxSize = null;
        for (Camera.Size size : pictureSizes) {
            long multiple = size.width * size.height;
            if (multiple > max) {
                max = multiple;
                maxSize = size;
            }
        }
        TLog.i("max size :" + maxSize.toString());
        params.setPictureSize(maxSize.width, maxSize.height);
        mCamera.setParameters(params);

        TLog.i("mPreviewSize : " + mPreviewSize.width + "   " + mPreviewSize.height);

        // 准备 FilterEngine
        constructFilterEngine();
        mFilterEngine.switchFilter("SkinNature02");
        // 开始相机预览
        tryStartPreview();
    }

    private void stopCamera() {
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }

        if (mSurfaceTexture != null) {
            mSurfaceTexture.release();
            mSurfaceTexture.setOnFrameAvailableListener(null);
            mSurfaceTexture = null;
        }

        mCameraStarted = false;

        destroyFilterEngine();
    }

    private void stopPreview() {
        if (mCamera != null) {
            mCamera.stopPreview();
        }
    }

    private void startPreview() {
        if (mCamera != null) {
            mCamera.startPreview();
        }
    }

    private void tryStartPreview() {
        if (mCameraStarted)
            return;

        if (mCamera != null && mSurfaceTexture != null) {
            try {

                // mCamera.setPreviewCallback(this);

                mCamera.setPreviewTexture(mSurfaceTexture);

                mCamera.startPreview();

                mCameraStarted = true;

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void takePicture() {
        Camera.Parameters parameters = mCamera.getParameters();
        Camera.Size size = parameters.getPictureSize();
        TLog.i("current picture size:" + size.width + "x" + size.height);
        mCamera.takePicture(null, null, new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(byte[] data, Camera camera) {
                stopPreview();
                mMetadata = ExifHelper.getExifInterface(data);
                mCaptureStartTime = TuSdkDate.create();
                mCaptureTime = System.currentTimeMillis();
                mFilterEngine.asyncProcessPictureData(data);
            }

        });
    }

    private void constructFilterEngine() {
        if (mFilterEngine != null) return;

        // 初始化滤镜引擎
        mFilterEngine = new TuSDKFilterEngine(mContext, true);
        mFilterEngine.setOutputOriginalImageOrientation(false);
        // 大眼瘦脸必须开启该配置
        mFilterEngine.setEnableLiveSticker(true);
        mFilterEngine.setCameraFacing(CameraConfigs.CameraFacing.Front);
        mFilterEngine.setOutputOriginalImageOrientation(true);
        mFilterEngine.setDelegate(new TuSDKFilterEngine.TuSDKFilterEngineDelegate() {
            @Override
            public void onPictureDataCompleted(IntBuffer intBuffer, TuSdkSize tuSdkSize) {
                TLog.i("handle sbf time:" + (System.currentTimeMillis() - mCaptureTime) + " ms");

                TLog.d("拍摄处理总耗时: %d ms", mCaptureStartTime.diffOfMillis());

                TuSdkDate date = TuSdkDate.create();

                Bitmap mBitmap = Bitmap.createBitmap(tuSdkSize.width, tuSdkSize.height, Bitmap.Config.ARGB_8888);
                mBitmap.copyPixelsFromBuffer(intBuffer);

                long s1 = date.diffOfMillis();

                TLog.d("buffer -> bitmap taken: %s", s1);

                date = TuSdkDate.create();

                // 将 Bitmap 存入系统相册
                ContentValues values = ImageSqlHelper.build(mBitmap, null, "");
                ImageSqlInfo imageInfo = ImageSqlHelper.saveJpgToAblum(mContext, mBitmap, 100, values);


                if (mMetadata != null) {
                    mMetadata.setTagValue(ExifInterface.TAG_IMAGE_WIDTH, tuSdkSize.width);
                    mMetadata.setTagValue(ExifInterface.TAG_IMAGE_LENGTH, tuSdkSize.height);
                    mMetadata.setTagValue(ExifInterface.TAG_ORIENTATION, ImageOrientation.Up.getExifOrientation());
                    ExifHelper.writeExifInterface(mMetadata, imageInfo.path);
                }

                // 刷新相册
                ImageSqlHelper.notifyRefreshAblum(mContext, imageInfo);

                s1 = date.diffOfMillis();

                TLog.d("save bitmap taken: %s", s1);

                // 拍照完毕后重新启动相机
                startPreview();

//                mSurfaceView.requestRender();

            }

            @Override
            public void onFilterChanged(SelesOutInput filter) {
                if (mFilerChangedListener != null) {
                    mFilerChangedListener.onFilerChanged(filter);
                    return;
                }
                TLog.i("onFilterChanged filter:" + filter + ",thread:" + Thread.currentThread().getName());
                List<SelesParameters.FilterArg> args = filter.getParameter().getArgs();

                for (SelesParameters.FilterArg arg : args) {
                /*
                 * smoothing 润滑
                 * mixed     效果
                 * eyeSize   大眼
                 * chinSize  瘦脸
                 *
                 */
                    if (arg.equalsKey("smoothing")) {
                        // 取值范围： 0 ~ 1.0
                        arg.setPrecentValue(0.9f);
                    } else if (arg.equalsKey("mixed")) {
                        arg.setPrecentValue(1.0f);
                    } else if (arg.equalsKey("eyeSize")) {
                        arg.setPrecentValue(1.0f);
                    } else if (arg.equalsKey("chinSize")) {
                        arg.setPrecentValue(0.0f);
                    }
                }

                filter.submitParameter();
            }
        });
    }

    private boolean isPortraitMode() {
        int rotation = ((Activity) getContext()).getWindowManager().getDefaultDisplay().getRotation();
        boolean ret = false;
        switch (rotation) {
            // 竖屏
            case Surface.ROTATION_0:
                ret = true;
                break;
            case Surface.ROTATION_90:
                ret = false;
                break;
            case Surface.ROTATION_180:
                ret = true;
                break;
            case Surface.ROTATION_270:
                ret = false;
                break;
        }
        return ret;
    }

    private void destroyFilterEngine() {
        if (mFilterEngine == null) return;

        mFilterEngine.destroy();
        mFilterEngine = null;
    }

    private void startDetectThread() {
        mHandlerThread = new HandlerThread("com.tusdk.FrameDetectProcessor");
        mHandlerThread.start();
        mAsyncHandler = new Handler(mHandlerThread.getLooper());
    }

    private void initSurfaceTexture() {
        mOESTextureId = GLUtils.createOESTexture();

        mSurfaceTexture = new SurfaceTexture(mOESTextureId);
        mSurfaceTexture.setOnFrameAvailableListener(this);

        if (textureProgram != null) {
            textureProgram.release();
        }
        // 初始化渲染脚本程序
        textureProgram = new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_2D);

        // 开始相机预览
        tryStartPreview();

        startDetectThread();
    }

    @Override
    public void onResume() {
        super.onResume();
        startCamera();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopCamera();

    }

    @Override
    public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
        startCamera();
        mFilterEngine.onSurfaceCreated();
    }

    @Override
    public void onSurfaceChanged(GL10 gl10, int width, int height) {
        mFilterEngine.onSurfaceChanged(width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl10) {
        if (!mCameraStarted) {
            initSurfaceTexture();
            return;
        }

        GLES20.glClearColor(1.0f, 0.0f, 0.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        // 更新帧
        mSurfaceTexture.updateTexImage();

        // 滤镜引擎处理，返回的 textureID 为 TEXTURE_2D 类型
        final int textureId = mFilterEngine.processFrame(mOESTextureId, mPreviewSize.width, mPreviewSize.height);

        // 设置绘制区域
        // previewSize 为横屏模式，如果当前为竖屏，则要交换宽高
        if (isPortraitMode()) {
            GLES20.glViewport(0, 0, mPreviewSize.height, mPreviewSize.width);
        } else {
            GLES20.glViewport(0, 0, mPreviewSize.width, mPreviewSize.height);
        }

        textureProgram.draw(textureId);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        super.surfaceDestroyed(holder);
        if (mFilterEngine != null) {
            mFilterEngine.onSurfaceDestroy();
            mFilterEngine.destroy();
        }

        mFilterEngine = null;
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        requestRender();
    }

}
