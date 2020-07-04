package ru.kirillinevg.camerastreamswriter;


import android.annotation.SuppressLint;
import android.graphics.ImageFormat;
import android.media.Image;
import android.media.ImageReader;
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
import android.util.Size;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class MainActivity extends AppCompatActivity {

    CameraDevice mCamera;
    MediaRecorder mMediaRecorderLow;
    MediaRecorder mMediaRecorderHigh;
    ImageReader mImageReader;
    CaptureRequest mCaptureRequest;
    CaptureRequest mImageCaptureRequest;
    CameraCaptureSession mCaptureSession;
    boolean mIsRecordingVideo = false;
    File mDirDCIM;

    TextView tvStatus;

    Handler mImageHandler;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);

        mImageHandler = new Handler();
    }

    @SuppressLint("MissingPermission")
    @Override
    protected void onResume() {
        super.onResume();

        if (mDirDCIM != null)
            return;






        mDirDCIM = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                "CameraStreamsWriter");

        final File outputFileLow = new File(mDirDCIM.getAbsolutePath(), "a_720p_.mp4");
        final File outputFileHigh = new File(mDirDCIM.getAbsolutePath(), "a_1080p_.mp4");


        CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);

        try {

            mDirDCIM.mkdirs();

            String[] cameras = manager.getCameraIdList();
            String cameraId = cameras[0];

            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap configs = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] sizes = configs.getOutputSizes(MediaRecorder.class);
            ((TextView)findViewById(R.id.tvResolutions)).setText(Arrays.toString(sizes));
//            final Size sizeLow = sizes[5];
//            final Size sizeHigh = sizes[0];

            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    mCamera = camera;

                    mMediaRecorderLow = new MediaRecorder();
                    mMediaRecorderLow.setVideoSource(MediaRecorder.VideoSource.SURFACE);
                    mMediaRecorderLow.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                    mMediaRecorderLow.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
                    mMediaRecorderLow.setVideoSize(1280, 720);
                    mMediaRecorderLow.setVideoFrameRate(30);
                    mMediaRecorderLow.setVideoEncodingBitRate(4*1024*1024);
                    mMediaRecorderLow.setMaxDuration(0);
                    mMediaRecorderLow.setMaxFileSize(0);
                    mMediaRecorderLow.setOrientationHint(0);
                    mMediaRecorderLow.setOutputFile(outputFileLow.getAbsolutePath());

                    mMediaRecorderHigh = new MediaRecorder();
                    mMediaRecorderHigh.setVideoSource(MediaRecorder.VideoSource.SURFACE);
                    mMediaRecorderHigh.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                    mMediaRecorderHigh.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
                    mMediaRecorderHigh.setVideoSize(1920, 1080);
                    mMediaRecorderHigh.setVideoFrameRate(30);
                    mMediaRecorderHigh.setVideoEncodingBitRate(8*1024*1024);
                    mMediaRecorderHigh.setMaxDuration(0);
                    mMediaRecorderHigh.setMaxFileSize(0);
                    mMediaRecorderHigh.setOrientationHint(0);
                    mMediaRecorderHigh.setOutputFile(outputFileHigh.getAbsolutePath());


                    mImageReader = ImageReader.newInstance(1920, 1080, ImageFormat.JPEG, 1);
                    mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, null);



                    try {


                        mMediaRecorderLow.prepare();
                        mMediaRecorderHigh.prepare();

                        List<Surface> surfaces = new ArrayList<>();
                        surfaces.add(mMediaRecorderLow.getSurface());
                        surfaces.add(mMediaRecorderHigh.getSurface());
                        surfaces.add(mImageReader.getSurface());

                        CaptureRequest.Builder captureRequestBuilder =
                                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                        captureRequestBuilder.addTarget(mMediaRecorderLow.getSurface());
                        captureRequestBuilder.addTarget(mMediaRecorderHigh.getSurface());
                        mCaptureRequest = captureRequestBuilder.build();

                        CaptureRequest.Builder captureBuilder =
                                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                        captureBuilder.addTarget(mImageReader.getSurface());
                        captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, 0);
                        mImageCaptureRequest = captureBuilder.build();

                        mCamera.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                            @Override
                            public void onConfigured(CameraCaptureSession session) {
                                mCaptureSession = session;
                                try {
                                    mCaptureSession.setRepeatingRequest(mCaptureRequest, null, null);

                                    mImageHandler.postDelayed(mImageRunnable, 1);

                                    mMediaRecorderLow.start();
                                    mMediaRecorderHigh.start();
                                    mIsRecordingVideo = true;

                                    tvStatus.setText("The recording is successfully started.\nClick BACK to finish recording.");
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    tvStatus.setText("(1) Error: " + e.toString());
                                }
                            }

                            @Override
                            public void onConfigureFailed(CameraCaptureSession session) {
                                ;
                            }
                        }, null);
                    } catch (Exception e) {
                        e.printStackTrace();
                        tvStatus.setText("(2) Error: " + e.toString());
                    }
                }

                @Override
                public void onDisconnected(CameraDevice camera) {

                }

                @Override
                public void onError(CameraDevice camera, int error) {

                }
            }, null);

        } catch (Exception e) {
            e.printStackTrace();
            tvStatus.setText("(3) Error: " + e.toString());
        }
    }


    @Override
    protected void onDestroy() {
        mImageHandler.removeCallbacks(mImageRunnable);

        if (mIsRecordingVideo) {
            mIsRecordingVideo = false;
            mMediaRecorderLow.stop();
            mMediaRecorderLow.reset();
            mMediaRecorderHigh.stop();
            mMediaRecorderHigh.reset();
        }

        if (mCaptureSession != null) {
            mCaptureSession.close();
            mCaptureSession = null;
        }

        if (mCamera != null) {
            mCamera.close();
            mCamera = null;
        }

        if (mMediaRecorderLow != null) {
            mMediaRecorderLow.release();
            mMediaRecorderLow = null;
        }

        if (mMediaRecorderHigh != null) {
            mMediaRecorderHigh.release();
            mMediaRecorderHigh = null;
        }

        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }

        super.onDestroy();
    }




    private final Runnable mImageRunnable = new Runnable() {

        @Override
        public void run() {
            try {
                mCaptureSession.capture(mImageCaptureRequest, null, null);
            } catch (Exception e) {
                e.printStackTrace();
                tvStatus.setText("(4) Error: " + e.toString());
            }
        }

    };





    /**
     * This a callback object for the {@link ImageReader}. "onImageAvailable" will be called when a
     * still image is ready to be saved.
     */
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        private int mImageIndex = 0;

        @Override
        public void onImageAvailable(ImageReader reader) {
            ++mImageIndex;

            //if (mImageIndex < 60)
                mImageHandler.postDelayed(mImageRunnable, 700);

            File file = new File(mDirDCIM.getAbsolutePath(),
                    String.format("b_1080p_%04d.jpg", mImageIndex));
            ImageSaver saver = new ImageSaver(reader.acquireNextImage(), file);
            saver.run();
        }

    };





    /**
     * Saves a JPEG {@link Image} into the specified {@link File}.
     */
    private static class ImageSaver implements Runnable {

        /**
         * The JPEG image
         */
        private final Image mImage;
        /**
         * The file we save the image into.
         */
        private final File mFile;

        ImageSaver(Image image, File file) {
            mImage = image;
            mFile = file;
        }

        @Override
        public void run() {
            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            FileOutputStream output = null;
            try {
                output = new FileOutputStream(mFile);
                output.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                mImage.close();
                if (null != output) {
                    try {
                        output.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

    }


}
