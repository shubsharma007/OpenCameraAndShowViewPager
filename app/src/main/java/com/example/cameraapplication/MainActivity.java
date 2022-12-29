package com.example.cameraapplication;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Camera;
import android.graphics.ImageFormat;
import android.graphics.PathMeasure;
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
import android.nfc.NfcAdapter;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;

import com.example.cameraapplication.databinding.ActivityMainBinding;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    ActivityMainBinding binding;

    public static final String TAG = "AndroidCameraApi";
    public static final SparseIntArray ORIENTATIONS = new SparseIntArray ();

    static {
        ORIENTATIONS.append (Surface.ROTATION_0, 90);
        ORIENTATIONS.append (Surface.ROTATION_90, 0);
        ORIENTATIONS.append (Surface.ROTATION_180, 270);
        ORIENTATIONS.append (Surface.ROTATION_270, 180);
    }

    private String cameraId;
    protected CameraDevice cameraDevice;
    protected CameraCaptureSession cameraCaptureSessions;
    protected CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private ImageReader imageReader;
    private File file;
    private File folder;
    private String folderName = "Clips";
    public static final int REQUEST_CAMERA_PERMISSION = 200;

    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate (savedInstanceState);
        binding = ActivityMainBinding.inflate (getLayoutInflater ());
        setContentView (binding.getRoot ());

        binding.textureView.setSurfaceTextureListener (textureListener);

        binding.btnTack.setOnClickListener (v -> {
            tackPicture ();
        });

        binding.btnView.setOnClickListener (v -> {
            startActivity (new Intent (this, CustomGallery.class));
        });
    }

    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener () {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
            openCamera ();
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

        }
    };

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback () {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {

            //This is called when the camera is open
            Log.e (TAG, "onOpened");
            cameraDevice = camera;
            createCameraPreview ();

        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            cameraDevice.close ();

        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            cameraDevice.close ();
            cameraDevice = null;
        }
    };

    protected void startBackgroundThread() {
        mBackgroundThread = new HandlerThread ("Camera Background");
        mBackgroundThread.start ();
        mBackgroundHandler = new Handler (mBackgroundThread.getLooper ());
    }

    protected void stopBackgroundThread() {
        mBackgroundThread.quitSafely ();
        try {
            mBackgroundThread.join ();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace ();
        }
    }

    protected void tackPicture() {

        if (cameraDevice == null) {
            Log.e (TAG, "cameraDevice is null");
            return;
        }
        if (!isExternalStorageAvailableForRW () || isExternalStorageReadOnly ()) {
            binding.btnTack.setEnabled (false);
        }
        if (isStoragePermissionGranted ()) {
            CameraManager manager = (CameraManager) getSystemService (Context.CAMERA_SERVICE);
            try {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics (cameraDevice.getId ());
                Size[] jpegSizes = null;
                if (characteristics != null) {
                    jpegSizes = characteristics.get (CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes (ImageFormat.JPEG);
                }
                int width = 640;
                int height = 480;
                if (jpegSizes != null && jpegSizes.length > 0) {
                    width = jpegSizes[0].getWidth ();
                    height = jpegSizes[0].getHeight ();
                }
                ImageReader reader = ImageReader.newInstance (width, height, ImageFormat.JPEG, 1);
                List<Surface> outputSurfaces = new ArrayList<Surface> (2);
                outputSurfaces.add (reader.getSurface ());
                outputSurfaces.add (new Surface (binding.textureView.getSurfaceTexture ()));
                final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest (CameraDevice.TEMPLATE_STILL_CAPTURE);
                captureBuilder.addTarget (reader.getSurface ());
                captureBuilder.set (CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                //Orientation
                int rotation = getWindowManager ().getDefaultDisplay ().getRotation ();
                captureBuilder.set (CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get (rotation));
                file = null;
                folder = new File (folderName);
                String timeStamp = new SimpleDateFormat ("yyyyMMdd_HHmmss").format (new Date ());
                String imageFileName = "IMG_" + timeStamp + ".jpg";
                file = new File (getExternalFilesDir (folderName), "/" + imageFileName);
                if (!folder.exists ()) {
                    folder.mkdirs ();
                }
                ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener () {
                    @Override
                    public void onImageAvailable(ImageReader reader) {
                        Image image = null;
                        try {
                            image = reader.acquireLatestImage ();
                            ByteBuffer buffer = image.getPlanes ()[0].getBuffer ();
                            byte[] bytes = new byte[buffer.capacity ()];
                            buffer.get (bytes);
                            save (bytes);
                        } catch (FileNotFoundException e) {
                            e.printStackTrace ();
                        } catch (IOException e) {
                            e.printStackTrace ();
                        } finally {
                            if (image != null) {
                                image.close ();
                            }
                        }
                    }

                    private void save(byte[] bytes) throws IOException {
                        OutputStream output = null;
                        try {
                            output = new FileOutputStream (file);
                            output.write (bytes);
                        } finally {
                            if (null != output) {
                                output.close ();
                            }
                        }
                    }
                };
                reader.setOnImageAvailableListener (readerListener, mBackgroundHandler);
                final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback () {
                    @Override
                    public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                        super.onCaptureCompleted (session, request, result);
                        Toast.makeText (MainActivity.this, "Saved", Toast.LENGTH_SHORT).show ();
                        Log.d (TAG, "" + file);
                        createCameraPreview ();
                    }
                };
                cameraDevice.createCaptureSession (outputSurfaces, new CameraCaptureSession.StateCallback () {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        try {
//                            CameraCaptureSession.CaptureCallback captureListener = null;
                            session.capture (captureBuilder.build (), captureListener, mBackgroundHandler);
                        } catch (CameraAccessException e) {
                            e.printStackTrace ();
                        }
                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    }

                }, mBackgroundHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace ();
            }
        }
    }


    private static boolean isExternalStorageReadOnly() {
        String extStorage = Environment.getExternalStorageState ();
        if (Environment.MEDIA_MOUNTED_READ_ONLY.equals (extStorage)) {
            return true;
        }
        return false;
    }

    private boolean isExternalStorageAvailableForRW() {
        String extStorage = Environment.getExternalStorageState ();
        if (extStorage.equals (Environment.MEDIA_MOUNTED)) {
            return true;
        }
        return false;
    }

    private boolean isStoragePermissionGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission (Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                //Permission is granted
                return true;
            } else {
                //Permission is revoked
                ActivityCompat.requestPermissions (this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                return false;
            }
        } else {
            //Permission is automatically granted  on SDK<23 upon installation
            return true;
        }
    }

    protected void createCameraPreview() {
        try {
            SurfaceTexture texture = binding.textureView.getSurfaceTexture ();
            assert texture != null;
            texture.setDefaultBufferSize (imageDimension.getWidth (), imageDimension.getHeight ());
            Surface surface = new Surface (texture);
            captureRequestBuilder = cameraDevice.createCaptureRequest (CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget (surface);
            cameraDevice.createCaptureSession (Arrays.asList (surface), new CameraCaptureSession.StateCallback () {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    //The Camera is already close
                    if (null == cameraDevice) {
                        return;
                    }
                    cameraCaptureSessions = cameraCaptureSession;
                    updatePreview ();
//                    if (null == cameraDevice) {
//                        Log.e (TAG, "updatePreview error ,return");
//                    }
//                    captureRequestBuilder.set (CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
//                    try {

//                        cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
//                    } catch (CameraAccessException e) {
//                        e.printStackTrace();
//                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText (MainActivity.this, "Configuration change", Toast.LENGTH_SHORT).show ();
                }

            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace ();
        }
    }

    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService (Context.CAMERA_SERVICE);
        Log.e (TAG, "is Camera Open...");
        try {
            cameraId = manager.getCameraIdList ()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics (cameraId);
            StreamConfigurationMap map = characteristics.get (CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes (SurfaceTexture.class)[0];
            //add permission for camera and let user grant the permission
            if (ActivityCompat.checkSelfPermission (this, Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission (this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions (MainActivity.this, new String[]{Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
                return;
            }
            manager.openCamera (cameraId, stateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace ();
        }
        Log.e (TAG, "openCamera X");
    }

    protected void updatePreview() {

        if(null == cameraDevice){
            Log.e (TAG, "updatePreview error, return");
        }
        captureRequestBuilder.set (CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        try {
            cameraCaptureSessions.setRepeatingRequest (captureRequestBuilder.build (), null, mBackgroundHandler);
        }catch (CameraAccessException e){
            e.printStackTrace ();
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult (requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                //close the application...
                Toast.makeText (this, "Sorry!!!, You can't use  this  app without granting permission", Toast.LENGTH_SHORT).show ();
                finish ();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume ();
        Log.e (TAG, "onResume");
        startBackgroundThread ();
        if (binding.textureView.isAvailable ()) {
            openCamera ();
        } else {
            binding.textureView.setSurfaceTextureListener (textureListener);
        }
    }

    @Override
    protected void onPause() {
        Log.e (TAG, "onPause");
        //Close Camera
        startBackgroundThread ();
        super.onPause ();
    }
}
