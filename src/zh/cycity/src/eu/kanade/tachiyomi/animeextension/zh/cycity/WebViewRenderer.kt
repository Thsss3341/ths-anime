package eu.kanade.tachiyomi.animeextension.zh.cycity

import android.annotation.SuppressLint
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Loads a page in an off-screen WebView and returns its HTML once [isReady] says the page's
 * scripts have built it. Used when the site answers with a small loader page that only fills
 * itself in with JavaScript. Blocking; never call it on the main thread.
 */
class WebViewRenderer(private val userAgent: String) {
    private val handler = Handler(Looper.getMainLooper())

    @SuppressLint("SetJavaScriptEnabled")
    fun render(url: String, headers: Map<String, String>, isReady: (String) -> Boolean): String? {
        val done = CountDownLatch(1)
        var result: String? = null
        var webView: WebView? = null
        val deadline = System.currentTimeMillis() + TIMEOUT_MS

        fun poll(view: WebView) {
            view.evaluateJavascript(READ_HTML) { encoded ->
                val html = runCatching { Json.decodeFromString<String>(encoded) }.getOrNull()
                when {
                    html != null && isReady(html) -> {
                        result = html
                        done.countDown()
                    }
                    System.currentTimeMillis() > deadline -> {
                        result = html
                        done.countDown()
                    }
                    else -> handler.postDelayed({ poll(view) }, POLL_INTERVAL_MS)
                }
            }
        }

        handler.post {
            val view = WebView(Injekt.get<Application>())
            webView = view
            with(view.settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                userAgentString = userAgent
            }
            view.webViewClient = object : WebViewClient() {
                private var polling = false
                override fun onPageFinished(view: WebView, url: String) {
                    if (!polling) {
                        polling = true
                        poll(view)
                    }
                }
            }
            view.loadUrl(url, headers)
        }

        done.await(TIMEOUT_MS + 2_000, TimeUnit.MILLISECONDS)
        handler.post {
            webView?.run {
                stopLoading()
                destroy()
            }
        }
        return result
    }

    private companion object {
        const val TIMEOUT_MS = 20_000L
        const val POLL_INTERVAL_MS = 500L
        const val READ_HTML = "document.documentElement.outerHTML"
    }
}
