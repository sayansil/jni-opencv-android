package zds.jnitest;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileUtils;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static android.os.Environment.getExternalStorageDirectory;

public class MainActivity extends AppCompatActivity {

    Handler handler = new Handler();
    Runnable runnable;

    private static final int PERMISSIONS_REQUEST_CODE = 22;
    private static final String TAG = "MainActivity";

    Button btn;
    TextureView img;


    private Handler cameraHandler;
    private HandlerThread cameraThread;

    protected CameraManager cameraManager;
    protected CameraDevice cameraDevice;
    private ImageReader imageReader;
    protected CameraCaptureSession cameraCaptureSessions;
    protected CaptureRequest captureRequest;
    protected CaptureRequest.Builder captureRequestBuilder;

    private Size imageDimension;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }
    private String frontCameraId;

    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            //open your camera here
            openCamera();
        }
        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            // Transform you image captured size according to the surface width and height
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
        public void onOpened(CameraDevice camera) {
            //This is called when the camera is open
            Log.e(TAG, "onOpened");
            cameraDevice = camera;
            createCameraPreview();
        }
        @Override
        public void onDisconnected(CameraDevice camera) {
            cameraDevice.close();
        }
        @Override
        public void onError(CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };

    final CameraCaptureSession.CaptureCallback captureCallbackListener = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            createCameraPreview();
        }
    };

    // JNI library and functions
    static { System.loadLibrary("native-lib"); }
    public native String stringFromJNI();
    public native void doSomething(int[] pixels, int width, int height);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btn = findViewById(R.id.btn);
        img= findViewById(R.id.img);

        cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);

        frontCameraId = null;
        try {
            for (String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics chars
                        = cameraManager.getCameraCharacteristics(cameraId);

                if(chars.get(CameraCharacteristics.LENS_FACING) != null &&
                        CameraCharacteristics.LENS_FACING_FRONT == chars.get(CameraCharacteristics.LENS_FACING)) {
                    frontCameraId = cameraId;
                }
            }
            if (null == frontCameraId) {
                frontCameraId = cameraManager.getCameraIdList()[0];
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        img.setSurfaceTextureListener(textureListener);
        btn.setOnClickListener(v -> takePicture());
    }

    protected void takePicture() {
        if(null == cameraDevice) {
            Log.e(TAG, "cameraDevice is null");
            return;
        }
        try {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraDevice.getId());
            Size[] jpegSizes = null;
            if (characteristics != null) {
                jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
            }
            int w = 640;
            int h = 480;
            if (jpegSizes != null && 0 < jpegSizes.length) {
                w = jpegSizes[0].getWidth();
                h = jpegSizes[0].getHeight();
            }

            final int width = w, height = h;

            ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
            List<Surface> outputSurfaces = new ArrayList<>(2);
            outputSurfaces.add(reader.getSurface());
            outputSurfaces.add(new Surface(img.getSurfaceTexture()));
            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(reader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            // Orientation
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

            ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = null;
                    try {
                        image = reader.acquireLatestImage();
                        doSomethingWithImage(image);
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        if (image != null) {
                            image.close();
                        }
                    }
                }
                private void doSomethingWithImage(Image image) {
//                    File imgFile = new  File("/storage/emulated/0/Pandu/r.png");
//                    Bitmap fbitmapImage = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
//                    int width = fbitmapImage.getWidth();
//                    int height = fbitmapImage.getHeight();

                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.capacity()];
                    buffer.get(bytes);

                    Bitmap bitmapImage = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null);
                    int[] pixels = new int[width * height];

                    bitmapImage.getPixels(pixels, 0, width, 0, 0, width, height);

                    doSomething(pixels, width, height);
                }
            };
            reader.setOnImageAvailableListener(readerListener, cameraHandler);
            final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    createCameraPreview();
                }
            };
            cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    try {
                        session.capture(captureBuilder.build(), captureListener, cameraHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                }
            }, cameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    protected void createCameraPreview() {
        try {
            SurfaceTexture texture = img.getSurfaceTexture();

            assert texture != null;
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());

            Surface surface = new Surface(texture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);

            cameraDevice.createCaptureSession(Collections.singletonList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    //The camera is already closed
                    if (null == cameraDevice) {
                        return;
                    }
                    // When the session is ready, we start displaying the preview.
                    cameraCaptureSessions = cameraCaptureSession;
                    updatePreview();
                }
                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(getApplicationContext(), "Configuration change", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        Log.d(TAG, "Attempting to open Camera");

        try {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(frontCameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Permission not satisfied");
                return;
            }
            manager.openCamera(frontCameraId, stateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        Log.d(TAG, "Camera open");
    }

    private void closeCamera() {
        if (null != cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (null != imageReader) {
            imageReader.close();
            imageReader = null;
        }
    }

    protected void updatePreview() {
        if(null == cameraDevice) {
            Log.e(TAG, "updatePreview error");
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, cameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
        if (img.isAvailable()) {
            openCamera();
        } else {
            img.setSurfaceTextureListener(textureListener);
        }
    }

    @Override
    protected void onStart() {
        runnable = () -> {
            List<String> requiredPermissions = new ArrayList<>();

            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
            if (checkSelfPermission(Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.CAMERA);
            }

            if (requiredPermissions.size() > 0) {
                requestPermissions(requiredPermissions.toArray(new String[0]),
                        PERMISSIONS_REQUEST_CODE);
            }
        };
        handler.postDelayed(runnable, 0);

        super.onStart();
    }

    @Override
    protected void onPause() {
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            for (int grantResult: grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(getApplicationContext(), "Permission nahi, toh kaam nahi", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
        }
    }

    protected void startBackgroundThread() {
        cameraThread = new HandlerThread("Camera Background");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());
    }

    protected void stopBackgroundThread() {
        cameraThread.quitSafely();
        try {
            cameraThread.join();
            cameraThread = null;
            cameraHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
