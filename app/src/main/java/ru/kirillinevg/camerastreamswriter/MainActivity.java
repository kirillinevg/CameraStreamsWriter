package ru.kirillinevg.camerastreamswriter;


import android.annotation.SuppressLint;
import android.graphics.ImageFormat;
import android.media.Image;
import android.media.ImageReader;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.app.AlertDialog;
import android.content.Intent;

import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaCodec;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.hardware.camera2.*;
import android.os.Environment;
import android.util.Log;
import android.util.Size;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import com.guichaguri.minimalftp.FTPServer;
import com.guichaguri.minimalftp.impl.NativeFileSystem;
import com.guichaguri.minimalftp.impl.NoOpAuthenticator;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;


public class MainActivity extends AppCompatActivity {

    static class CameraItem {
        CameraDevice            camera;
        String                  cameraId;
        MediaRecorder           mediaRecorder;
        CameraCaptureSession    captureSession;
        boolean                 isOpeningCamera;
        boolean                 isWritingVideo;
        File                    filePathWriting;
        File                    filePathFinished;
    }

    CameraItem [] mCameras;


    TextView tvStatus;


    File mDirDCIM;


    FTPServer mFtpServer;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        tvStatus = findViewById(R.id.tvStatus);

        mDirDCIM = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                "CameraStreamsWriter");
        mDirDCIM.mkdirs();

        startFtpServer(mDirDCIM);

        connectToWifi("tvhelp", "1q2w3e4r");

        mCameras = new CameraItem[2];
        mCameras[0] = new CameraItem();
        mCameras[0].filePathWriting = new File(mDirDCIM, "first.writing");
        mCameras[0].filePathFinished = new File(mDirDCIM, "first.mp4");
        mCameras[1] = new CameraItem();
        mCameras[1].filePathWriting = new File(mDirDCIM, "second.writing");
        mCameras[1].filePathFinished = new File(mDirDCIM, "second.mp4");

        CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            String[] cameras = manager.getCameraIdList();
            Log.e("xxx", "cameras: " + Arrays.toString(cameras));
            int n = Math.min(cameras.length, mCameras.length);
            for (int i = 0; i < n; i++) {
                mCameras[i].cameraId = cameras[i];
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }



    CameraItem findCameraItem(String cameraId) {
        for (int i = 0; i < mCameras.length; i++) {
            if (Objects.equals(mCameras[i].cameraId, cameraId)) {
                return mCameras[i];
            }
        }

        return null;
    }






    @SuppressLint("MissingPermission")
    void startWriting(final CameraItem item) {
        if (item.cameraId == null || item.isOpeningCamera || item.isWritingVideo)
            return;

        stopWriting(item);

        String cameraId = item.cameraId;

        CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);

        try {
//            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
//            StreamConfigurationMap configs = characteristics.get(
//                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
//            Size[] sizes = configs.getOutputSizes(MediaRecorder.class);
//            ((TextView)findViewById(R.id.tvResolutions)).setText(Arrays.toString(sizes));
//            Log.e("xxx", "Resolutions: " + Arrays.toString(sizes));
////            final Size sizeHigh = sizes[0];
//            final Size sizeHigh = (sizes[0].getWidth() < 1920) ? (sizes[0]) : (new Size(1920, 1080));

            final Size sizeHigh = new Size(1280, 720);

            item.isOpeningCamera = true;

            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    item.camera = camera;



                    item.mediaRecorder = new MediaRecorder();
                    item.mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
                    item.mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                    item.mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
                    item.mediaRecorder.setVideoSize(sizeHigh.getWidth(), sizeHigh.getHeight());
                    item.mediaRecorder.setVideoFrameRate(25);
                    item.mediaRecorder.setVideoEncodingBitRate(4*1024*1024);
                    item.mediaRecorder.setMaxDuration(0);
                    item.mediaRecorder.setMaxFileSize(0);
                    item.mediaRecorder.setOrientationHint(0);
                    item.mediaRecorder.setOutputFile(item.filePathWriting.getAbsolutePath());



                    try {



                        item.mediaRecorder.prepare();

                        List<Surface> surfaces = new ArrayList<>();
                        surfaces.add(item.mediaRecorder.getSurface());



                        CaptureRequest.Builder captureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                        captureBuilder.addTarget(item.mediaRecorder.getSurface());
                        setUpCaptureRequestBuilder(captureBuilder);
                        final CaptureRequest captureRequest = captureBuilder.build();



                        camera.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                            @Override
                            public void onConfigured(CameraCaptureSession session) {
                                item.captureSession = session;
                                try {
                                    item.captureSession.setRepeatingRequest(captureRequest, null, null);




                                    item.mediaRecorder.start();
                                    item.isWritingVideo = true;

                                    tvStatus.setText("The recording is successfully started.\nClick BACK to finish recording.");
                                    Log.e("xxx", "The recording is successfully started.\nClick BACK to finish recording.");


                                } catch (Exception e) {
                                    e.printStackTrace();
                                    tvStatus.setText("(1) Error: " + e.toString());
                                    Log.e("xxx", "(1) Error: " + e.toString());
                                }

                                item.isOpeningCamera = false;
                            }

                            @Override
                            public void onConfigureFailed(CameraCaptureSession session) {
                                tvStatus.setText("(4) Error: onConfigureFailed");
                                Log.e("xxx", "(4) Error: onConfigureFailed");
                                item.isOpeningCamera = false;
                            }
                        }, null);
                    } catch (Exception e) {
                        e.printStackTrace();
                        tvStatus.setText("(2) Error: " + e.toString());
                        Log.e("xxx", "(2) Error: " + e.toString());
                        item.isOpeningCamera = false;
                    }
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    item.isOpeningCamera = false;
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    Log.e("xxx", "(err) Error: " + error);
                    item.isOpeningCamera = false;
                }
            }, null);

        } catch (Exception e) {
            e.printStackTrace();
            tvStatus.setText("(3) Error: " + e.toString());
            Log.e("xxx", "(3) Error: " + e.toString());
            Log.e("xxx", Log.getStackTraceString(e));
            item.isOpeningCamera = false;
        }

    }



    private void setUpCaptureRequestBuilder(CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
    }





    void stopWriting(CameraItem item) {
        if (item.isWritingVideo) {
            item.mediaRecorder.stop();
            item.mediaRecorder.reset();
        }

        if (item.captureSession != null) {
            item.captureSession.close();
            item.captureSession = null;
        }

        if (item.camera != null) {
            item.camera.close();
            item.camera = null;
        }

        if (item.mediaRecorder != null) {
            item.mediaRecorder.release();
            item.mediaRecorder = null;
        }

        if (item.isWritingVideo) {
            item.isWritingVideo = false;
            item.filePathFinished.delete();
            item.filePathWriting.renameTo(item.filePathFinished);
        }
    }





    @Override
    protected void onDestroy() {
        for (CameraItem item : mCameras)
            stopWriting(item);

        stopFtpServer();

        super.onDestroy();
    }






    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int action = event.getAction();
        int keyCode = event.getKeyCode();
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                if (action == KeyEvent.ACTION_DOWN) {
                    Log.e("xxx", "KEYCODE_VOLUME_UP");
                    for (int i = mCameras.length - 1; i >= 0; i--)
                        startWriting(mCameras[i]);
                }
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (action == KeyEvent.ACTION_DOWN) {
                    Log.e("xxx", "KEYCODE_VOLUME_DOWN");
                    for (CameraItem item : mCameras)
                        stopWriting(item);
                }
                return true;
        }
        return super.dispatchKeyEvent(event);
    }









    private boolean startFtpServer(File root) {
        if (mFtpServer != null)
            return true;

        // Uses the current working directory as the root
        //File root = new File(System.getProperty("user.dir"));

        // Creates a native file system
        NativeFileSystem fs = new NativeFileSystem(root);

        // Creates a noop authenticator
        NoOpAuthenticator auth = new NoOpAuthenticator(fs);

        // Creates the server with the authenticator
        mFtpServer = new FTPServer(auth);

        // Start listening asynchronously
        try {
            mFtpServer.listen(2121);
        } catch (IOException e) {
            Log.e("xxxFTP", Log.getStackTraceString(e));
            stopFtpServer();
        }

        return (mFtpServer != null);
    }

    private void stopFtpServer() {
        if (mFtpServer != null) {
            try {
                mFtpServer.close();
            } catch (IOException e) {
            }
            mFtpServer = null;
        }
    }








    private void connectToWifi(String networkSSID, String networkPassword) {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);

        if (!wifiManager.isWifiEnabled()) {
            wifiManager.setWifiEnabled(true);
        }

        WifiConfiguration conf = new WifiConfiguration();
        conf.SSID = String.format("\"%s\"", networkSSID);
        conf.preSharedKey = String.format("\"%s\"", networkPassword);

        int netId = wifiManager.addNetwork(conf);
        wifiManager.disconnect();
        wifiManager.enableNetwork(netId, true);
        wifiManager.reconnect();
    }












}
