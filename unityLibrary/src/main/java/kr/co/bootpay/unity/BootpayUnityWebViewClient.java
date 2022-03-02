package kr.co.bootpay.unity;

import android.annotation.TargetApi;
import android.app.Activity;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.os.Build;
import android.util.Base64;
import android.view.KeyEvent;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.HttpAuthHandler;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.Nullable;

import com.unity3d.player.UnityPlayer;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;

import kr.co.bootpay.core.webview.BootpayUrlHelper;

public class BootpayUnityWebViewClient extends WebViewClient {

    protected boolean isCDNLoaded = false;

    private Hashtable<String, String> mCustomHeaders;

    private BootpayUnityWebView.BootpayJavascriptBridge mJavascriptBridge;
//    private CWebViewPluginInterface mWebViewPlugin;
//    private String mWebViewUA;
    private String mBasicAuthUserName;
    private String mBasicAuthPassword;


    protected @Nullable
    String ignoreErrFailedForThisURL = null;

    public BootpayUnityWebViewClient(BootpayUnityWebView.BootpayJavascriptBridge javascriptBridge, String authUserName, String authPassword) {
//        this.mWebViewPlugin = webViewInterface;
        this.mJavascriptBridge = javascriptBridge;
        this.mBasicAuthUserName = authUserName;
        this.mBasicAuthPassword = authPassword;
    }

//    public void setBasicAuth(String userName, String password) {
//        this.mBasicAuthUserName = userName;
//        this.mBasicAuthPassword = password;
//    }

    public void setIgnoreErrFailedForThisURL(@Nullable String url) {
        this.ignoreErrFailedForThisURL = url;
    }

    @Override
    public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
        view.loadUrl("about:blank");
        mJavascriptBridge.call("CallOnError", errorCode + "\t" + description + "\t" + failingUrl);
    }

    @Override
    public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
