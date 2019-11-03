package com.example.opencvbase;

// ---ライブラリの読み込み---

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.HardwareBuffer;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Parcel;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.widget.ImageView;

import org.opencv.android.Utils;
import org.opencv.core.Mat;

import java.nio.ByteBuffer;
import java.util.Arrays;



public class MainActivity extends AppCompatActivity {
// ---変数の宣言---

    private TextureView textureView;
    private ImageView imageView;
    private  String cameraId;
    protected CameraCaptureSession cameraCaptureSessions;
    protected CaptureRequest captureRequest;
    protected CaptureRequest.Builder captureRequestBuilder;
    protected CameraDevice cameraDevice;
    private Size imageDimension;
    private ImageReader imageReader;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    private Handler oBackgroundHandler;
    private HandlerThread oBackgroundThread;

// ---OpenCVをロード---
    static {
        System.loadLibrary("opencv_java4");
}

// ---変数の定義---

    TextureView.SurfaceTextureListener textureViewListener = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            createCameraPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            cameraDevice.close();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };

    private ImageReader.OnImageAvailableListener imageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireLatestImage();
            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer buffer =plane.getBuffer() ;
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            // Parcel parcel = Parcel.obtain();
            // HardwareBuffer hardwareBuffer = image.getHardwareBuffer();
            // hardwareBuffer.writeToParcel(parcel, 0);
            // byte[] bytes = {};
            // parcel.readByteArray(bytes);
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            image.close();
            Mat mat = new Mat();
            Utils.bitmapToMat(bitmap, mat);
            Utils.matToBitmap(mat, bitmap);
            imageView.setImageBitmap(bitmap);
        }
    };



// ---ユーザー定義の関数---

    protected void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        oBackgroundThread = new HandlerThread("imageProc");
        mBackgroundThread.start();
        oBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
        oBackgroundHandler = new Handler(oBackgroundThread.getLooper());
    }

    protected void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        oBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            oBackgroundThread.join();
            mBackgroundThread = null;
            oBackgroundThread = null;
            mBackgroundHandler = null;
            oBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            cameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[] {Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
            }
            manager.openCamera(cameraId, stateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    protected void createCameraPreview() {
        try {
            // SurfaceTexture texture = textureView.getSurfaceTexture();
            // Surface surface = new Surface(texture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(cameraDevice.TEMPLATE_PREVIEW);
            imageReader = ImageReader.newInstance(imageDimension.getWidth(), imageDimension.getHeight(), ImageFormat.JPEG, 1);
            Surface imageReaderSurface = imageReader.getSurface();
            captureRequestBuilder.addTarget(imageReaderSurface);
            cameraDevice.createCaptureSession(Arrays.asList(imageReaderSurface), new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    if (cameraDevice == null) {
                        return;
                    }
                    cameraCaptureSessions = session;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    protected void updatePreview() {
        if (cameraDevice == null) {
            return;
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        imageReader.setOnImageAvailableListener(imageAvailableListener,oBackgroundHandler);
    }

    // ---メインの関数---
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // textureView = (TextureView) findViewById(R.id.textureView);
        // textureView.setSurfaceTextureListener(textureViewListener);
        imageView = (ImageView) findViewById(R.id.imageView);
        openCamera();
    }
}