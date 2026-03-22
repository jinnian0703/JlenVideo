package top.jlen.vod.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.ValueCallback
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.util.concurrent.atomic.AtomicBoolean

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HiddenStreamResolver(
    pageUrl: String,
    onDetected: (String) -> Unit,
    onFailed: (String) -> Unit
) {
    val context = LocalContext.current
    val delivered = remember(pageUrl) { AtomicBoolean(false) }
    val webView = remember(pageUrl) {
        WebView(context).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            alpha = 0f
            configurePlayerWebSettings(settings)
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): android.webkit.WebResourceResponse? {
                    request.url?.toString()?.let { maybeReportMediaUrl(view, it, delivered, onDetected) }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onLoadResource(view: WebView, url: String) {
                    super.onLoadResource(view, url)
                    maybeReportMediaUrl(view, url, delivered, onDetected)
                }

                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    view.postDelayed({ inspectVideoElement(view, delivered, onDetected) }, 1200)
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    super.onPageFinished(view, url)
                    inspectVideoElement(view, delivered, onDetected)
                    view.postDelayed({ inspectVideoElement(view, delivered, onDetected) }, 2500)
                    view.postDelayed({
                        if (!delivered.get()) {
                            onFailed("这条线路还没成功接管，建议先换源试试")
                        }
                    }, 12000)
                }
            }
            loadUrl(pageUrl)
        }
    }

    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.destroy()
        }
    }

    AndroidView(
        factory = { webView },
        modifier = Modifier.size(1.dp)
    )
}

private fun maybeReportMediaUrl(
    webView: WebView,
    rawUrl: String,
    delivered: AtomicBoolean,
    onDetected: (String) -> Unit
) {
    if (!isTakeoverMediaUrl(rawUrl)) return
    if (!delivered.compareAndSet(false, true)) return
    webView.post { onDetected(rawUrl) }
}

private fun inspectVideoElement(
    webView: WebView,
    delivered: AtomicBoolean,
    onDetected: (String) -> Unit
) {
    if (delivered.get()) return
    webView.evaluateJavascript(VIDEO_INSPECT_SCRIPT, ValueCallback { result ->
        val cleaned = result.orEmpty().trim().removeSurrounding("\"").replace("\\u0026", "&")
        if (cleaned.isNotBlank() && isTakeoverMediaUrl(cleaned) && delivered.compareAndSet(false, true)) {
            onDetected(cleaned.replace("\\/", "/"))
        }
    })
}

private fun isTakeoverMediaUrl(url: String): Boolean {
    val lower = url.lowercase()
    return (lower.contains(".m3u8") || lower.contains(".mp4")) &&
        !lower.contains("favicon") &&
        !lower.contains(".css") &&
        !lower.contains(".js")
}

private const val VIDEO_INSPECT_SCRIPT = """
(function() {
  try {
    var video = document.querySelector('video');
    if (video) {
      return video.currentSrc || video.src || '';
    }
    var source = document.querySelector('source');
    if (source) {
      return source.src || '';
    }
    return '';
  } catch (e) {
    return '';
  }
})();
"""
