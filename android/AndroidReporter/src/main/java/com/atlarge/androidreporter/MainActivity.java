package com.atlarge.androidreporter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedList;
import java.util.Collections;
import java.util.Comparator;

import android.graphics.ImageFormat;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.text.method.ScrollingMovementMethod;
import android.util.Xml;
import android.view.Menu;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.xml.sax.SAXException;
import org.xmlpull.v1.XmlSerializer;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;


public class MainActivity extends Activity {
    private TextView mOutputText = null;
    private ScrollView mScroller = null;
    private ProgressBar mProgressSend= null;
    private PhoneInfo mPi = null;
    private static final Comparator<Camera.Size> mSizeComparator = new Comparator<Camera.Size>() {
        public int compare(Camera.Size a, Camera.Size b) {
            return a.width - b.width;
        }
    };


    // Uses AsyncTask to create a task away from the main UI thread. This task takes a
    // URL string and uses it to create an HttpUrlConnection. Once the connection
    // has been established, the AsyncTask downloads the contents of the webpage as
    // an InputStream. Finally, the InputStream is converted into a string, which is
    // displayed in the UI by the AsyncTask's onPostExecute method.
    private class DownloadWebpageTask extends AsyncTask<PhoneInfo, Void, String> {

        // Reads an InputStream and converts it to a String.
        public String readIt(InputStream stream, int len) throws IOException, UnsupportedEncodingException {
            Reader reader = null;
            reader = new InputStreamReader(stream, "UTF-8");
            char[] buffer = new char[len];
            reader.read(buffer);
            return new String(buffer);
        }

        public String convertXMLFileToString(String xml) throws ParserConfigurationException, IOException, SAXException, TransformerException {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            org.w3c.dom.Document doc = documentBuilderFactory.newDocumentBuilder().parse(xml);
            StringWriter stw = new StringWriter();
            Transformer serializer = TransformerFactory.newInstance().newTransformer();
            serializer.transform(new DOMSource(doc), new StreamResult(stw));
            return stw.toString();
        }

        // Given a URL, establishes an HttpUrlConnection and retrieves
        // the web page content as a InputStream, which it returns as
        // a string.
        private String sendPhoneInfo(PhoneInfo pi)  {

            try {
                HttpClient client = new DefaultHttpClient();
                HttpPost request = new HttpPost(getResources().getString(R.string.post_url));
                List<NameValuePair> nameValuePairs = new LinkedList<NameValuePair>();
                //nameValuePairs.add(new BasicNameValuePair("xmldata", convertXMLFileToString(pi.toXml())));
                nameValuePairs.add(new BasicNameValuePair("xmldata", pi.toXml()));
                request.setEntity(new UrlEncodedFormEntity(nameValuePairs));

                try {
                    HttpResponse httpResponse = client.execute(request);

                    InputStream inputStream = httpResponse.getEntity().getContent();
                    InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                    BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                    StringBuilder stringBuilder = new StringBuilder();
                    String bufferedStrChunk = null;
                    while((bufferedStrChunk = bufferedReader.readLine()) != null){
                        stringBuilder.append(bufferedStrChunk);
                    }

                    if (httpResponse.getStatusLine().getStatusCode() == 200) {
                        return "Success\n" + stringBuilder.toString();
                    } else {
                        return "Failure\n: " + stringBuilder.toString();
                    }
                } catch (ClientProtocolException cpe) {
                    return "Failure\n: " + cpe;
                } catch (IOException ioe) {
                    return "Failure\n: " + ioe;
                }
            } catch (Exception e) {
                return "Failure\n: " + e;
            }
        }

        @Override
        protected String doInBackground(PhoneInfo... pis) {
            // params comes from the execute() call: params[0] is the url.
            return sendPhoneInfo(pis[0]);
        }

        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(String result) {
            mOutputText.append("done. ");
            mOutputText.append(result);
            strollToTheEnd();
            mProgressSend.setVisibility(View.INVISIBLE);
        }
    }

    private static class PhoneInfo {
        String mOsCodename = android.os.Build.VERSION.CODENAME;
        String mOsRelease = android.os.Build.VERSION.RELEASE;
        String mOsIncrement = android.os.Build.VERSION.INCREMENTAL;
        String mApiLevel = android.os.Build.VERSION.SDK;
        String mDevice = android.os.Build.DEVICE;
        String mModel = android.os.Build.MODEL;
        String mProduct = android.os.Build.PRODUCT;

        InfoAllCameras mInfoAllCameras = null;

