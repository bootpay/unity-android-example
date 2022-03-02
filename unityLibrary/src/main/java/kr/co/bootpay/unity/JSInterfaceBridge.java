package kr.co.bootpay.unity;

import android.webkit.JavascriptInterface;

public interface JSInterfaceBridge {
    @JavascriptInterface
    void error(String data);

    @JavascriptInterface
    void close(String data);

    @JavascriptInterface
    void cancel(String data);

    @JavascriptInterface
    void ready(String data);

    @JavascriptInterface
    String confirm(String data);

    @JavascriptInterface
    void done(String data);

    @JavascriptInterface
    void call(String data);
}
