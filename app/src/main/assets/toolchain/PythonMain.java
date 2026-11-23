package __PACKAGE__;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.view.View;
import android.view.WindowInsets;
import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class Main extends Activity {
    private WebView web;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LinkedBlockingQueue<String> events = new LinkedBlockingQueue<String>();
    private final LinkedBlockingQueue<String> inputs = new LinkedBlockingQueue<String>();
    private final StringBuilder pending = new StringBuilder();
    private volatile boolean pageReady = false;
    private volatile boolean tkMode = false;
    private volatile boolean finished = false;

    public void onCreate(Bundle saved) {
        super.onCreate(saved);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDefaultTextEncodingName("utf-8");
        web.addJavascriptInterface(new Bridge(), "Stroka");
        FrameLayout root = new FrameLayout(this);
        root.addView(web, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                view.setPadding(
                    insets.getSystemWindowInsetLeft(),
                    insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(),
                    insets.getSystemWindowInsetBottom());
                return insets;
            }
        });
        setContentView(root);
        web.loadDataWithBaseURL("https://stroka.local/", consolePage(String.valueOf(getTitle())), "text/html", "utf-8", null);
        final Main self = this;
        new Thread(new Runnable() {
            public void run() {
                try {
                    extractPy();
                    if (!Python.isStarted()) Python.start(new AndroidPlatform(self));
                    File dir = new File(getFilesDir(), "py");
                    Python.getInstance().getModule("sys").get("path").callAttr("insert", 0, dir.getAbsolutePath());
                    final PyObject app = Python.getInstance().getModule("stroka_py_app");
                    String mode = app.callAttr("prepare", self).toString();
                    if ("native".equals(mode)) {
                        main.post(new Runnable() {
                            public void run() {
                                try {
                                    app.callAttr("run_native", self);
                                } catch (Throwable e) {
                                    String msg = e.getMessage() == null ? String.valueOf(e) : e.getMessage();
                                    showConsole();
                                    consoleWrite(msg + "\n", true);
                                }
                            }
                        });
                    } else {
                        app.callAttr("run", self);
                    }
                } catch (Throwable e) {
                    String msg = e.getMessage() == null ? String.valueOf(e) : e.getMessage();
                    consoleWrite("Ошибка запуска Python: " + msg + "\n", true);
                    consoleDone();
                }
            }
        }, "py-app").start();
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        in.close();
        return out.toByteArray();
    }

    private void extractPy() throws Exception {
        File dir = new File(getFilesDir(), "py");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new Exception("Не удалось создать папку " + dir);
        String[] names = new String[] { "stroka.py", "stroka_tkinter.py", "stroka_py_app.py", "program.py", "FILENAME.txt" };
        for (int i = 0; i < names.length; i++) {
            byte[] data = readAll(getAssets().open("py/" + names[i]));
            FileOutputStream out = new FileOutputStream(new File(dir, names[i]));
            out.write(data);
            out.close();
        }
    }

    private static String js(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') b.append("\\\\");
            else if (c == '"') b.append("\\\"");
            else if (c == '\n') b.append("\\n");
            else if (c == '\r') b.append("\\r");
            else if (c == '\u2028' || c == '\u2029' || c < 0x20) b.append(String.format("\\u%04x", (int) c));
            else b.append(c);
        }
        return b.append('"').toString();
    }

    private void evalJs(final String code) {
        main.post(new Runnable() {
            public void run() {
                web.evaluateJavascript(code, null);
            }
        });
    }

    public void consoleWrite(String text, boolean err) {
        if (text == null || text.length() == 0) return;
        String call = "window.strokaOut&&strokaOut(" + js(text) + "," + (err ? "true" : "false") + ")";
        synchronized (pending) {
            if (!pageReady) {
                pending.append(call).append(";\n");
                return;
            }
        }
        evalJs(call);
    }

    public String consoleInput(String prompt) {
        if (finished) return null;
        if (tkMode) return "";
        evalJs("window.strokaAsk&&strokaAsk(" + js(prompt == null ? "" : prompt) + ")");
        try {
            String v = inputs.take();
            return "\u0000EOF".equals(v) ? null : v;
        } catch (InterruptedException e) {
            return null;
        }
    }

    public void showConsole() {
        main.post(new Runnable() {
            public void run() {
                if (web.getParent() == null) {
                    setContentView(web, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
                }
            }
        });
    }

    public void consoleDone() {
        evalJs("window.strokaDone&&strokaDone()");
    }

    public void showPreview(final String html) {
        tkMode = true;
        main.post(new Runnable() {
            public void run() {
                web.loadDataWithBaseURL("https://stroka.local/", html, "text/html", "utf-8", null);
            }
        });
    }

    public String waitUiEvent(long timeoutMs) {
        try {
            String v = events.poll(timeoutMs, TimeUnit.MILLISECONDS);
            return v == null ? "" : v;
        } catch (InterruptedException e) {
            return "";
        }
    }

    public String waitUiEvent() {
        return waitUiEvent(1000L);
    }

    public void postUiEvent(String event) {
        events.offer(event);
    }

    protected void onDestroy() {
        finished = true;
        inputs.offer("\u0000EOF");
        super.onDestroy();
    }

    public class Bridge {
        @JavascriptInterface
        public void onUiEvent(String event) {
            postUiEvent(event);
        }

        @JavascriptInterface
        public void onInput(String line) {
            inputs.offer(line == null ? "" : line);
        }

        @JavascriptInterface
        public void onReady() {
            String code;
            synchronized (pending) {
                pageReady = true;
                code = pending.toString();
                pending.setLength(0);
            }
            if (code.length() > 0) evalJs(code);
        }
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String consolePage(String title) {
        return "<!doctype html><html lang=ru><head><meta charset=utf-8>"
            + "<meta name=viewport content='width=device-width,initial-scale=1'>"
            + "<style>"
            + "html,body{margin:0;height:100%;background:#15161c;color:#e8e8ee;font-family:sans-serif}"
            + "body{display:flex;flex-direction:column}"
            + "header{padding:12px 16px;font-weight:600;background:#1f2029;border-bottom:1px solid #2c2d38}"
            + "#out{flex:1;overflow:auto;margin:0;padding:12px 16px;font:15px/1.45 monospace;white-space:pre-wrap;word-break:break-word}"
            + ".err{color:#ff7b7b}.echo{color:#8fd18f}.info{color:#9aa0b4}"
            + "form{display:flex;gap:8px;padding:10px 12px;background:#1f2029;border-top:1px solid #2c2d38}"
            + "#q{flex:1;font-size:16px;padding:10px;border-radius:8px;border:1px solid #3a3b48;background:#101117;color:#fff}"
            + "button{font-size:16px;padding:10px 16px;border-radius:8px;border:0;background:#4c6ef5;color:#fff}"
            + "button:disabled,#q:disabled{opacity:.45}"
            + "</style></head><body>"
            + "<header>" + escape(title) + "</header>"
            + "<pre id=out></pre>"
            + "<form id=f><input id=q autocomplete=off placeholder='Ввод для программы' disabled>"
            + "<button id=b disabled>Отправить</button></form>"
            + "<script>"
            + "var out=document.getElementById('out'),q=document.getElementById('q'),b=document.getElementById('b');"
            + "function add(t,c){var s=document.createElement('span');if(c)s.className=c;s.textContent=t;out.appendChild(s);out.scrollTop=out.scrollHeight;}"
            + "window.strokaOut=function(t,e){add(t,e?'err':'');};"
            + "window.strokaAsk=function(p){q.disabled=false;b.disabled=false;q.placeholder=p&&p.trim()?p.trim():'Ввод для программы';q.focus();};"
            + "window.strokaDone=function(){q.disabled=true;b.disabled=true;q.placeholder='Программа завершена';add('\\n— Программа завершена —\\n','info');};"
            + "document.getElementById('f').addEventListener('submit',function(ev){ev.preventDefault();if(q.disabled)return;var v=q.value;q.value='';q.disabled=true;b.disabled=true;add(v+'\\n','echo');Stroka.onInput(v);});"
            + "Stroka.onReady();"
            + "</script></body></html>";
    }
}
