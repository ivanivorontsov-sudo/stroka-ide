package __PACKAGE__;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.view.View;
import android.view.WindowInsets;
import org.json.JSONObject;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

public class Main extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WebView web = new WebView(this);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        web.addJavascriptInterface(new SystemBridge(this), "StrokaSystem");
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsAlert(WebView view, String url, String message, final JsResult result) {
                new AlertDialog.Builder(Main.this)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                result.confirm();
                            }
                        })
                        .setOnCancelListener(new DialogInterface.OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface d) {
                                result.cancel();
                            }
                        })
                        .show();
                return true;
            }

            @Override
            public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, final JsPromptResult result) {
                final EditText input = new EditText(Main.this);
                input.setText(defaultValue == null ? "" : defaultValue);
                FrameLayout box = new FrameLayout(Main.this);
                int pad = Math.round(20 * getResources().getDisplayMetrics().density);
                box.setPadding(pad, pad / 2, pad, 0);
                box.addView(input);
                new AlertDialog.Builder(Main.this)
                        .setTitle(message == null || message.length() == 0 ? "Ввод" : message)
                        .setView(box)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                result.confirm(input.getText().toString());
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                result.cancel();
                            }
                        })
                        .setOnCancelListener(new DialogInterface.OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface d) {
                                result.cancel();
                            }
                        })
                        .show();
                return true;
            }
        });
        web.loadUrl("file:///android_asset/www/index.html");

        // Android 15 enforces edge-to-edge for targetSdk 35. Keep generated
        // app content below the status bar and above the navigation area.
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
    }

    /** Deliberately small, explicit Android API surface for programs written in Ponyatny. */
    public static final class SystemBridge {
        private final Activity activity;
        private final Handler main = new Handler(Looper.getMainLooper());

        public SystemBridge(Activity activity) { this.activity = activity; }

        @JavascriptInterface
        public void toast(String message) {
            main.post(() -> Toast.makeText(activity, message == null ? "" : message, Toast.LENGTH_SHORT).show());
        }

        @JavascriptInterface
        public boolean vibrate(long durationMs) {
            final long duration = Math.max(1L, Math.min(1000L, durationMs));
            main.post(() -> {
                try {
                    Vibrator vibrator = (Vibrator) activity.getSystemService(Activity.VIBRATOR_SERVICE);
                    if (vibrator != null && vibrator.hasVibrator())
                        vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
                } catch (Exception ignored) { }
            });
            return true;
        }

        @JavascriptInterface
        public boolean openUrl(String value) {
            try {
                Uri uri = Uri.parse(value == null ? "" : value.trim());
                String scheme = uri.getScheme();
                if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) return false;
                main.post(() -> {
                    try { activity.startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
                    catch (Exception e) { toast("Не удалось открыть ссылку"); }
                });
                return true;
            } catch (Exception e) { return false; }
        }

        @JavascriptInterface
        public boolean copyText(String value) {
            main.post(() -> {
                try {
                    ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Activity.CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(ClipData.newPlainText("Строка", value == null ? "" : value));
                } catch (Exception ignored) { }
            });
            return true;
        }

        @JavascriptInterface
        public String clipboardText() {
            FutureTask<String> task = new FutureTask<>(() -> {
                ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Activity.CLIPBOARD_SERVICE);
                if (clipboard == null || clipboard.getPrimaryClip() == null || clipboard.getPrimaryClip().getItemCount() == 0) return "";
                CharSequence text = clipboard.getPrimaryClip().getItemAt(0).coerceToText(activity);
                return text == null ? "" : text.toString();
            });
            main.post(task);
            try { return task.get(2, TimeUnit.SECONDS); }
            catch (Exception e) { return ""; }
        }

        @JavascriptInterface
        public String deviceInfo() {
            try {
                return new JSONObject()
                        .put("manufacturer", Build.MANUFACTURER)
                        .put("model", Build.MODEL)
                        .put("android", Build.VERSION.RELEASE)
                        .put("sdk", Build.VERSION.SDK_INT)
                        .toString();
            } catch (Exception e) { return "{}"; }
        }

        @JavascriptInterface
        public int batteryPercent() {
            try {
                BatteryManager manager = activity.getSystemService(BatteryManager.class);
                return manager == null ? -1 : manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            } catch (Exception e) { return -1; }
        }

        @JavascriptInterface
        public boolean networkAvailable() {
            try {
                ConnectivityManager manager = (ConnectivityManager) activity.getSystemService(Activity.CONNECTIVITY_SERVICE);
                if (manager == null || manager.getActiveNetwork() == null) return false;
                NetworkCapabilities caps = manager.getNetworkCapabilities(manager.getActiveNetwork());
                return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            } catch (Exception e) { return false; }
        }

        @JavascriptInterface
        public boolean openSettings() {
            main.post(() -> {
                try { activity.startActivity(new Intent(Settings.ACTION_SETTINGS)); }
                catch (Exception ignored) { }
            });
            return true;
        }
    }
}
