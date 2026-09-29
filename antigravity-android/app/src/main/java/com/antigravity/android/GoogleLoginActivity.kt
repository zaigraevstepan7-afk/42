package com.antigravity.android

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import com.antigravity.android.auth.OAuthCallbackServer
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean

class GoogleLoginActivity : ComponentActivity() {
    private var server: OAuthCallbackServer? = null
    private val done = AtomicBoolean(false)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val state = intent.getStringExtra(EXTRA_STATE)
        val url = intent.getStringExtra(EXTRA_URL)
        if (state.isNullOrBlank() || url.isNullOrBlank()) {
            finish()
            return
        }
        server = OAuthCallbackServer(
            expectedState = state,
            onCode = { deliver(it) },
            onError = { fail(it) },
        ).also { it.start() }

        val web = WebView(this)
        setContentView(web)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.userAgentString = MOBILE_UA
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val target = request.url?.toString().orEmpty()
                if (!isCallback(target)) return null
                take(target)
                val html = "<html><body>OK</body></html>".toByteArray()
                return WebResourceResponse("text/html", "utf-8", ByteArrayInputStream(html))
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val target = request.url?.toString().orEmpty()
                if (!isCallback(target)) return false
                take(target)
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                if (url != null && isCallback(url)) take(url)
            }
        }
        web.loadUrl(url)
    }

    override fun onDestroy() {
        server?.stop()
        super.onDestroy()
    }

    private fun take(url: String) {
        val params = OAuthCallbackServer.parseQuery(url.substringAfter("?", ""))
        if (params["state"] != intent.getStringExtra(EXTRA_STATE)) return
        val code = params["code"]
        if (!code.isNullOrBlank()) deliver(code)
        else params["error"]?.let { fail(it) }
    }

    private fun deliver(code: String) {
        if (!done.compareAndSet(false, true)) return
        setResult(Activity.RESULT_OK, intent.putExtra(EXTRA_CODE, code))
        finish()
    }

    private fun fail(message: String) {
        if (!done.compareAndSet(false, true)) return
        setResult(Activity.RESULT_CANCELED, intent.putExtra(EXTRA_ERROR, message))
        finish()
    }

    companion object {
        const val EXTRA_STATE = "state"
        const val EXTRA_URL = "url"
        const val EXTRA_CODE = "code"
        const val EXTRA_ERROR = "error"
        private const val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

        fun isCallback(url: String): Boolean {
            return url.startsWith("http://127.0.0.1:51121/oauth-callback") ||
                url.startsWith("http://localhost:51121/oauth-callback")
        }
    }
}
