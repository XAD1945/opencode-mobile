package com.opencode.mobile

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.*
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

class WebViewActivity : ComponentActivity() {

    private var webView: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val serverUrl = intent.getStringExtra("server_url") ?: "http://localhost:3000"

        setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            ) {
                WebViewScreen(
                    serverUrl = serverUrl,
                    onOpenSettings = {
                        startActivity(Intent(this@WebViewActivity, SettingsActivity::class.java))
                    },
                    onShareText = { text ->
                        shareText(text)
                    }
                )
            }
        }
    }

    private fun shareText(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, "Code from OpenCode")
        }
        startActivity(Intent.createChooser(intent, "Share code"))
    }

    @Deprecated("Use OnBackPressedCallback instead")
    override fun onBackPressed() {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        webView?.destroy()
        super.onDestroy()
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(
    serverUrl: String,
    onOpenSettings: () -> Unit,
    onShareText: (String) -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var showError by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val handler = remember { Handler(Looper.getMainLooper()) }
    var retryCount by remember { mutableIntStateOf(0) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewRef = this

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = true
                        allowContentAccess = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        builtInZoomControls = true
                        displayZoomControls = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        cacheMode = WebSettings.LOAD_DEFAULT
                        mediaPlaybackRequiresUserGesture = false
                        userAgentString = "${userAgentString} OpenCodeMobile/1.0"
                    }

                    addJavascriptInterface(MobileBridge(ctx), "OpenCodeMobile")

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                            retryCount = 0
                            injectMobileOverlay(view)
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            if (request?.isForMainFrame == true) {
                                isLoading = false
                                if (retryCount < 10) {
                                    retryCount++
                                    handler.postDelayed({
                                        showError = false
                                        isLoading = true
                                        view?.reload()
                                    }, 2000)
                                } else {
                                    showError = true
                                }
                            }
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            isLoading = newProgress < 100
                        }
                    }

                    loadUrl(serverUrl)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Connecting to OpenCode...")
                    if (retryCount > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Attempt $retryCount...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }

        if (showError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Cannot connect to OpenCode",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Make sure OpenCode is running in Termux.\n\nopencode web --port 3000 --hostname 127.0.0.1",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    Button(onClick = {
                        showError = false
                        isLoading = true
                        retryCount = 0
                        webViewRef?.reload()
                    }) {
                        Text("Retry")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(onClick = onOpenSettings) {
                        Text("Settings")
                    }
                }
            }
        }

        FloatingActionBar(
            visible = showControls && !showError && !isLoading,
            onNewSession = {
                webViewRef?.evaluateJavascript(
                    "window.location.hash = '#/'", null
                )
            },
            onSettings = onOpenSettings,
            onTermux = {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setClassName("com.termux", "com.termux.app.TermuxActivity")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    webViewRef?.context?.startActivity(intent)
                } catch (e: Exception) {
                    // Termux not installed
                }
            },
            onRefresh = {
                retryCount = 0
                webViewRef?.reload()
            },
            onBack = {
                webViewRef?.goBack()
            },
            canGoBack = webViewRef?.canGoBack() == true
        )
    }
}

private val TextAlign = androidx.compose.ui.text.style.TextAlign

@Composable
fun FloatingActionBar(
    visible: Boolean,
    onNewSession: () -> Unit,
    onSettings: () -> Unit,
    onTermux: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    canGoBack: Boolean
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent()
                    }
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.End
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = onTermux,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(Icons.Default.Build, contentDescription = "Termux")
                }
                SmallFloatingActionButton(
                    onClick = onRefresh,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
                SmallFloatingActionButton(
                    onClick = onNewSession,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Session")
                }
                SmallFloatingActionButton(
                    onClick = onSettings,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
                if (canGoBack) {
                    SmallFloatingActionButton(
                        onClick = onBack,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            }
        }
    }
}

class MobileBridge(private val context: Context) {

    @JavascriptInterface
    fun showToast(message: String) {
        android.os.Handler(context.mainLooper).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    @JavascriptInterface
    fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("OpenCode", text)
        clipboard.setPrimaryClip(clip)
    }

    @JavascriptInterface
    fun shareText(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(Intent.createChooser(intent, "Share"))
    }

    @JavascriptInterface
    fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    @JavascriptInterface
    fun vibrate(durationMs: Int) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        @Suppress("DEPRECATION")
        vibrator.vibrate(android.os.VibrationEffect.createOneShot(
            durationMs.toLong(),
            android.os.VibrationEffect.DEFAULT_AMPLITUDE
        ))
    }
}

private fun injectMobileOverlay(webView: WebView?) {
    val css = """
        (function() {
            var style = document.createElement('style');
            style.textContent = `
                body { 
                    -webkit-touch-callout: none;
                    -webkit-user-select: none;
                    user-select: none;
                    padding-bottom: 80px !important;
                }
                input, textarea, [contenteditable] {
                    -webkit-user-select: text !important;
                    user-select: text !important;
                }
                ::-webkit-scrollbar { width: 4px; }
                ::-webkit-scrollbar-track { background: transparent; }
                ::-webkit-scrollbar-thumb { 
                    background: rgba(128,128,128,0.3); 
                    border-radius: 2px; 
                }
            `;
            document.head.appendChild(style);
        })();
    """.trimIndent()
    webView?.evaluateJavascript(css, null)
}
