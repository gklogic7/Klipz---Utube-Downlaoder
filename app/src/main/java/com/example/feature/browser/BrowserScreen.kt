package com.example.feature.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.common.UrlValidator
import com.example.domain.models.MediaFormat
import com.example.feature.components.PlatformIconBadge
import com.example.ui.theme.KlipzMonoTextStyle
import java.io.ByteArrayInputStream

private val AD_HOST_BLOCKLIST = setOf(
    "doubleclick.net",
    "googlesyndication.com",
    "googleadservices.com",
    "adservice.google.com",
    "pagead2.googlesyndication.com",
    "taboola.com",
    "outbrain.com",
    "adnxs.com",
    "ads.pubmatic.com",
    "popads.net"
)

private const val MEDIA_SNIFFER_JS = """
(function() {
  try {
    var nodes = document.querySelectorAll('video, audio, source');
    nodes.forEach(function(n) {
      var src = n.currentSrc || n.src;
      if (src && src.indexOf('http') === 0) {
        KlipzMediaBridge.onMediaFound(src, n.type || '');
      }
    });
  } catch (e) {}
})();
"""

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    onAnalyzeCurrentPage: (url: String, detectedStreams: List<MediaFormat>, titleHint: String) -> Unit,
    onNavigateHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var showBrowserOptions by remember { mutableStateOf(false) }

    BackHandler(enabled = state.canGoBack) {
        webViewRef?.goBack()
    }

    LaunchedEffect(state.pendingLoadTrigger) {
        if (state.pendingLoadTrigger > 0L) {
            webViewRef?.loadUrl(state.currentUrl)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Monochrome Top Address Bar (matching Screenshot 3 structure)
        Surface(
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (state.canGoBack) webViewRef?.goBack() else onNavigateHome()
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("browser_back_or_home_button")
                    ) {
                        Icon(
                            imageVector = if (state.canGoBack) Icons.AutoMirrored.Filled.ArrowBack else Icons.Filled.Home,
                            contentDescription = if (state.canGoBack) "Back" else "Home",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Rounded Monochrome Address Pill
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PlatformIconBadge(platform = state.platform, size = 22.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            BasicTextField(
                                value = state.addressInput,
                                onValueChange = viewModel::onAddressInputChanged,
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                keyboardActions = KeyboardActions(onGo = { viewModel.loadUrl(state.addressInput) }),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("browser_address_input")
                            )
                            IconButton(
                                onClick = {
                                    if (state.isLoading) webViewRef?.stopLoading() else webViewRef?.reload()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (state.isLoading) Icons.Filled.Close else Icons.Filled.Refresh,
                                    contentDescription = if (state.isLoading) "Stop loading" else "Refresh page",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = viewModel::toggleBookmark,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("browser_bookmark_button")
                    ) {
                        Icon(
                            imageVector = if (state.isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = "Toggle bookmark",
                            tint = if (state.isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showBrowserOptions = true },
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("browser_options_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Tune,
                                contentDescription = "Browser controls",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        DropdownMenu(
                            expanded = showBrowserOptions,
                            onDismissRequest = { showBrowserOptions = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Analyze Current URL") },
                                onClick = {
                                    showBrowserOptions = false
                                    val liveUrl = webViewRef?.url?.takeIf { UrlValidator.isValidHttpUrl(it) } ?: state.currentUrl
                                    val liveTitle = webViewRef?.title?.takeIf { it.isNotBlank() } ?: state.pageTitle
                                    onAnalyzeCurrentPage(liveUrl, state.detectedStreams, liveTitle)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Ad-Host Blocker: ${if (state.preferences.enableAdBlocker) "ON" else "OFF"}") },
                                onClick = {
                                    viewModel.toggleAdBlocker(!state.preferences.enableAdBlocker)
                                    showBrowserOptions = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Block Popups: ${if (state.preferences.blockPopupAds) "ON" else "OFF"}") },
                                onClick = {
                                    viewModel.togglePopupBlocker(!state.preferences.blockPopupAds)
                                    showBrowserOptions = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Video Grabber FAB: ${if (state.preferences.showVideoGrabber) "ON" else "OFF"}") },
                                onClick = {
                                    viewModel.toggleGrabber(!state.preferences.showVideoGrabber)
                                    showBrowserOptions = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Open Homepage") },
                                onClick = {
                                    showBrowserOptions = false
                                    viewModel.loadUrl(state.preferences.browserHomepage)
                                }
                            )
                        }
                    }
                }

                if (state.isLoading) {
                    LinearProgressIndicator(
                        progress = { (state.loadProgress / 100f).coerceIn(0.05f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }

        // Embedded WebView + Floating Media Grabber Button
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        settings.loadsImagesAutomatically = state.preferences.showWebImages
                        settings.javaScriptCanOpenWindowsAutomatically = !state.preferences.blockPopupAds
                        settings.setSupportMultipleWindows(false)

                        addJavascriptInterface(
                            object {
                                @JavascriptInterface
                                fun onMediaFound(url: String, mime: String) {
                                    viewModel.onMediaResourceDetected(url, mime)
                                }
                            },
                            "KlipzMediaBridge"
                        )

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                url?.let(viewModel::onPageStarted)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                if (url != null) {
                                    viewModel.onPageFinished(
                                        url = url,
                                        title = view?.title,
                                        canGoBack = view?.canGoBack() ?: false,
                                        canGoForward = view?.canGoForward() ?: false
                                    )
                                }
                                view?.evaluateJavascript(MEDIA_SNIFFER_JS, null)
                            }

                            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                super.doUpdateVisitedHistory(view, url, isReload)
                                if (url != null) {
                                    viewModel.onSpaUrlChanged(
                                        url = url,
                                        title = view?.title,
                                        canGoBack = view?.canGoBack() ?: false,
                                        canGoForward = view?.canGoForward() ?: false
                                    )
                                    view?.evaluateJavascript(MEDIA_SNIFFER_JS, null)
                                }
                            }

                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val reqUrl = request?.url?.toString() ?: return null
                                val host = request.url?.host?.lowercase().orEmpty()

                                if (state.preferences.enableAdBlocker &&
                                    AD_HOST_BLOCKLIST.any { adHost -> host == adHost || host.endsWith(".$adHost") }
                                ) {
                                    return WebResourceResponse(
                                        "text/plain",
                                        "utf-8",
                                        ByteArrayInputStream(ByteArray(0))
                                    )
                                }

                                if (UrlValidator.looksLikeDirectMediaUrl(reqUrl)) {
                                    viewModel.onMediaResourceDetected(reqUrl)
                                }
                                return super.shouldInterceptRequest(view, request)
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                viewModel.onProgressChanged(
                                    progress = newProgress,
                                    canGoBack = view?.canGoBack() ?: false,
                                    canGoForward = view?.canGoForward() ?: false
                                )
                            }
                        }

                        loadUrl(state.currentUrl)
                        webViewRef = this
                    }
                },
                update = { wv ->
                    wv.settings.loadsImagesAutomatically = state.preferences.showWebImages
                    wv.settings.javaScriptCanOpenWindowsAutomatically = !state.preferences.blockPopupAds
                }
            )

            // Floating Media Grabber FAB (inspired by Screenshot 3 bottom-right grabber)
            if (state.preferences.showVideoGrabber) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    BadgedBox(
                        badge = {
                            if (state.detectedStreams.isNotEmpty()) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ) {
                                    Text(
                                        text = state.detectedStreams.size.toString(),
                                        style = KlipzMonoTextStyle
                                    )
                                }
                            }
                        }
                    ) {
                        FloatingActionButton(
                            onClick = {
                                webViewRef?.evaluateJavascript(MEDIA_SNIFFER_JS, null)
                                val liveUrl = webViewRef?.url?.takeIf { UrlValidator.isValidHttpUrl(it) } ?: state.currentUrl
                                val liveTitle = webViewRef?.title?.takeIf { it.isNotBlank() } ?: state.pageTitle
                                onAnalyzeCurrentPage(
                                    liveUrl,
                                    state.detectedStreams,
                                    liveTitle
                                )
                            },
                            shape = CircleShape,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                            modifier = Modifier.testTag("browser_grab_fab")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Download,
                                contentDescription = "Analyze & download media on page"
                            )
                        }
                    }
                }
            }
        }
    }
}
