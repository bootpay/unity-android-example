package kr.co.bootpay.unity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.unity3d.player.UnityPlayer;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import kr.co.bootpay.core.api.BootpayDialog;
import kr.co.bootpay.core.api.BootpayDialogX;
import kr.co.bootpay.core.constants.BootpayBuildConfig;
import kr.co.bootpay.core.constants.BootpayConstants;
import kr.co.bootpay.core.events.BootpayEventListener;
//import kr.co.bootpay.core.events.JSInterfaceBridge;
import kr.co.bootpay.core.models.Payload;
//import kr.co.bootpay.core.webview.BootpayWebViewClient;

public class BootpayUnityWebView extends WebView {
    Context context;
    FrameLayout layout;
    View mVideoView;
    CWebViewPlugin mWebViewPlugin;

    public FrameLayout getLayout() {
        return layout;
    }

    public View getVideoView() {
        return mVideoView;
    }

    public CWebViewPlugin getWebViewPlugin() {
        return mWebViewPlugin;
    }

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

    private ValueCallback<Uri> mUploadMessage;
    private ValueCallback<Uri[]> mFilePathCallback;
    private Uri mCameraPhotoUri;

    Pattern mBasicAuthUserName;
    Pattern mBasicAuthPassword;



    /* bootpay */
//    BootpayDialog mDialog;
//    BootpayDialogX mDialogX;

//    private Payload mPayload;
//    BootpayWebViewClient mWebViewClient;
    BootpayEventListener mEventListener;

    protected @Nullable
    String injectedJS;

    protected @Nullable
    List<String> injectedJSBeforePayStart;

    BootpayJavascriptBridge javascriptBridge;

    public BootpayUnityWebView(@NonNull Context context) {
        super(context);
        this.context = context;
        payWebSettings(context);
    }

    public BootpayUnityWebView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        payWebSettings(context);
    }

    public BootpayUnityWebView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.context = context;
        payWebSettings(context);
    }
    public BootpayUnityWebView(
            @NonNull Context context,
            @NonNull FrameLayout layout,
            @NonNull View videoView,
            @NonNull CWebViewPlugin plugin) {
        super(context);

        this.context = context;
        this.layout = layout;
        this.mVideoView = videoView;
        this.mWebViewPlugin = plugin;

        payWebSettings(context);
    }

    void setAlertDialogEnabled(boolean value) { this.mAlertDialogEnabled = value; }

    void setAlertVideoCapture(boolean value) { this.mAllowVideoCapture = value; }

    void setAlertAudioCapture(boolean value) { this.mAllowAudioCapture = value; }

    void setUploadMessage(ValueCallback<Uri> message) {
        this.mUploadMessage = message;
    }

    void setFilePathCallback(ValueCallback<Uri[]> callback) {
        this.mFilePathCallback = callback;
    }

    void setIgnoreSSLError() {

    }

    public void setInjectedJS(@Nullable String injectedJS) {
        this.injectedJS = injectedJS;
    }

    public void setInjectedJSBeforePayStart(@Nullable List<String> injectedJSBeforePayStart) {
        this.injectedJSBeforePayStart = injectedJSBeforePayStart;
    }

    public BootpayJavascriptBridge getJavascriptBridge() {
        return javascriptBridge;
    }

    @SuppressLint("JavascriptInterface")
    void payWebSettings(Context context) {
        javascriptBridge = new BootpayJavascriptBridge(this.mWebViewPlugin , "Unity");



        addJavascriptInterface(javascriptBridge, "Android");
        getSettings().setAppCacheEnabled(true);
        getSettings().setAllowFileAccess(false);
        getSettings().setAllowContentAccess(false);
        getSettings().setBuiltInZoomControls(true);
        getSettings().setDisplayZoomControls(false);
        getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        getSettings().setDomStorageEnabled(true);
        getSettings().setJavaScriptEnabled(true);
        getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        getSettings().setLoadsImagesAutomatically(true);
        getSettings().setLoadWithOverviewMode(true);
        getSettings().setUseWideViewPort(true);
        getSettings().setSupportMultipleWindows(true);

        if (BootpayBuildConfig.DEBUG == true && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            context.getApplicationInfo().flags &=  context.getApplicationInfo().FLAG_DEBUGGABLE;
            if (0 != context.getApplicationInfo().flags)  setWebContentsDebuggingEnabled(true);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            getSettings().setAllowFileAccessFromFileURLs(false);
            getSettings().setAllowUniversalAccessFromFileURLs(false);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            CookieManager.getInstance().setAcceptCookie(true);
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true);
        }
    }

    public void removePaymentWindow() {
        load("BootPay.removePaymentWindow();");
//        if(mDialog != null) mDialog.removePaymentWindow();
//        else if(mDialogX != null) mDialogX.removePaymentWindow();

        final Activity currentActivity = UnityPlayer.currentActivity;
        currentActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(layout == null) return;
                layout.removeAllViews();
                if(layout.getParent() != null) ((ViewGroup)layout.getParent()).removeView(layout);
                layout = null;
            }
        });
    }

    public void startBootpay(Payload payload, BootpayEventListener listener) {
//        this.mPayload = payload;
//        this.mEventListener = listener;
        boolean quickPopup = false;
        if(payload != null && payload.getExtra() != null && payload.getExtra().getPopup() == 1) quickPopup = true;
        if(listener != null) setEventListener(listener);
        if(injectedJS == null || injectedJS.length() == 0) setInjectedJS(BootpayConstants.getJSPay(payload));
        if(injectedJSBeforePayStart == null || injectedJSBeforePayStart.size() == 0) setInjectedJSBeforePayStart(BootpayConstants.getJSBeforePayStart(getContext(), quickPopup));
        connectBootpay();
    }


