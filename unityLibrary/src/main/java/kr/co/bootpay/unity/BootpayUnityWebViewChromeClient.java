package kr.co.bootpay.unity;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Message;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.GeolocationPermissions;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.FrameLayout;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;

import kr.co.bootpay.core.constants.BootpayBuildConfig;
//import kr.co.bootpay.core.webview.BootpayWebView;

public class BootpayUnityWebViewChromeClient extends WebChromeClient {
    protected Context mContext;
    private FrameLayout mLayout = null;
    private View mVideoView;
    private boolean mAlertDialogEnabled = true;

    BootpayUnityWebView mainView;

    public BootpayUnityWebViewChromeClient(Context context, FrameLayout layout, View videoView) {
        this.mContext = context;
        this.mLayout = layout;
        this.mVideoView = videoView;
    }

    public void setAlertDialogEnabled(boolean value) {
        this.mAlertDialogEnabled = value;
    }

    @Override
    public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
        mainView = (BootpayUnityWebView) view;

        BootpayUnityWebView newWindow = new BootpayUnityWebView(view.getContext());
        newWindow.setWebChromeClient(new BootpayUnityWebViewChromeClient(mainView.getContext(), mainView.getLayout(), mainView.getVideoView()));
        newWindow.setWebViewClient(new BootpayUnityWebViewClient(mainView.getJavascriptBridge(), "", ""));


        BootpayUnityWebView webview = (BootpayUnityWebView) view;
        newWindow.setEventListener(webview.getEventListener());
        newWindow.setInjectedJS(webview.getInjectedJS());
        newWindow.setInjectedJSBeforePayStart(webview.getInjectedJSBeforePayStart());

        view.addView(newWindow,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Gravity.NO_GRAVITY)
        );

        final WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
        transport.setWebView(newWindow);
        resultMsg.sendToTarget();

        return true;
    }

    @Override
    public void onShowCustomView(View view, CustomViewCallback callback) {
        super.onShowCustomView(view, callback);
        if (mLayout != null) {
            mVideoView = view;
            mLayout.setBackgroundColor(0xff000000);
            mLayout.addView(mVideoView);
        }
    }

    @Override
    public void onHideCustomView() {
        super.onHideCustomView();
        if (mLayout != null) {
            mLayout.removeView(mVideoView);
            mLayout.setBackgroundColor(0x00000000);
            mVideoView = null;
        }
    }

    @Override
    public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
        if (!mAlertDialogEnabled) {
            result.cancel();
            return true;
        }
        return super.onJsAlert(view, url, message, result);
    }

    @Override
    public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
        if (!mAlertDialogEnabled) {
            result.cancel();
            return true;
        }
        return super.onJsConfirm(view, url, message, result);
    }

    @Override
    public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
        if (!mAlertDialogEnabled) {
            result.cancel();
            return true;
        }
        return super.onJsPrompt(view, url, message, defaultValue, result);
    }

    @Override
    public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
        callback.invoke(origin, true, false);
    }

    @Override
    public void onCloseWindow(WebView window) {
        super.onCloseWindow(window);
        if(mainView != null) mainView.removeView(window);
        window.setVisibility(View.GONE);
    }

    @Override
    public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
        if(BootpayBuildConfig.DEBUG) {
            return super.onConsoleMessage(consoleMessage);
        }

        return true;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onPermissionRequest(final PermissionRequest request) {
        String[] requestedResources = request.getResources();
        ArrayList<String> permissions = new ArrayList<>();
        ArrayList<String> grantedPermissions = new ArrayList<String>();
        for (int i = 0; i < requestedResources.length; i++) {
            if (requestedResources[i].equals(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                permissions.add(Manifest.permission.RECORD_AUDIO);
            } else if (requestedResources[i].equals(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                permissions.add(Manifest.permission.CAMERA);
            }
            // TODO: RESOURCE_MIDI_SYSEX, RESOURCE_PROTECTED_MEDIA_ID.
        }

        for (int i = 0; i < permissions.size(); i++) {
            if (ContextCompat.checkSelfPermission(mContext, permissions.get(i)) != PackageManager.PERMISSION_GRANTED) {
                continue;
            }
            if (permissions.get(i).equals(Manifest.permission.RECORD_AUDIO)) {
                grantedPermissions.add(PermissionRequest.RESOURCE_AUDIO_CAPTURE);
            } else if (permissions.get(i).equals(Manifest.permission.CAMERA)) {
                grantedPermissions.add(PermissionRequest.RESOURCE_VIDEO_CAPTURE);
            }
        }

        if (grantedPermissions.isEmpty()) {
            request.deny();
        } else {
            String[] grantedPermissionsArray = new String[grantedPermissions.size()];
            grantedPermissionsArray = grantedPermissions.toArray(grantedPermissionsArray);
            request.grant(grantedPermissionsArray);
        }
    }

}
