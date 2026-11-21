package __PACKAGE__;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class Main extends Activity {
    private WebView web;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LinkedBlockingQueue events = new LinkedBlockingQueue();

    public void onCreate(Bundle saved) {
        super.onCreate(saved);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        web.addJavascriptInterface(new Bridge(), "Stroka");
        setContentView(web, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
        extractPy();
        final Main self = this;
        new Thread(new Runnable() {
            public void run() {
                try {
                    if (!Python.isStarted()) Python.start(new AndroidPlatform(self));
                    File dir = new File(getFilesDir(), "py");
                    Python.getInstance().getModule("sys").get("path").callAttr("insert", 0, dir.getAbsolutePath());
                    Python.getInstance().getModule("stroka_py_app").callAttr("run", self);
                } catch (final Throwable e) {
                    final String msg = e.getMessage() == null ? String.valueOf(e) : e.getMessage();
                    main.post(new Runnable() {
                        public void run() {
                            web.loadDataWithBaseURL("https://stroka.local/",
                                "<!doctype html><meta charset=utf-8><pre>" + escape(msg) + "</pre>",
                                "text/html", "utf-8", null);
                        }
                    });
                }
            }
        }, "py-app").start();
    }

    private void extractPy() {
        File dir = new File(getFilesDir(), "py");
        dir.mkdirs();
        String[] names = new String[] { "stroka_tkinter.py", "stroka_py_app.py", "program.py", "FILENAME.txt" };
        for (int i = 0; i < names.length; i++) {
            try {
                InputStream in = getAssets().open("py/" + names[i]);
                FileOutputStream out = new FileOutputStream(new File(dir, names[i]));
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                out.close();
                in.close();
            } catch (Exception ignored) {
            }
        }
    }

    public void showPreview(final String html) {
        main.post(new Runnable() {
            public void run() {
                web.loadDataWithBaseURL("https://stroka.local/", html, "text/html", "utf-8", null);
            }
        });
    }

    public String waitUiEvent(long timeoutMs) {
        try {
            Object v = events.poll(timeoutMs, TimeUnit.MILLISECONDS);
            return v == null ? "" : String.valueOf(v);
        } catch (InterruptedException e) {
            return "";
        }
    }

    public void postUiEvent(String event) {
        events.offer(event);
    }

    public class Bridge {
        @JavascriptInterface
        public void onUiEvent(String event) {
            postUiEvent(event);
        }
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
