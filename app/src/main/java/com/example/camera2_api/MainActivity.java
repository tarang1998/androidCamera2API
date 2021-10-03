package com.example.camera2_api;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private TextureView textureView;
    private TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {

            //Toast.makeText(getApplicationContext(),"Texture View available",Toast.LENGTH_SHORT).show();
            Log.d("DEBUG_TEST","Texture View Available");


            setUpCamera(width,height);
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

    private CameraDevice cameraDevice;
    private CameraDevice.StateCallback cameraDeviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
            cameraDevice =  null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            camera.close();
            cameraDevice = null;

        }
    };

    private String cameraId;
    private HandlerThread backgroundHandlerThread ;
    private Handler backgroundHandler;
    private static SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0,0);
        ORIENTATIONS.append(Surface.ROTATION_90,90);
        ORIENTATIONS.append(Surface.ROTATION_180,180);
        ORIENTATIONS.append(Surface.ROTATION_270,270);

    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        //Toast.makeText(getApplicationContext(),"App Activity Created",Toast.LENGTH_SHORT).show();
        Log.d("DEBUG_TEST","App Activity Created");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textureView = (TextureView) findViewById(R.id.textureView);
    }

    @Override
    protected  void onResume(){
        super.onResume();

        //Toast.makeText(getApplicationContext(),"App Activity Resumed",Toast.LENGTH_SHORT).show();
        Log.d("DEBUG_TEST","App Activity Resumed");


        startBackgroundThread();


        if(textureView.isAvailable()){

            setUpCamera(textureView.getWidth(),textureView.getHeight());

        }
        else{
            textureView.setSurfaceTextureListener(surfaceTextureListener);
        }
    }

    @Override
    protected void onPause(){

        //Toast.makeText(getApplicationContext(),"App Activity Paused",Toast.LENGTH_SHORT).show();
        Log.d("DEBUG_TEST","App Activity Paused");


        closeCamera();


        stopBackgroundThread();

        super.onPause();


    }


    @Override
    public void onWindowFocusChanged(boolean hasFocus){
        super.onWindowFocusChanged(hasFocus);
        View decorView = getWindow().getDecorView();

        if(hasFocus){
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            );
        }
    }

    private void setUpCamera(int width, int height){

        //Toast.makeText(getApplicationContext(),"Setting Up the Camera",Toast.LENGTH_SHORT).show();
        Log.d("DEBUG_TEST","Setting up the camera");
        Log.d("DEBUG_TEST","Texture View Width : " + Integer.toString(width) );
        Log.d("DEBUG_TEST","Texture View Height : " + Integer.toString(height) );



        CameraManager cameraManager = (CameraManager)getSystemService(Context.CAMERA_SERVICE);
        try {
            for(String id : cameraManager.getCameraIdList()){
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(id);
                if(cameraCharacteristics.get(CameraCharacteristics.LENS_FACING)== CameraCharacteristics.LENS_FACING_FRONT){
                    continue;
                }
                int deviceOrientation = getWindowManager().getDefaultDisplay().getRotation();
                int totalRotation = sensorToDeviceRotation(cameraCharacteristics,deviceOrientation);
                boolean swapRotation = totalRotation == 90 || totalRotation == 270 ;
                int rotatedWidth = width;
                int rotatedHeight = height;
                if(swapRotation){

                    Log.d("DEBUG_TEST","Swapping the width and height" );

                    rotatedWidth = height;
                    rotatedHeight = width;
                }
                cameraId = id;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera(){

        //Toast.makeText(getApplicationContext(),"Closing the Camera",Toast.LENGTH_SHORT).show();
        Log.d("DEBUG_TEST","Closing the camera");


        if(cameraDevice!=null){
            cameraDevice.close();
            cameraDevice= null;
        }
    }

    private void startBackgroundThread(){

        //Toast.makeText(getApplicationContext(),"Starting the background thread",Toast.LENGTH_SHORT).show();
        Log.d("DEBUG_TEST","Starting the background thread");


        backgroundHandlerThread = new HandlerThread("Camera2VideoAudio");
        backgroundHandlerThread.start();
        backgroundHandler = new Handler(backgroundHandlerThread.getLooper());
    }

    private void stopBackgroundThread(){

        //Toast.makeText(getApplicationContext(),"Stopping the background thread",Toast.LENGTH_SHORT).show();
        Log.d("DEBUG_TEST","Stopping the background thread");


        backgroundHandlerThread.quitSafely();
        try {
            backgroundHandlerThread.join();
            backgroundHandlerThread = null;
            backgroundHandler = null;

        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    private static int sensorToDeviceRotation(CameraCharacteristics cameraCharacteristics, int deviceOrientation){
        int sensorOrientation =  cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

        Log.d("DEBUG_TEST","Sensor Orientation : " + Integer.toString(sensorOrientation) + " DeviceOrientation : " + Integer.toString(deviceOrientation));

        deviceOrientation = ORIENTATIONS.get(deviceOrientation);

        Log.d("DEBUG_TEST","Transformed device Orientation : " + Integer.toString(deviceOrientation));

        int totalRotation = (sensorOrientation+deviceOrientation+360)%360;

        Log.d("DEBUG_TEST","Total Rotation : " + Integer.toString(totalRotation));


        return totalRotation;

    }
}