        private static class InfoAllCameras {
            int mNumCameras;
            List<InfoSingleCamera> mInfos = new LinkedList<InfoSingleCamera>();

            private static class InfoSingleCamera {
                List<Camera.Size> mPictureSizes = null;
                List<Camera.Size> mPreviewSizes = null;
                List<Integer> mSupportedPreviewFormats = null;
                List<Integer> mSupportedPictureFormats = null;
                Camera.CameraInfo mCameraInfo = new Camera.CameraInfo();
                static HashMap<Integer, String> mPictureFormatNames = null;

                static {
                    mPictureFormatNames = new HashMap<Integer, String>();
                    mPictureFormatNames.put(ImageFormat.JPEG,       "JPEG");
                    mPictureFormatNames.put(ImageFormat.NV16,       "NV16");
                    mPictureFormatNames.put(ImageFormat.NV21,       "NV21");
                    mPictureFormatNames.put(ImageFormat.RGB_565,    "RGB_565");
                    mPictureFormatNames.put(ImageFormat.UNKNOWN,    "UNKNOWN");
                    mPictureFormatNames.put(ImageFormat.YUY2,       "YUY2");
                    mPictureFormatNames.put(ImageFormat.YV12,       "YV12");
                }
                private InfoSingleCamera(int cameraId){
                    Camera.getCameraInfo(cameraId, mCameraInfo);
                    Camera camera = Camera.open(cameraId);
                    if (camera!= null) {
                        try {
                            Camera.Parameters params = camera.getParameters();
                            mPictureSizes = params.getSupportedPictureSizes();
                            mPreviewSizes = params.getSupportedPreviewSizes();
                            mSupportedPreviewFormats = params.getSupportedPreviewFormats();
                            mSupportedPictureFormats = params.getSupportedPictureFormats();
                        } finally {
                            camera.release();
                            camera=null;
                        }

                        Collections.sort(mPictureSizes, mSizeComparator);
                        Collections.sort(mPreviewSizes, mSizeComparator);
                    }
                }

                public String cameraFacingString() {
                    String facingString;
                    switch (mCameraInfo.facing) {
                        case CameraInfo.CAMERA_FACING_BACK: facingString="CAMERA_FACING_BACK"; break;
                        case CameraInfo.CAMERA_FACING_FRONT: facingString="CAMERA_FACING_FRONT"; break;
                        default: facingString=String.format("Unknown (%d)", mCameraInfo.facing);
                    }
                    return facingString;
                }

                private static void sbAppendSizeList(StringBuilder sb, List<Camera.Size> list) {
                    String sep = "";
                    for (Camera.Size sz : list) {
                        sb.append(sep); sep=",";
                        sb.append(String.format("%dx%d", sz.width, sz.height));
                    }
                }

                private static void sbAppendIntList(StringBuilder sb, List<Integer> list) {
                    String sep = "";
                    for (Integer i : list) {
                        sb.append(sep); sep=",";
                        sb.append(String.format("%d", i));
                    }
                }

                private static void sbAppendPictureFormatList(StringBuilder sb, List<Integer> list) {
                    String sep = "";
                    for (Integer i : list) {
                        sb.append(sep); sep=",";
                        String formatName;
                        if ( (formatName = mPictureFormatNames.get(i)) != null ) {
                            sb.append(String.format("%s(%d)", formatName, i));
                        } else {
                            sb.append(String.format("%d", formatName, i));
                        }
                    }
                }

                public String toString() {
                    StringBuilder sb = new StringBuilder();

                    sb.append(String.format("Facing: %s\n", cameraFacingString()));

                    sb.append(String.format("Preview sizes: "));
                    sbAppendSizeList(sb, mPreviewSizes);
                    sb.append("\n");

                    sb.append(String.format("Picture sizes: "));
                    sbAppendSizeList(sb, mPictureSizes);
                    sb.append("\n");

                    sb.append(String.format("Preview formats: "));
                    sbAppendPictureFormatList(sb, mSupportedPreviewFormats);
                    sb.append("\n");

                    sb.append(String.format("Picture formats: "));
                    sbAppendPictureFormatList(sb, mSupportedPictureFormats);
                    sb.append("\n");

                    return sb.toString();
                }

                private static void xmlAppendSizeList(XmlSerializer serializer, String name, List<Camera.Size> list) throws IOException {
                    for (Camera.Size sz : list) {
                        serializer.startTag("", name);
                        try {
                            serializer.attribute("", "w", String.valueOf(sz.width));
                            serializer.attribute("", "h", String.valueOf(sz.height));
                        } finally {
                            serializer.endTag("", name);
                        }
                    }
                }

