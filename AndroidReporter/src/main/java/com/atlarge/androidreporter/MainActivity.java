package com.atlarge.androidreporter;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.LinkedList;
import java.util.Collections;
import java.util.Comparator;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.os.Message;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.util.Xml;
import android.view.Menu;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.xmlpull.v1.XmlSerializer;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;


public class MainActivity extends Activity {
    private TextView outputText = null;
    private ScrollView scroller = null;
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

        public String convertXMLFileToString(String xml)
        {
            try{
                DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
                org.w3c.dom.Document doc = documentBuilderFactory.newDocumentBuilder().parse(xml);
                StringWriter stw = new StringWriter();
                Transformer serializer = TransformerFactory.newInstance().newTransformer();
                serializer.transform(new DOMSource(doc), new StreamResult(stw));
                return stw.toString();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        // Given a URL, establishes an HttpUrlConnection and retrieves
        // the web page content as a InputStream, which it returns as
        // a string.
        private String sendPhoneInfo(PhoneInfo pi) throws IOException {
            InputStream is = null;
            // Only display the first 500 characters of the retrieved
            // web page content.
            int len = 500;

            try {
                HttpClient client = new DefaultHttpClient();
                HttpPost request = new HttpPost(getResources().getString(R.string.post_url));
                List<NameValuePair> nameValuePairs = new LinkedList<NameValuePair>(5);
                nameValuePairs.add(new BasicNameValuePair("xmldata", convertXMLFileToString(pi.toXml())));
                request.setEntity(new UrlEncodedFormEntity(nameValuePairs));
                //request.setHeader("User-Agent", sUserAgent);
                //try {
                    HttpResponse response = client.execute(request);
                //} catch (IOException e) {
//                    throw new Exception("Problem communicating with API", e);
  //              }


            } finally {
                if (is != null) {
                    is.close();
                }
            }
        }

        @Override
        protected String doInBackground(PhoneInfo... pis) {

            // params comes from the execute() call: params[0] is the url.
            try {
                return sendPhoneInfo(pis[0]);
            } catch (IOException e) {
                return "Unable to retrieve web page. URL may be invalid.";
            }
        }

        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(String result) {
            textView.setText(result);
        }
    }

    private class PhoneInfo {
        String mOsCodename = android.os.Build.VERSION.CODENAME;
        String mOsRelease = android.os.Build.VERSION.RELEASE;
        String mOsIncrement = android.os.Build.VERSION.INCREMENTAL;
        String mApiLevel = android.os.Build.VERSION.SDK;
        String mDevice = android.os.Build.DEVICE;
        String mModel = android.os.Build.MODEL;
        String mProduct = android.os.Build.PRODUCT;

        InfoAllCameras mInfoAllCameras = null;

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

                public String cameraFacingString() {
                    String facingString;
                    switch (mCameraInfo.facing) {
                        case CameraInfo.CAMERA_FACING_BACK: facingString="CAMERA_FACING_BACK"; break;
                        case CameraInfo.CAMERA_FACING_FRONT: facingString="CAMERA_FACING_FRONT"; break;
                        default: facingString=String.format("Unknown (%d)", mCameraInfo.facing);
                    }
                    return facingString;
                }

                public String toString() {
                    StringBuilder sb = new StringBuilder();

                    sb.append(String.format("Facing: %s\n", cameraFacingString()));

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
                    for (Camera.Size sz : mPictureSizes) {
                        sb.append(sep); sep=",";
                        sb.append(String.format("%dx%d", sz.width, sz.height));
                    }

                    return sb.toString();
                }

                public void addToXml(XmlSerializer serializer) throws IOException {
                    serializer.startTag("", "camera_info");
                    try{
                        serializer.attribute("", "facing", cameraFacingString());

                        for (Camera.Size sz : mPreviewSizes) {
                            serializer.startTag("", "preview_size");
                            try {
                                serializer.attribute("", "w", String.valueOf(sz.width));
                                serializer.attribute("", "h", String.valueOf(sz.height));
                            } finally {
                                serializer.endTag("", "preview_size");
                            }
                        }

                        for (Camera.Size sz : mPictureSizes) {
                            serializer.startTag("", "picture_size");
                            try {
                                serializer.attribute("", "w", String.valueOf(sz.width));
                                serializer.attribute("", "h", String.valueOf(sz.height));
                            } finally {
                                serializer.endTag("", "preview_size");
                            }
                        }

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
        outputText.append("\nFinished!\n");

        // Ensure scroll to end of text
        scroller.post(new Runnable() {
            public void run() {
                scroller.fullScroll(ScrollView.FOCUS_DOWN);
            }
        });
    }

    public void onSendButtonClick(View view) {
        ConnectivityManager connMgr = (ConnectivityManager)
                getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected()) {
            // fetch data
        } else {
            // display error
        }
    }

}
