package kr.co.bootpay.unity;

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Pair;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.webkit.WebView;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.widget.FrameLayout;
import android.webkit.ValueCallback;
import android.widget.Toast;

import androidx.core.content.FileProvider;
// import android.support.v4.app.ActivityCompat;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.regex.Pattern;

import com.unity3d.player.UnityPlayer;

import kr.co.bootpay.core.events.BootpayEventListener;
import kr.co.bootpay.core.models.Payload;


public class CWebViewPlugin extends Fragment {

    private static final int REQUEST_CODE = 100001;

    private static FrameLayout layout = null;
//    private WebView mWebView;
    private BootpayUnityWebView mWebView;
    private View mVideoView;
    private OnGlobalLayoutListener mGlobalLayoutListener;
//    private BootpayUnityWebView.BootpayJavascriptBridge mJavascriptBridge;
//    private CWebViewPluginInterface mWebViewPlugin;
    private int progress;
    private boolean canGoBack;
    private boolean canGoForward;
    private boolean mAlertDialogEnabled;
    private boolean mAllowVideoCapture;
    private boolean mAllowAudioCapture;
    private Hashtable<String, String> mCustomHeaders;
    private String mWebViewUA;
    private Pattern mAllowRegex;
    private Pattern mDenyRegex;
    private Pattern mHookRegex;
    private BootpayUnityWebViewChromeClient chromeClient;

    private static final int INPUT_FILE_REQUEST_CODE = 1;
    private ValueCallback<Uri> mUploadMessage;
    private ValueCallback<Uri[]> mFilePathCallback;
    private Uri mCameraPhotoUri;

    private static long instanceCount;
    private long mInstanceId;
    private boolean mPaused;
    private List<Pair<String, CWebViewPlugin>> mTransactions;

    private String mBasicAuthUserName;
    private String mBasicAuthPassword;

    public CWebViewPlugin() {
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        final Activity a = UnityPlayer.currentActivity;
        if (requestCode == REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                ProcessChooser();
            } else {
                mFilePathCallback.onReceiveValue(null);
                mFilePathCallback = null;
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != INPUT_FILE_REQUEST_CODE) {
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (mFilePathCallback == null) {
                super.onActivityResult(requestCode, resultCode, data);
                return;
            }
            Uri[] results = null;
            // Check that the response is a good one
            if (resultCode == Activity.RESULT_OK) {
                if (data == null) {
                    if (mCameraPhotoUri != null) {
                        results = new Uri[] { mCameraPhotoUri };
                    }
                } else {
                    String dataString = data.getDataString();
                    // cf. https://www.petitmonte.com/java/android_webview_camera.html
                    if (dataString == null) {
                        if (mCameraPhotoUri != null) {
                            results = new Uri[] { mCameraPhotoUri };
                        }
                    } else {
                        results = new Uri[] { Uri.parse(dataString) };
                    }
                }
            }
            mFilePathCallback.onReceiveValue(results);
            mFilePathCallback = null;
        } else {
            if (mUploadMessage == null) {
                super.onActivityResult(requestCode, resultCode, data);
                return;
            }
            Uri result = null;
            if (resultCode == Activity.RESULT_OK) {
                if (data != null) {
                    result = data.getData();
                }
            }
            mUploadMessage.onReceiveValue(result);
            mUploadMessage = null;
        }
    }

    public static boolean IsWebViewAvailable() {
        final Activity a = UnityPlayer.currentActivity;
        FutureTask<Boolean> t = new FutureTask<Boolean>(new Callable<Boolean>() {
            public Boolean call() throws Exception {
                boolean isAvailable = false;
                try {
                    WebView webView = new WebView(a);
                    if (webView != null) {
                        webView = null;
                        isAvailable = true;
                    }
                } catch (Exception e) {
                }
                return isAvailable;
            }
        });
        a.runOnUiThread(t);
        try {
            return t.get();
        } catch (Exception e) {
            return false;
        }
    }

