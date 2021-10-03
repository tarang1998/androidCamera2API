package com.example.camera2_api;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Toast;


import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CAMERA_PERMISSIONS_RESULT = 0;

    private TextureView textureView;
    private TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {

            //Toast.makeText(getApplicationContext(),"Texture View available",Toast.LENGTH_SHORT).show();
            Log.d("DEBUG_TEST","Texture View Available");


            setUpCamera(width,height);
            connectCamera();
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
            Toast.makeText(getApplicationContext(),"Camera Connections Made!!!",Toast.LENGTH_SHORT).show();
            Log.d("DEBUG_TEST","Camera Connections Successfully Created");
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
    private Size previewSize;
    private HandlerThread backgroundHandlerThread ;
    private Handler backgroundHandler;
    //Device Orientation into degrees
    private static SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0,0);
        ORIENTATIONS.append(Surface.ROTATION_90,90);
        ORIENTATIONS.append(Surface.ROTATION_180,180);
        ORIENTATIONS.append(Surface.ROTATION_270,270);

    }

    //A class for comparisons between the different resolutions of the preview
    private static class CompareSizeByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() /
                    (long) rhs.getWidth() * rhs.getHeight());
        }
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
            connectCamera();

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
    public void onRequestPermissionsResult(int requestCode , String[] permissions, int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode == REQUEST_CAMERA_PERMISSIONS_RESULT){
            if(grantResults[0] != PackageManager.PERMISSION_GRANTED){
                Toast.makeText(getApplicationContext(),"Failed to grant camera permissions",Toast.LENGTH_SHORT).show();
            }
        }
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
                //Contains list of the various resolutions for the camera preview
                StreamConfigurationMap map  = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                int deviceOrientation = getWindowManager().getDefaultDisplay().getRotation();
                int totalRotation = sensorToDeviceRotation(cameraCharacteristics,deviceOrientation);
                boolean swapRotation = totalRotation == 90 || totalRotation == 270 ;
                int rotatedWidth = width;
                int rotatedHeight = height;
                //whether or not the device is in landscape or portrait mode
                //We are forcing the values to go into a landscape size because the preview Resolutions are in landscape size
                if(swapRotation){

                    Log.d("DEBUG_TEST","Swapping the width and height" );

                    rotatedWidth = height;
                    rotatedHeight = width;

                    Log.d("DEBUG_TEST","Updated Texture View Width : " + Integer.toString(rotatedWidth) );

                    Log.d("DEBUG_TEST","Updated Texture View Height : " + Integer.toString(rotatedHeight) );

                }
                //We now need to find the previewResolution of the camera sensor matching the texture view resolution
                previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),rotatedWidth,rotatedHeight);

                Log.d("DEBUG_TEST","Optimal Preview Size Width : " + Integer.toString(previewSize.getWidth()) );

                Log.d("DEBUG_TEST","Updated Texture View Height  : " + Integer.toString(previewSize.getHeight()) );

                cameraId = id;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void  connectCamera(){

        Log.d("DEBUG_TEST"," Connecting to the Camera");


        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try{

            Log.d("DEBUG_TEST"," Checking CameraPermissions");

            //permissions needed to access camera for version beyond 23
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
                if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)==
                        PackageManager.PERMISSION_GRANTED){

                }else{
                    //If you have denied access to the camera Previously
                    if(shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)){
                        Toast.makeText(this,"The app requires Camera Permissions", Toast.LENGTH_SHORT).show();
                    }
                    //Trying to access the camera permissions
                    //The result would be received in the onRequestPermissionsResult Callback
                    requestPermissions(new String [] {Manifest.permission.CAMERA},REQUEST_CAMERA_PERMISSIONS_RESULT);
                }
            }
            else{
                cameraManager.openCamera(cameraId,cameraDeviceStateCallback,backgroundHandler);
            }
        }
        catch(CameraAccessException error){
            error.printStackTrace();
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

    //Device supports different orientation  - Portrait and Landscape
    //Camera Sensor also supports different orientation - Portrait and Landscape
    //Orientation of the sensor might not match the orientation of the device
    //sensor has number of preview resolutions and they tend to be setup in landscape mode
    //So while in portrait mode their height and width need to be swapped


    private static int sensorToDeviceRotation(CameraCharacteristics cameraCharacteristics, int deviceOrientation){
        int sensorOrientation =  cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

        Log.d("DEBUG_TEST","Sensor Orientation : " + Integer.toString(sensorOrientation) + " DeviceOrientation : " + Integer.toString(deviceOrientation));

        deviceOrientation = ORIENTATIONS.get(deviceOrientation);

        Log.d("DEBUG_TEST","Transformed device Orientation : " + Integer.toString(deviceOrientation));

        int totalRotation = (sensorOrientation+deviceOrientation+360)%360;

        Log.d("DEBUG_TEST","Total Rotation : " + Integer.toString(totalRotation));


        return totalRotation;

    }

    //choices : Array of preview resolutions from the sensor
    //width : Updated Texture View Width
    //height : Updated Texture View Height
    private static Size chooseOptimalSize(Size[] choices, int width , int height ){

        //If the resolution of the sensor is big enough for the display
        List<Size> bigEnough = new ArrayList<>();
        Size selectedPreviewResolution = new Size(0,0);
        for(Size option : choices){
            //Choosing a appropriate Sensor Orientation
            //Aspect ratio of the preview resolution matches that of the texture view
            //Preview Resolution width is greater than or equal to the updated texture view height and the width
            if( option.getHeight() ==  option.getWidth() * height/width &&
                option.getWidth()>=width &&
                option.getHeight()>= width){
                    bigEnough.add(option);
            }

            if(bigEnough.size()>0){
                //Returning the preview resolution most closely matching the texture view resolution
                return Collections.min(bigEnough, new CompareSizeByArea());

            }else
                selectedPreviewResolution = choices[0];
            }

        return selectedPreviewResolution;
        }

}