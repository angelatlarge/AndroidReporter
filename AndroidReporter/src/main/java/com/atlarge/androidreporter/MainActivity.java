package com.atlarge.androidreporter;

import java.util.List;
import java.util.LinkedList;
import java.util.Collections;
import java.util.Comparator;

import android.os.Bundle;
import android.app.Activity;
import android.text.method.ScrollingMovementMethod;
import android.view.Menu;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Size;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;


public class MainActivity extends Activity {
    private TextView outputText = null;
    private ScrollView scroller = null;
    private static final Comparator<Camera.Size> mSizeComparator = new Comparator<Camera.Size>() {
        public int compare(Camera.Size a, Camera.Size b) {
            return a.width - b.width;
        }
    };

    private class PhoneInfo {
        InfoAllCameras mInfoAllCameras;

        private class InfoAllCameras {
            int mNumCameras;
            List<InfoSingleCamera> mInfos = new LinkedList<InfoSingleCamera>();

            private class InfoSingleCamera {
                List<Camera.Size> mPictureSizes = null;
                List<Camera.Size> mPreviewSizes = null;
                Camera.CameraInfo mCameraInfo = new Camera.CameraInfo();

                private InfoSingleCamera(int cameraId){
                    Camera.getCameraInfo(cameraId, mCameraInfo);
                    Camera camera = Camera.open(cameraId);
                    if (camera!= null) {
                        try {
                            Camera.Parameters params = camera.getParameters();
                            mPictureSizes = params.getSupportedPictureSizes();
                            mPreviewSizes = params.getSupportedPreviewSizes();
                        } finally {
                            camera.release();
                            camera=null;
                        }

                        Collections.sort(mPictureSizes, mSizeComparator);
                        Collections.sort(mPreviewSizes, mSizeComparator);
                    }
                }

                public String toString() {
                    StringBuilder sb = new StringBuilder();

                    String facingString;
                    switch (mCameraInfo.facing) {
                        case CameraInfo.CAMERA_FACING_BACK: facingString="CAMERA_FACING_BACK"; break;
                        case CameraInfo.CAMERA_FACING_FRONT: facingString="CAMERA_FACING_FRONT"; break;
                        default: facingString=String.format("Unknown (%d)", mCameraInfo.facing);
                    }
                    sb.append(String.format("Facing: %s\n", facingString));

                    String sep;
                    sep = "";
                    sb.append(String.format("Preview sizes: "));
                    for (Camera.Size sz : mPreviewSizes) {
                        sb.append(sep); sep=",";
                        sb.append(String.format("%dx%d", sz.width, sz.height));
                    }
                    sb.append("\n");

                    sep = "";
                    sb.append(String.format("Picture sizes: "));
                    for (Camera.Size sz : mPreviewSizes) {
                        sb.append(sep); sep=",";
                        sb.append(String.format("%dx%d", sz.width, sz.height));
                    }

                    return sb.toString();
                }

            }

            public InfoAllCameras() {
                mNumCameras = Camera.getNumberOfCameras();
                for (int i=0; i<mNumCameras; i++) {
                    mInfos.add(new InfoSingleCamera(i));
                }
            }

            public String toString() {
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("%d cameras:\n", mInfos.size()));
                int i=0;
                for (InfoSingleCamera ci : mInfos) {
                    sb.append(String.format("\nCamera %d:\n", i++));
                    sb.append(ci.toString());
                }
                return sb.toString();
            }

        }


        public PhoneInfo() {
            mInfoAllCameras = new InfoAllCameras();
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("OS: %s %s %s\n", android.os.Build.VERSION.CODENAME, android.os.Build.VERSION.RELEASE, android.os.Build.VERSION.INCREMENTAL));
            sb.append(String.format("OS API Level: %s\n", android.os.Build.VERSION.SDK));
            sb.append(String.format("Device: %s\n", android.os.Build.DEVICE));
            sb.append(String.format("Model (product): %s (%s)\n", android.os.Build.MODEL, android.os.Build.PRODUCT));
            sb.append(mInfoAllCameras.toString());
            return sb.toString();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        outputText = (TextView)findViewById(R.id.OutputText);
        outputText.setText("Press 'Run' to start...\n");
        outputText.setMovementMethod(new ScrollingMovementMethod());

        scroller = (ScrollView)findViewById(R.id.Scroller);

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    public void onRunButtonClick(View view) {
        outputText.append("Started...\n");
        {
            PhoneInfo pi = new PhoneInfo();
            outputText.append(pi.toString());
        }
        outputText.append("Finished!\n");

        // Ensure scroll to end of text
        scroller.post(new Runnable() {
            public void run() {
                scroller.fullScroll(ScrollView.FOCUS_DOWN);
            }
        });
    }

}
