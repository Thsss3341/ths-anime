package eu.kanade.tachiyomi.animeextension.zh.cycity

import android.annotation.SuppressLint
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Reads the login the website saved in the app's WebView (the "Open in WebView" screen shares its
 * storage with every other WebView in the app). The website keeps its session as JSON in
 * localStorage, or in sessionStorage when "remember me" was off.
 *
 * Blocking; never call it on the main thread.
 */
class WebViewSession(private val url: String, private val userAgent: String) {
    private val handler = Handler(Looper.getMainLooper())

    @SuppressLint("SetJavaScriptEnabled")
    fun readToken(): String? {
        val done = CountDownLatch(1)
        var stored: String? = null
        var webView: WebView? = null

        handler.post {
            val view = WebView(Injekt.get<Application>())
            webView = view
            with(view.settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                userAgentString = userAgent
            }
            view.webViewClient = object : WebViewClient() {
                private var read = false
                override fun onPageFinished(view: WebView, url: String) {
                    if (read) return
                    read = true
                    view.evaluateJavascript(READ_SESSION) { result ->
                        stored = runCatching { Json.decodeFromString<String?>(result) }.getOrNull()
                        done.countDown()
                    }
                }
            }
            view.loadUrl(url)
        }

        done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        handler.post {
            webView?.run {
                stopLoading()
                destroy()
            }
        }
        val session = stored ?: return null
        return runCatching {
            Json.decodeFromString<JsonObject>(session)["token"]?.jsonPrimitive?.contentOrNull?.trim()
        }.getOrNull()?.ifEmpty { null }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 20L

        // The keys the website uses for its session, newest first.
        const val READ_SESSION = """(function () {
            var keys = ["cycweb:auth:v2", "cycweb:auth:v1"];
            var stores = [window.localStorage, window.sessionStorage];
            for (var s = 0; s < stores.length; s++) {
                for (var k = 0; k < keys.length; k++) {
                    try {
                        var value = stores[s] && stores[s].getItem(keys[k]);
                        if (value) return value;
                    } catch (e) {}
                }
            }
            return null;
        })()"""
    }
}
