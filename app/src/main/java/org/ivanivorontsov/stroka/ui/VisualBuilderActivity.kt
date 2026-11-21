package org.ivanivorontsov.stroka.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient\nimport android.webkit.WebChromeClient
import androidx.appcompat.app.AppCompatActivity
import java.net.URLEncoder

class VisualBuilderActivity : AppCompatActivity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.allowFileAccess = false\n        web.settings.allowContentAccess = false
        web.settings.allowContentAccess = false
        web.webViewClient = WebViewClient()\n        web.webChromeClient = WebChromeClient()
        web.addJavascriptInterface(Bridge(), "StrokaBuilder")
        setContentView(web)
        val source = URLEncoder.encode(intent.getStringExtra("source").orEmpty(), "UTF-8").replace("+", "%20")
        web.loadDataWithBaseURL("https://stroka.local/", assets.open("visual_builder.html").bufferedReader().use { it.readText() }.replace("const seed=decodeURIComponent(location.hash.slice(1));", "const seed=decodeURIComponent(\\"$source\\");"), "text/html", "UTF-8", null)
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