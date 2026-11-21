package org.ivanivorontsov.stroka.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import java.net.URLEncoder

class VisualBuilderActivity : AppCompatActivity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.allowFileAccess = false
        web.settings.allowContentAccess = false
        web.webViewClient = WebViewClient()
        web.addJavascriptInterface(Bridge(), "StrokaBuilder")
        setContentView(web)
        val source = URLEncoder.encode(intent.getStringExtra("source").orEmpty(), "UTF-8").replace("+", "%20")
        web.loadUrl("file:///android_asset/visual_builder.html#$source")
    }
    private inner class Bridge {
        @JavascriptInterface fun save(html: String) {
            runOnUiThread {
                setResult(Activity.RESULT_OK, intent.putExtra("html", html))
                finish()
            }
        }
    }
}