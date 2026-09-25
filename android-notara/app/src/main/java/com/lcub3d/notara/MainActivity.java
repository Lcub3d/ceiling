package com.lcub3d.notara;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int FILE_CHOOSER_REQUEST = 1001;
    private static final String PREFS = "notara_android";
    private static final String PREF_BASE_URL = "base_url";

    private WebView webView;
    private ValueCallback<Uri[]> fileChooserCallback;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Notara");

        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            s.setSafeBrowsingEnabled(true);
        }

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookies.setAcceptThirdPartyCookies(webView, true);
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleNavigation(request.getUrl());
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleNavigation(Uri.parse(url));
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    showConnectionError();
                }
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                if (request.isForMainFrame() && errorResponse.getStatusCode() >= 400) {
                    showConnectionError();
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(
                    WebView webView,
                    ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams) {
                if (fileChooserCallback != null) {
                    fileChooserCallback.onReceiveValue(null);
                }
                fileChooserCallback = filePathCallback;

                Intent intent;
                try {
                    intent = fileChooserParams.createIntent();
                } catch (Exception e) {
                    intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                }

                try {
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                    return true;
                } catch (Exception e) {
                    fileChooserCallback = null;
                    Toast.makeText(MainActivity.this, "无法打开文件选择器", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception e) {
                Toast.makeText(this, "无法打开下载链接", Toast.LENGTH_SHORT).show();
            }
        });

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            String saved = getBaseUrl();
            if (saved.isEmpty()) {
                showSetupPage();
                webView.postDelayed(this::showServerDialog, 250);
            } else {
                webView.loadUrl(saved);
            }
        }
    }

    private boolean handleNavigation(Uri uri) {
        String scheme = uri.getScheme();
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
            return false;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception ignored) {
        }
        return true;
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private String getBaseUrl() {
        return prefs().getString(PREF_BASE_URL, "");
    }

    private void loadHome() {
        String url = getBaseUrl();
        if (url.isEmpty()) {
            showSetupPage();
            showServerDialog();
        } else {
            webView.loadUrl(url);
        }
    }

    private String normalizeUrl(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            return "";
        }
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "https://" + value;
        }
        if (!value.endsWith("/")) {
            value += "/";
        }
        return value;
    }

    private void showSetupPage() {
        String html =
                "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'>" +
                "<style>body{font-family:sans-serif;padding:28px;line-height:1.6;color:#222}" +
                "h2{margin-top:24px}code{background:#f3f3f3;padding:2px 5px;border-radius:4px}" +
                ".box{background:#f6f6f6;padding:16px;border-radius:12px;margin-top:18px}</style></head>" +
                "<body><h2>连接 Notara</h2>" +
                "<p>这个 Android 测试版是 Notara 的客户端壳，需要连接一个正在运行的 Notara 服务器。</p>" +
                "<div class='box'><b>首次使用</b><br>点右上角 ⋮ → <b>服务器地址</b>，填入你的地址，例如：<br>" +
                "<code>https://notes.example.com</code><br>局域网也可以，例如 <code>http://192.168.1.20:3000</code>。</div>" +
                "<p>Notara 官方目前没有 Android Release APK；此测试版不在手机本地运行 Notara 服务端。</p>" +
                "</body></html>";
        webView.loadDataWithBaseURL("https://notara-android.local/", html, "text/html", "UTF-8", null);
    }

    private void showConnectionError() {
        String html =
                "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'>" +
                "<style>body{font-family:sans-serif;padding:28px;line-height:1.6;color:#222}" +
                ".box{background:#fff4f2;padding:16px;border-radius:12px}</style></head>" +
                "<body><h2>无法连接 Notara</h2><div class='box'>" +
                "当前服务器没有响应。请检查服务器是否启动、地址是否正确，以及手机能否访问该地址。" +
                "</div><p>点右上角 ⋮ → <b>服务器地址</b> 更换地址，或选择 <b>刷新</b> 重试。</p>" +
                "</body></html>";
        webView.loadDataWithBaseURL("https://notara-android.local/", html, "text/html", "UTF-8", null);
    }

    private void showServerDialog() {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("https://notes.example.com");
        input.setText(getBaseUrl());
        input.setSelectAllOnFocus(true);

        new AlertDialog.Builder(this)
                .setTitle("Notara 服务器地址")
                .setMessage("请输入你的自托管 Notara 地址。官方 Demo 当前不可作为可靠默认入口。")
                .setView(input)
                .setPositiveButton("连接", (dialog, which) -> {
                    String url = normalizeUrl(input.getText().toString());
                    if (url.isEmpty()) {
                        Toast.makeText(this, "服务器地址不能为空", Toast.LENGTH_SHORT).show();
                        showSetupPage();
                        return;
                    }
                    prefs().edit().putString(PREF_BASE_URL, url).apply();
                    webView.loadUrl(url);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add("服务器地址");
        menu.add("刷新");
        menu.add("回到首页");
        menu.add("清除服务器");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        String title = String.valueOf(item.getTitle());
        if ("服务器地址".equals(title)) {
            showServerDialog();
            return true;
        }
        if ("刷新".equals(title)) {
            String url = getBaseUrl();
            if (url.isEmpty()) {
                showSetupPage();
            } else {
                webView.loadUrl(url);
            }
            return true;
        }
        if ("回到首页".equals(title)) {
            loadHome();
            return true;
        }
        if ("清除服务器".equals(title)) {
            prefs().edit().remove(PREF_BASE_URL).apply();
            showSetupPage();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_REQUEST) {
            Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            if (fileChooserCallback != null) {
                fileChooserCallback.onReceiveValue(result);
                fileChooserCallback = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (fileChooserCallback != null) {
            fileChooserCallback.onReceiveValue(null);
            fileChooserCallback = null;
        }
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