    public boolean verifyStoragePermissions(final Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PackageManager pm = activity.getPackageManager();
            int hasPerm1 = pm.checkPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE, activity.getPackageName());
            int hasPerm2 = pm.checkPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE, activity.getPackageName());
            int hasPerm3 = pm.checkPermission(android.Manifest.permission.CAMERA, activity.getPackageName());
            if (hasPerm1 != PackageManager.PERMISSION_GRANTED
                    || hasPerm2 != PackageManager.PERMISSION_GRANTED
                    || hasPerm3 != PackageManager.PERMISSION_GRANTED) {
                activity.runOnUiThread(new Runnable() {public void run() {
                    String[] PERMISSIONS = {
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.CAMERA
                    };
                    requestPermissions(PERMISSIONS, REQUEST_CODE);
                }});
                return false;
            }
            return true;
        } else {
            return true;
        }
    }

    public boolean IsInitialized() {
        return mWebView != null;
    }

//    public void Init(final String gameObject, final boolean transparent, final boolean zoom, final int androidForceDarkMode, final String ua) {
    private void Init(final String gameObject, String payload) {
        final CWebViewPlugin self = this;
        final Activity a = UnityPlayer.currentActivity;
        instanceCount++;
        mInstanceId = instanceCount;
        a.runOnUiThread(new Runnable() {public void run() {

            if (mWebView != null) {
                return;
            }

            setRetainInstance(true);
            if (mPaused) {
                if (mTransactions == null) {
                    mTransactions = new ArrayList<Pair<String, CWebViewPlugin>>();
                }
                mTransactions.add(Pair.create("add", self));
            } else {
                a
                        .getFragmentManager()
                        .beginTransaction()
                        .add(0, self, "CWebViewPlugin" + mInstanceId)
                        .commit();
            }

            mAlertDialogEnabled = true;
            mAllowVideoCapture = false;
            mAllowAudioCapture = false;
            mCustomHeaders = new Hashtable<String, String>();

            final BootpayUnityWebView webView = new BootpayUnityWebView(a, layout, mVideoView, self);
            chromeClient = new BootpayUnityWebViewChromeClient(a, layout, mVideoView);
            webView.setWebChromeClient(chromeClient);

//            mWebViewPlugin = new CWebViewPluginInterface(self, gameObject);
            webView.setWebViewClient(new BootpayUnityWebViewClient(webView.getJavascriptBridge(), mBasicAuthUserName, mBasicAuthPassword));

            if (layout == null || layout.getParent() != a.findViewById(android.R.id.content)) {
                layout = new FrameLayout(a);
                a.addContentView(
                        layout,
                        new LayoutParams(
                                LayoutParams.MATCH_PARENT,
                                LayoutParams.MATCH_PARENT));
                layout.setFocusable(true);
                layout.setFocusableInTouchMode(true);
            }
            layout.addView(
                    webView,
                    new FrameLayout.LayoutParams(
                            LayoutParams.MATCH_PARENT,
                            LayoutParams.MATCH_PARENT,
                            Gravity.NO_GRAVITY));
            mWebView = webView;
            goBootpayRequest(webView, gameObject, payload);
        }});

        final View activityRootView = a.getWindow().getDecorView().getRootView();
        mGlobalLayoutListener = new OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                android.graphics.Rect r = new android.graphics.Rect();
                //r will be populated with the coordinates of your view that area still visible.
                activityRootView.getWindowVisibleDisplayFrame(r);
                android.view.Display display = a.getWindowManager().getDefaultDisplay();
                // cf. http://stackoverflow.com/questions/9654016/getsize-giving-me-errors/10564149#10564149
                int h = 0;
                try {
                    Point size = new Point();
                    display.getSize(size);
                    h = size.y;
                } catch (java.lang.NoSuchMethodError err) {
                    h = display.getHeight();
                }

                View rootView = activityRootView.getRootView();
                int bottomPadding = 0;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    Point realSize = new Point();
                    display.getRealSize(realSize); // this method was added at JELLY_BEAN_MR1
                    int[] location = new int[2];
                    rootView.getLocationOnScreen(location);
                    bottomPadding = realSize.y - (location[1] + rootView.getHeight());
                }
                int heightDiff = rootView.getHeight() - (r.bottom - r.top);
                if (heightDiff > 0 && (heightDiff + bottomPadding) > (h + bottomPadding) / 3) { // assume that this means that the keyboard is on
                    UnityPlayer.UnitySendMessage(gameObject, "SetKeyboardVisible", "true");
                } else {
                    UnityPlayer.UnitySendMessage(gameObject, "SetKeyboardVisible", "false");
                }
            }
        };
        activityRootView.getViewTreeObserver().addOnGlobalLayoutListener(mGlobalLayoutListener);
    }

    public void Request(String gameobject, String request) {
        Init(gameobject, request);
    }

    private void goBootpayRequest(BootpayUnityWebView webView, String gameObject, String payload) {

        Payload _payload = Payload.fromJson(payload);

        BootpayEventListener listener = new BootpayEventListener() {
            @Override
            public void onCancel(String data) {
                Log.d("bootpay", "cancel: " + data);
                callbackUnity(gameObject, "CallOnCancel", data);
            }

            @Override
            public void onError(String data) {
                Log.d("bootpay", "error: " + data);
                callbackUnity(gameObject, "CallOnError", data);
            }

            @Override
            public void onClose(String data) {
                Log.d("bootpay", "close: " + data);
                callbackUnity(gameObject, "CallOnClose", data);
            }

            @Override
            public void onReady(String data) {
                Log.d("bootpay", "ready: " + data);
                callbackUnity(gameObject, "CallOnReady", data);
            }

            @Override
            public boolean onConfirm(String data) {
                Log.d("bootpay", "confirm: " + data);
                callbackUnity(gameObject, "CallOnConfirm", data);
                return false;
            }

            @Override
            public void onDone(String data) {
                Log.d("bootpay", "done: " + data);
                callbackUnity(gameObject, "CallOnDone", data);
            }

            @Override
            public void onCall(String data) {
                Log.d("bootpay", "call: " + data);
                callbackUnity(gameObject, "OnCall", data);
            }
        };

        webView.startBootpay(_payload, listener);
    }


    private void callbackUnity(String gameObject, String method, String message) {
        final Activity a = UnityPlayer.currentActivity;
        a.runOnUiThread(new Runnable() {public void run() {
            if (mWebView != null) {
                UnityPlayer.UnitySendMessage(gameObject, method, message);
            }
        }});
    }

    private void ProcessChooser() {
        mCameraPhotoUri = null;
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getActivity().getPackageManager()) != null) {
            // Create the File where the photo should go
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                // Error occurred while creating the File
                Log.e("CWebViewPlugin", "Unable to create Image File", ex);
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                takePictureIntent.putExtra("PhotoPath", photoFile);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    mCameraPhotoUri = FileProvider.getUriForFile(getActivity(), getActivity().getPackageName() + ".unitywebview.fileprovider", photoFile);
                } else {
                    mCameraPhotoUri = Uri.parse("file:" + photoFile.getAbsolutePath());
                }
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, mCameraPhotoUri);
                //takePictureIntent.putExtra(MediaStore.EXTRA_SIZE_LIMIT, "720000");
            } else {
                takePictureIntent = null;
            }
        }

        Intent contentSelectionIntent = new Intent(Intent.ACTION_GET_CONTENT);
        contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE);
        contentSelectionIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        contentSelectionIntent.setType("image/*");

        Intent[] intentArray;
        if(takePictureIntent != null) {
            intentArray = new Intent[]{takePictureIntent};
        } else {
            intentArray = new Intent[0];
        }

        Intent chooserIntent = new Intent(Intent.ACTION_CHOOSER);
        chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent);
        // chooserIntent.putExtra(Intent.EXTRA_TITLE, "Image Chooser");
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray);

        startActivityForResult(Intent.createChooser(chooserIntent, "Select images"), INPUT_FILE_REQUEST_CODE);
    }

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getActivity().getExternalFilesDir(Environment.DIRECTORY_DCIM);
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }
        File imageFile = File.createTempFile(imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );
        return imageFile;
    }

    public void TransactionConfirm(String data) {
        if(mWebView != null) mWebView.transactionConfirm(data);
    }

    public void RemovePaymentWindow() {
        if(mWebView != null) mWebView.removePaymentWindow();
    }


    boolean doubleBackToExitPressedOnce = false;
    public void GoBackButton() {
        if(mWebView == null) return;
        final Activity a = UnityPlayer.currentActivity;
        final CWebViewPlugin self = this;
        a.runOnUiThread(new Runnable() {public void run() {
            if (mWebView == null) {
                return;
            }

            if (doubleBackToExitPressedOnce) {
                RemovePaymentWindow();
                return;
            }
            doubleBackToExitPressedOnce = true;
            Toast.makeText(a.getApplicationContext(), "결제를 종료하시려면 '뒤로' 버튼을 한번 더 눌러주세요.", Toast.LENGTH_SHORT).show();
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    doubleBackToExitPressedOnce = false;
                }
            }, 2000);
        }});
    }


    public void Dismiss() {

        final Activity a = UnityPlayer.currentActivity;
        final CWebViewPlugin self = this;
        a.runOnUiThread(new Runnable() {public void run() {
            if (mWebView == null) {
                return;
            }
            if (mGlobalLayoutListener != null) {
                View activityRootView = a.getWindow().getDecorView().getRootView();
                activityRootView.getViewTreeObserver().removeOnGlobalLayoutListener(mGlobalLayoutListener);
                mGlobalLayoutListener = null;
            }
            mWebView.stopLoading();
            if (layout != null && mVideoView != null) {
                layout.removeView(mVideoView);
                layout.setBackgroundColor(0x00000000);
                mVideoView = null;
            }
            if(layout != null) layout.removeView(mWebView);
            mWebView.destroy();
            mWebView = null;

            if (mPaused) {
                if (mTransactions == null) {
                    mTransactions = new ArrayList<Pair<String, CWebViewPlugin>>();
                }
                mTransactions.add(Pair.create("remove", self));
            } else {
                a
                        .getFragmentManager()
                        .beginTransaction()
                        .remove(self)
                        .commit();
            }

        }});
    }

    public boolean SetURLPattern(final String allowPattern, final String denyPattern, final String hookPattern)
    {
        try {
            final Pattern allow = (allowPattern == null || allowPattern.length() == 0) ? null : Pattern.compile(allowPattern);
            final Pattern deny = (denyPattern == null || denyPattern.length() == 0) ? null : Pattern.compile(denyPattern);
            final Pattern hook = (hookPattern == null || hookPattern.length() == 0) ? null : Pattern.compile(hookPattern);
            final Activity a = UnityPlayer.currentActivity;
            a.runOnUiThread(new Runnable() {public void run() {
                mAllowRegex = allow;
                mDenyRegex = deny;
                mHookRegex = hook;
            }});
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void LoadURL(final String url) {
        final Activity a = UnityPlayer.currentActivity;
        a.runOnUiThread(new Runnable() {public void run() {
            if (mWebView == null) {
                return;
            }
            if (mCustomHeaders != null && !mCustomHeaders.isEmpty()) {
                mWebView.loadUrl(url, mCustomHeaders);
            } else {
                mWebView.loadUrl(url);;
            }
        }});
    }

    public void LoadHTML(final String html, final String baseURL)
    {
        final Activity a = UnityPlayer.currentActivity;
        a.runOnUiThread(new Runnable() {public void run() {
            if (mWebView == null) {
                return;
            }
            mWebView.loadDataWithBaseURL(baseURL, html, "text/html", "UTF8", null);
        }});
    }

    public void EvaluateJS(final String js) {
        final Activity a = UnityPlayer.currentActivity;
        a.runOnUiThread(new Runnable() {public void run() {
            if (mWebView == null) {
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                mWebView.evaluateJavascript(js, null);
            } else {
                mWebView.loadUrl("javascript:" + URLEncoder.encode(js));
            }
        }});
    }

    public void GoBack() {
        final Activity a = UnityPlayer.currentActivity;
        a.runOnUiThread(new Runnable() {public void run() {
            if (mWebView == null) {
                return;
            }
            mWebView.goBack();
        }});
    }

    public void GoForward() {
        final Activity a = UnityPlayer.currentActivity;
        a.runOnUiThread(new Runnable() {public void run() {
            if (mWebView == null) {
                return;
            }
            mWebView.goForward();
        }});
    }

    public void Reload() {
        final Activity a = UnityPlayer.currentActivity;
        a.runOnUiThread(new Runnable() {public void run() {
            if (mWebView == null) {
                return;
            }
            mWebView.reload();
        }});
    }

    public void SetMargins(int left, int top, int right, int bottom) {
        final FrameLayout.LayoutParams params
                = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
                Gravity.NO_GRAVITY);
        params.setMargins(left, top, right, bottom);
        final Activity a = UnityPlayer.currentActivity;
        a.runOnUiThread(new Runnable() {public void run() {
            if (mWebView == null) {
                return;
            }
            mWebView.setLayoutParams(params);
        }});
    }

    public void SetVisibility(final boolean visibility) {
        final Activity a = UnityPlayer.currentActivity;
        a.runOnUiThread(new Runnable() {public void run() {
            if (mWebView == null) {
                return;
            }
            if (visibility) {
                mWebView.setVisibility(View.VISIBLE);
                layout.requestFocus();
                mWebView.requestFocus();
            } else {
                mWebView.setVisibility(View.GONE);
            }
        }});
    }

    public void SetScrollbarsVisibility(final boolean visibility) {
        final Activity a = UnityPlayer.currentActivity;
        a.runOnUiThread(new Runnable() {public void run() {
            if (mWebView == null) {
                return;
            }
            mWebView.setHorizontalScrollBarEnabled(visibility);
            mWebView.setVerticalScrollBarEnabled(visibility);
        }});
    }

    public void SetAlertDialogEnabled(final boolean enabled) {
        final Activity a = UnityPlayer.currentActivity;
        a.runOnUiThread(new Runnable() {public void run() {
            mAlertDialogEnabled = enabled;
            if(chromeClient != null) chromeClient.setAlertDialogEnabled(mAlertDialogEnabled);
        }});
    }

    public void SetCameraAccess(final boolean allowed) {
        final Activity a = UnityPlayer.currentActivity;
        a.runOnUiThread(new Runnable() {public void run() {
            mAllowVideoCapture = allowed;
        }});
    }

    public void SetMicrophoneAccess(final boolean allowed) {
        final Activity a = UnityPlayer.currentActivity;
        a.runOnUiThread(new Runnable() {public void run() {
            mAllowAudioCapture = allowed;
        }});
    }

    // cf. https://stackoverflow.com/questions/31788748/webview-youtube-videos-playing-in-background-on-rotation-and-minimise/31789193#31789193
    public void OnApplicationPause(boolean paused) {
        mPaused = paused;
        final Activity a = UnityPlayer.currentActivity;
        a.runOnUiThread(new Runnable() {public void run() {
            if (!mPaused) {
                if (mTransactions != null) {
                    for (Pair<String, CWebViewPlugin> pair : mTransactions) {
                        CWebViewPlugin self = pair.second;
                        switch (pair.first) {
                            case "add":
                                a
                                        .getFragmentManager()
                                        .beginTransaction()
                                        .add(0, self, "CWebViewPlugin" + mInstanceId)
                                        .commit();
                                break;
                            case "remove":
                                a
                                        .getFragmentManager()
                                        .beginTransaction()
                                        .remove(self)
                                        .commit();
                                break;
                        }
                    }
                    mTransactions.clear();
                }
            }
            if (mWebView == null) {
                return;
            }
            if (mPaused) {
                mWebView.onPause();
                if (mWebView.getVisibility() == View.VISIBLE) {
                    // cf. https://qiita.com/nbhd/items/d31711faa8852143f3a4
                    mWebView.pauseTimers();
                }
            } else {
                mWebView.onResume();
                mWebView.resumeTimers();
            }
        }});
    }

    public void AddCustomHeader(final String headerKey, final String headerValue)
    {
        if (mCustomHeaders == null) {
            return;
        }
        mCustomHeaders.put(headerKey, headerValue);
    }

    public String GetCustomHeaderValue(final String headerKey)
    {
        if (mCustomHeaders == null) {
            return null;
        }

        if (!mCustomHeaders.containsKey(headerKey)) {
            return null;
        }
        return this.mCustomHeaders.get(headerKey);
    }

    public void RemoveCustomHeader(final String headerKey)
    {
        if (mCustomHeaders == null) {
            return;
        }

        if (this.mCustomHeaders.containsKey(headerKey)) {
            this.mCustomHeaders.remove(headerKey);
        }
    }

    public void ClearCustomHeader()
    {
        if (mCustomHeaders == null) {
            return;
        }

        this.mCustomHeaders.clear();
    }

    public void ClearCookies()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
        {
            CookieManager.getInstance().removeAllCookies(null);
            CookieManager.getInstance().flush();
        } else {
            final Activity a = UnityPlayer.currentActivity;
            CookieSyncManager cookieSyncManager = CookieSyncManager.createInstance(a);
            cookieSyncManager.startSync();
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.removeAllCookie();
            cookieManager.removeSessionCookie();
            cookieSyncManager.stopSync();
            cookieSyncManager.sync();
        }
    }

    public void SaveCookies()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
        {
            CookieManager.getInstance().flush();
        } else {
            final Activity a = UnityPlayer.currentActivity;
            CookieSyncManager cookieSyncManager = CookieSyncManager.createInstance(a);
            cookieSyncManager.startSync();
            cookieSyncManager.stopSync();
            cookieSyncManager.sync();
        }
    }

    public String GetCookies(String url)
    {
        CookieManager cookieManager = CookieManager.getInstance();
        return cookieManager.getCookie(url);
    }

    public void SetCookies(String url, List<String> setCookieHeaders)
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
        {
            CookieManager cookieManager = CookieManager.getInstance();
            for (String header : setCookieHeaders)
            {
                cookieManager.setCookie(url, header);
            }
            cookieManager.flush();
        } else {
            final Activity a = UnityPlayer.currentActivity;
            CookieSyncManager cookieSyncManager = CookieSyncManager.createInstance(a);
            cookieSyncManager.startSync();
            CookieManager cookieManager = CookieManager.getInstance();
            for (String header : setCookieHeaders)
            {
                cookieManager.setCookie(url, header);
            }
            cookieSyncManager.stopSync();
            cookieSyncManager.sync();
        }
    }

    public void SetBasicAuthInfo(final String userName, final String password)
    {
        mBasicAuthUserName = userName;
        mBasicAuthPassword = password;
    }

    public void ClearCache(final boolean includeDiskFiles)
    {
        final Activity a = UnityPlayer.currentActivity;
        a.runOnUiThread(new Runnable() {public void run() {
            if (mWebView == null) {
                return;
            }
            mWebView.clearCache(includeDiskFiles);
        }});
    }

    public void SetTextZoom(final int textZoom)
    {
        final Activity a = UnityPlayer.currentActivity;
        a.runOnUiThread(new Runnable() {public void run() {
            if (mWebView == null) {
                return;
            }
            mWebView.getSettings().setTextZoom(textZoom);
        }});
    }
}