//        canGoBack = webView.canGoBack();
//        canGoForward = webView.canGoForward();
//        mWebViewPlugin.call("CallOnHttpError", Integer.toString(errorResponse.getStatusCode()));
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
//        canGoBack = webView.canGoBack();
//        canGoForward = webView.canGoForward();
//        mWebViewPlugin.call("CallOnStarted", url);
    }

    @Override
    public void onLoadResource(WebView view, String url) {
//        canGoBack = webView.canGoBack();
//        canGoForward = webView.canGoForward();
    }

    @Override
    public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler, String host, String realm) {
//        if (mBasicAuthUserName != null && mBasicAuthPassword != null) {
//            handler.proceed(mBasicAuthUserName, mBasicAuthPassword);
//        } else {
//            handler.cancel();
//        }
    }


    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, final String url) {
        if (mCustomHeaders == null || mCustomHeaders.isEmpty()) {
            return super.shouldInterceptRequest(view, url);
        }

        try {
            HttpURLConnection urlCon = (HttpURLConnection) (new URL(url)).openConnection();
            urlCon.setInstanceFollowRedirects(false);
            // The following should make HttpURLConnection have a same user-agent of webView)
            // cf. http://d.hatena.ne.jp/faw/20070903/1188796959 (in Japanese)
//            urlCon.setRequestProperty("User-Agent", mWebViewUA);

            if (mBasicAuthUserName != null && mBasicAuthPassword != null) {
                String authorization = mBasicAuthUserName + ":" + mBasicAuthPassword;
                urlCon.setRequestProperty("Authorization", "Basic " + Base64.encodeToString(authorization.getBytes(), Base64.NO_WRAP));
            }

            if (Build.VERSION.SDK_INT != Build.VERSION_CODES.KITKAT && Build.VERSION.SDK_INT != Build.VERSION_CODES.KITKAT_WATCH) {
                // cf. https://issuetracker.google.com/issues/36989494
                String cookies = GetCookies(url);
                if (cookies != null && !cookies.isEmpty()) {
                    urlCon.addRequestProperty("Cookie", cookies);
                }
            }

            for (HashMap.Entry<String, String> entry: mCustomHeaders.entrySet()) {
                urlCon.setRequestProperty(entry.getKey(), entry.getValue());
            }

            urlCon.connect();

            int responseCode = urlCon.getResponseCode();
            if (responseCode >= 300 && responseCode < 400) {
                // To avoid a problem due to a mismatch between requested URL and returned content,
                // make WebView request again in the case that redirection response was returned.
                return null;
            }

            final List<String> setCookieHeaders = urlCon.getHeaderFields().get("Set-Cookie");
            if (setCookieHeaders != null) {
                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT || Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT_WATCH) {
                    // In addition to getCookie, setCookie cause deadlock on Android 4.4.4 cf. https://issuetracker.google.com/issues/36989494
                    UnityPlayer.currentActivity.runOnUiThread(new Runnable() {
                        public void run() {
                            SetCookies(url, setCookieHeaders);
                        }
                    });
                } else {
                    SetCookies(url, setCookieHeaders);
                }
            }

            return new WebResourceResponse(
                    urlCon.getContentType().split(";", 2)[0],
                    urlCon.getContentEncoding(),
                    urlCon.getInputStream()
            );

        } catch (Exception e) {
            return super.shouldInterceptRequest(view, url);
        }
    }



    @Override
    public void onPageFinished(WebView webView, String url) {
        super.onPageFinished(webView, url);

        if (!isCDNLoaded) {
            BootpayUnityWebView _webView = (BootpayUnityWebView) webView;
            _webView.callInjectedJavaScriptBeforePayStart();
            _webView.callInjectedJavaScript();
            isCDNLoaded = true;
        }

        mJavascriptBridge.call("CallOnLoaded", url);
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        return BootpayUrlHelper.shouldOverrideUrlLoading(view, url);
    }


    @TargetApi(Build.VERSION_CODES.N)
    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        final String url = request.getUrl().toString();
        return this.shouldOverrideUrlLoading(view, url);
    }

    @Override
    public boolean shouldOverrideKeyEvent(WebView view, KeyEvent event) {
//        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) back();
        return super.shouldOverrideKeyEvent(view, event);
    }



    @Override
    public void onReceivedSslError(final WebView webView, final SslErrorHandler handler, final SslError error) {
        handler.proceed();
//        String topWindowUrl = webView.getUrl();
//        String failingUrl = error.getUrl();
//
//        handler.cancel();
//
//        if (!topWindowUrl.equalsIgnoreCase(failingUrl)) {
//            // If error is not due to top-level navigation, then do not call onReceivedError()
//            return;
//        }
//
//        int code = error.getPrimaryError();
//        String description = "";
//        String descriptionPrefix = "SSL error: ";
//
//        // https://developer.android.com/reference/android/net/http/SslError.html
//        switch (code) {
//            case SslError.SSL_DATE_INVALID:
//                description = "The date of the certificate is invalid";
//                break;
//            case SslError.SSL_EXPIRED:
//                description = "The certificate has expired";
//                break;
//            case SslError.SSL_IDMISMATCH:
//                description = "Hostname mismatch";
//                break;
//            case SslError.SSL_INVALID:
//                description = "A generic error occurred";
//                break;
//            case SslError.SSL_NOTYETVALID:
//                description = "The certificate is not yet valid";
//                break;
//            case SslError.SSL_UNTRUSTED:
//                description = "The certificate authority is not trusted";
//                break;
//            default:
//                description = "Unknown SSL Error";
//                break;
//        }
//
//        description = descriptionPrefix + description;
//
//        this.onReceivedError(
//                webView,
//                code,
//                description,
//                failingUrl
//        );
    }

//    protected HashMap createWebViewEvent(WebView webView, String url) {
//        HashMap<String, Object> event = new HashMap<>();
//        event.put("target", webView.getId());
//        event.put("url", url);
////        event.put("loading", !mLastLoadFailed && webView.getProgress() != 100);
//        event.put("title", webView.getTitle());
//        event.put("canGoBack", webView.canGoBack());
//        event.put("canGoForward", webView.canGoForward());
//        return event;
//    }

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
}