//    public void startBootpay() {
//        connectBootpay();
//    }

    public void transactionConfirm(String data) {
        load("var data = JSON.parse('" + data + "'); BootPay.transactionConfirm(data);");
    }

    private void load(String script) {
        post(() -> loadUrl(String.format(Locale.KOREA, "javascript:(function(){%s})()", script)));
    }

    public class BootpayJavascriptBridge implements JSInterfaceBridge {

        private CWebViewPlugin mPlugin;
        private String mGameObject;

        public BootpayJavascriptBridge(CWebViewPlugin plugin, String gameObject) {
            mPlugin = plugin;
            mGameObject = gameObject;
        }

        @JavascriptInterface
        @Override
        public void error(String data) {
            if (mEventListener != null) mEventListener.onError(data);
        }

        @JavascriptInterface
        @Override
        public void close(String data) {
            if (mEventListener != null) mEventListener.onClose(data);
        }

        @JavascriptInterface
        @Override
        public void cancel(String data) {
            if (mEventListener != null) mEventListener.onCancel(data);
        }

        @JavascriptInterface
        @Override
        public void ready(String data) {
            if (mEventListener != null) mEventListener.onReady(data);
        }

        @JavascriptInterface
        @Override
        public String confirm(String data) {
            boolean goTransaction = false;
            if (mEventListener != null) goTransaction = mEventListener.onConfirm(data);
            if(goTransaction) transactionConfirm(data);
            return String.valueOf(goTransaction);
        }

        @JavascriptInterface
        @Override
        public void done(String data) {
            if (mEventListener != null) mEventListener.onDone(data);
        }

        @JavascriptInterface 
        public void call(final String message) {
            call("CallFromJS", message);
        }

        public void call(final String method, final String message) {
            final Activity a = UnityPlayer.currentActivity;
            a.runOnUiThread(new Runnable() {public void run() {
                if (mPlugin.IsInitialized()) {
                    UnityPlayer.UnitySendMessage(mGameObject, method, message);
                }
            }});
        }
    }

    public void connectBootpay() {
        loadUrl(BootpayConstants.CDN_URL);
    }


//    boolean doubleBackToExitPressedOnce = false;
//    public boolean isBackRemoveWindow() {
//        if (doubleBackToExitPressedOnce) {
//            return true;
//        }
//        doubleBackToExitPressedOnce = true;
//        Toast.makeText(getContext(), "결제를 종료하시려면 '뒤로' 버튼을 한번 더 눌러주세요.", Toast.LENGTH_SHORT).show();
//        new Handler().postDelayed(new Runnable() {
//            @Override
//            public void run() {
//                doubleBackToExitPressedOnce = false;
//            }
//        }, 2000);
//        return false;
//    }

    void evaluateJavascriptWithFallback(String script) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            evaluateJavascript(script, null);
            return;
        }

        try {
            loadUrl("javascript:" + URLEncoder.encode(script, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            // UTF-8 should always be supported
            throw new RuntimeException(e);
        }
    }

    void callJavaScript(@Nullable String script) {
        if (getSettings().getJavaScriptEnabled() &&
                script != null &&
                !TextUtils.isEmpty(script)) {
            evaluateJavascriptWithFallback("(function() {\n" + script + ";\n})();");
        }
    }

    public void setEventListener(BootpayEventListener listener) {
        this.mEventListener = listener;
    }

    public void callInjectedJavaScript() {

        callJavaScript(injectedJS);
    }

    public void callInjectedJavaScriptBeforePayStart() {
        if(injectedJSBeforePayStart == null) return;
        for(String js : injectedJSBeforePayStart) {
            callJavaScript(js);
        }
    }

    public void setIgnoreErrFailedForThisURL(@Nullable String url) {
//        if(mWebViewClient != null) mWebViewClient.setIgnoreErrFailedForThisURL(url);
    }


    public BootpayEventListener getEventListener() {
        return mEventListener;
    }

    @Nullable
    public String getInjectedJS() {
        return injectedJS;
    }

    @Nullable
    public List<String> getInjectedJSBeforePayStart() {
        return injectedJSBeforePayStart;
    }
}
