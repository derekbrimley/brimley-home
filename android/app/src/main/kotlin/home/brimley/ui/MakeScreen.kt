package home.brimley.ui

import android.annotation.SuppressLint
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import home.brimley.model.Make
import home.brimley.ui.theme.Ink
import home.brimley.ui.theme.Paper
import java.net.URI

// A make with a link (a Scratch game, a story on a web page), opened in kiosk.
// No address bar, no new windows, no downloads, and it can't leave the make's site.
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MakeScreen(make: Make, onBack: () -> Unit) {
    val link = make.link.orEmpty()
    var failed by remember(link) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Paper)) {
        Row(
            Modifier.fillMaxWidth().height(110.dp).background(Ink).padding(horizontal = 30.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            InkButton("← Back to the fridge", onClick = onBack, filled = false, onPaper = false)
            Text(make.title, style = MaterialTheme.typography.headlineLarge, color = Paper, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        Box(Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.setSupportMultipleWindows(false)
                        setDownloadListener { _, _, _, _, _ -> }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                                !sameHost(link, request.url.toString())

                            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                if (request.isForMainFrame) failed = true
                            }
                        }
                        loadUrl(link)
                    }
                },
                onRelease = { it.stopLoading(); it.destroy() },
                modifier = Modifier.fillMaxSize(),
            )
            if (failed) {
                Column(
                    Modifier.fillMaxSize().background(Paper).padding(40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    FridgeIllustration(Modifier.size(160.dp))
                    Spacer(Modifier.height(18.dp))
                    Text("This one didn't open. Ask a grown-up to check the link.", style = MaterialTheme.typography.titleMedium, color = Ink)
                }
            }
        }
    }
}

// Top-level navigations may stay on the make's own host (http or https). Everything
// else, other sites, subdomains, intent: and javascript: links, is swallowed.
fun sameHost(base: String, url: String): Boolean {
    val a = runCatching { URI(base) }.getOrNull() ?: return false
    val b = runCatching { URI(url) }.getOrNull() ?: return false
    if (b.scheme?.lowercase() !in setOf("http", "https")) return false
    val host = a.host ?: return false
    return host.equals(b.host, ignoreCase = true)
}