                private static void xmlAppendPictureFormatList(XmlSerializer serializer, String name, List<Integer> list) throws IOException  {
                    for (Integer i : list) {
                        serializer.startTag("", name);
                        try {
                            String formatName;
                            if ( (formatName = mPictureFormatNames.get(i)) != null ) {
                                serializer.attribute("", "name", formatName);
                            }
                            serializer.attribute("", "value", String.valueOf(i));
                        } finally {
                            serializer.endTag("", name);
                        }
                    }
                }

                public void addToXml(XmlSerializer serializer) throws IOException {
                    serializer.startTag("", "camera_info");
                    try{
                        serializer.attribute("", "facing_id", String.valueOf(mCameraInfo.facing));
                        serializer.attribute("", "facing_string", cameraFacingString());
                        xmlAppendSizeList(serializer, "preview_size", mPreviewSizes);
                        xmlAppendSizeList(serializer, "picture_size", mPictureSizes);
                        xmlAppendPictureFormatList(serializer, "preview_format", mSupportedPreviewFormats);
                        xmlAppendPictureFormatList(serializer, "picture_format", mSupportedPictureFormats);
                    } finally {
                        serializer.endTag("", "camera_info");
                    }

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
                    sb.append(String.format("\nCamera %d\n", i++));
                    sb.append(ci.toString());
                    sb.append("\n");
                }
                return sb.toString();
            }

            public void addToXml(XmlSerializer serializer) throws IOException {
                for (InfoSingleCamera isc : mInfos) {
                    isc.addToXml(serializer);
                }
            }
        }

        public PhoneInfo() {
            mInfoAllCameras = new InfoAllCameras();
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("OS: %s %s %s\n", mOsCodename, mOsRelease, mOsIncrement));
            sb.append(String.format("OS API Level: %s\n", mApiLevel));
            sb.append(String.format("Device: %s\n", mDevice));
            sb.append(String.format("Model (product): %s (%s)\n",mModel, mProduct));
            sb.append(mInfoAllCameras.toString());
            return sb.toString();
        }

        public void addToXml(XmlSerializer serializer) throws IOException {
            serializer.startTag("", "phone_info");
            try {
                serializer.attribute("", "os_codename", mOsCodename);
                serializer.attribute("", "os_release", mOsRelease);
                serializer.attribute("", "os_increment", mOsIncrement);
                serializer.attribute("", "device", mDevice);
                serializer.attribute("", "model", mModel);
                serializer.attribute("", "product", mProduct);
                mInfoAllCameras.addToXml(serializer);
            } finally {
                serializer.endTag("", "phone_info");
            }
        }

        public String toXml() {
            XmlSerializer serializer = Xml.newSerializer();
            StringWriter writer = new StringWriter();
            try {
                serializer.setOutput(writer);
                serializer.startDocument("UTF-8", true);
                addToXml(serializer);
                serializer.endDocument();
                return writer.toString();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mOutputText = (TextView)findViewById(R.id.OutputText);
        mOutputText.setText("Press 'Run' to start...\n");
        mOutputText.setMovementMethod(new ScrollingMovementMethod());

        mScroller = (ScrollView)findViewById(R.id.Scroller);

        mProgressSend = (ProgressBar)findViewById(R.id.progressBarSend);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    private void strollToTheEnd() {
        mScroller.post(new Runnable() {
            public void run() {
                mScroller.fullScroll(ScrollView.FOCUS_DOWN);
            }
        });
    }

    public void ensureHavePiInfo() {
        if (mPi==null) {
            mOutputText.append("Started...\n");
            {
                try {
                    mPi = new PhoneInfo();
                    mOutputText.append(mPi.toString());
                } catch (Exception e) {
                    mOutputText.append(String.format("\nError: %s %s", e.getMessage(), e.getCause()));
                }
            }
            mOutputText.append("\nFinished\n");
            strollToTheEnd();
        }
    }

    public void onRunButtonClick(View view) {
        ensureHavePiInfo();
    }

    public void onSendButtonClick(View view) {
        ensureHavePiInfo();

        ConnectivityManager connMgr = (ConnectivityManager)
        getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected()) {
            mProgressSend.setVisibility(View.VISIBLE);
            mOutputText.append("Sending...");
            new DownloadWebpageTask().execute(mPi);
        } else {
            mProgressSend.setVisibility(View.INVISIBLE);
            // display error
        }
    }

}
