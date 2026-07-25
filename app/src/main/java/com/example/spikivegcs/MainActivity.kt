package com.example.spikivegcs

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.spikivegcs.network.NetworkKind
import com.example.spikivegcs.ui.theme.LichtblickBackground
import com.example.spikivegcs.ui.theme.LichtblickDivider
import com.example.spikivegcs.ui.theme.LichtblickError
import com.example.spikivegcs.ui.theme.LichtblickGrey200
import com.example.spikivegcs.ui.theme.LichtblickMenu
import com.example.spikivegcs.ui.theme.LichtblickPaper
import com.example.spikivegcs.ui.theme.LichtblickPrimary
import com.example.spikivegcs.ui.theme.LichtblickSuccess
import com.example.spikivegcs.ui.theme.LichtblickTextPrimary
import com.example.spikivegcs.ui.theme.LichtblickTextSecondary
import com.example.spikivegcs.ui.theme.SpikiveMono
import com.example.spikivegcs.ui.theme.SpikiveGCSTheme

class MainActivity : ComponentActivity() {
    private val hostViewModel: SpikiveHostViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SpikiveGCSTheme {
                val uiState by hostViewModel.uiState.collectAsState()
                Surface(modifier = Modifier.fillMaxSize(), color = LichtblickBackground) {
                    SpikiveHostScreen(
                        uiState = uiState,
                        onNetworkButton = hostViewModel::onNetworkButton,
                        onLeaveWebView = hostViewModel::leaveWebView,
                        onRetryServer = hostViewModel::retryServer,
                    )
                }
            }
        }
    }
}

@Composable
private fun SpikiveHostScreen(
    uiState: HostUiState,
    onNetworkButton: (NetworkKind) -> Unit,
    onLeaveWebView: () -> Unit,
    onRetryServer: () -> Unit,
) {
    when {
        uiState.showWebView -> {
            val selected = checkNotNull(uiState.selected)
            val server = uiState.serverState as ServerState.Ready
            key(selected.networkHandle) {
                GroundStationWebView(url = server.url, onExit = onLeaveWebView)
            }
        }
        uiState.serverState == ServerState.Starting -> CenteredStatus(
            message = "正在通过${uiState.selected?.kind?.label().orEmpty()}进入地面站…",
            showProgress = true,
            secondaryActionLabel = "取消",
            onSecondaryAction = onLeaveWebView,
        )
        uiState.serverState is ServerState.Failed -> CenteredStatus(
            message = uiState.serverState.message,
            actionLabel = "重试",
            onAction = onRetryServer,
            secondaryActionLabel = "返回网络选择",
            onSecondaryAction = onLeaveWebView,
        )
        else -> NetworkSelectionScreen(uiState, onNetworkButton)
    }
}

@Composable
private fun CenteredStatus(
    message: String,
    showProgress: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LichtblickBackground)
            .systemBarsPadding(),
    ) {
        HostAppBar(modifier = Modifier.align(Alignment.TopCenter))
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .border(1.dp, LichtblickDivider, HOST_SHAPE)
                .background(LichtblickPaper, HOST_SHAPE)
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showProgress) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(34.dp)
                        .padding(bottom = 2.dp),
                    color = LichtblickPrimary,
                    strokeWidth = 3.dp,
                )
                Spacer(modifier = Modifier.height(18.dp))
            }
            Text(
                text = if (showProgress) "正在准备工作区" else "无法打开工作区",
                color = LichtblickTextPrimary,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = message,
                color = LichtblickTextSecondary,
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (actionLabel != null && onAction != null) {
                Button(
                    onClick = onAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 22.dp),
                    shape = HOST_SHAPE,
                    colors = primaryButtonColors(),
                ) {
                    Text(actionLabel)
                }
            }
            if (secondaryActionLabel != null && onSecondaryAction != null) {
                OutlinedButton(
                    onClick = onSecondaryAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    shape = HOST_SHAPE,
                    border = BorderStroke(1.dp, LichtblickDivider),
                    colors = outlineButtonColors(),
                ) {
                    Text(secondaryActionLabel)
                }
            }
        }
    }
}

@Composable
private fun HostAppBar(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(LichtblickMenu),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(LichtblickPrimary, HOST_SHAPE),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "S",
                    color = LichtblickBackground,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            Column(modifier = Modifier.padding(start = 10.dp)) {
                Text(
                    text = "SPIKIVE GCS",
                    color = LichtblickTextPrimary,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = "LICHTBLICK ANDROID HOST",
                    color = LichtblickTextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            StatusBadge(label = "LANDSCAPE", color = LichtblickTextSecondary)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(LichtblickDivider),
        )
    }
}

@Composable
private fun StatusBadge(label: String, color: Color) {
    Row(
        modifier = Modifier
            .border(1.dp, color.copy(alpha = 0.55f), HOST_SHAPE)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color, RoundedCornerShape(50)),
        )
        Text(
            text = label,
            color = color,
            modifier = Modifier.padding(start = 6.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun NetworkSelectionScreen(
    uiState: HostUiState,
    onNetworkButton: (NetworkKind) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LichtblickBackground)
            .systemBarsPadding(),
    ) {
        HostAppBar()
        Column(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 980.dp)
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column {
                    Text(
                        text = "选择数据网络",
                        color = LichtblickTextPrimary,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = "先搜索接口地址，确认后再进入 Lichtblick 工作区。",
                        color = LichtblickTextSecondary,
                        modifier = Modifier.padding(top = 3.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                StatusBadge(label = "CELLULAR DISABLED", color = LichtblickPrimary)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                NetworkOptionCard(
                    modifier = Modifier.weight(1f),
                    kind = NetworkKind.USB_ETHERNET,
                    title = "USB 网口",
                    description = "自组网模块 / Android Ethernet",
                    option = uiState.usb,
                    onClick = { onNetworkButton(NetworkKind.USB_ETHERNET) },
                )
                NetworkOptionCard(
                    modifier = Modifier.weight(1f),
                    kind = NetworkKind.WIFI,
                    title = "Wi-Fi",
                    description = "当前连接的无线局域网",
                    option = uiState.wifi,
                    onClick = { onNetworkButton(NetworkKind.WIFI) },
                )
            }
            uiState.message?.let { message ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .border(1.dp, LichtblickPrimary.copy(alpha = 0.55f), HOST_SHAPE)
                        .background(LichtblickPrimary.copy(alpha = 0.08f), HOST_SHAPE)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(LichtblickPrimary, RoundedCornerShape(50)),
                    )
                    Text(
                        text = message,
                        color = LichtblickTextPrimary,
                        modifier = Modifier.padding(start = 9.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun NetworkOptionCard(
    modifier: Modifier = Modifier,
    kind: NetworkKind,
    title: String,
    description: String,
    option: NetworkOptionState,
    onClick: () -> Unit,
) {
    val found = option.phase == SearchPhase.FOUND && option.ipAddress != null
    val statusColor = when (option.phase) {
        SearchPhase.IDLE -> LichtblickTextSecondary
        SearchPhase.NOT_FOUND -> LichtblickError
        SearchPhase.FOUND -> LichtblickSuccess
    }
    Column(
        modifier = modifier
            .heightIn(min = 176.dp)
            .border(
                width = 1.dp,
                color = if (found) LichtblickPrimary else LichtblickDivider,
                shape = HOST_SHAPE,
            )
            .background(LichtblickPaper, HOST_SHAPE)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(LichtblickGrey200, HOST_SHAPE),
                contentAlignment = Alignment.Center,
            ) {
                NetworkGlyph(
                    kind = kind,
                    tint = if (found) LichtblickPrimary else LichtblickTextSecondary,
                    modifier = Modifier.size(25.dp),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 13.dp),
            ) {
                Text(
                    text = title,
                    color = LichtblickTextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = description,
                    color = LichtblickTextSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            StatusBadge(
                label = when (option.phase) {
                    SearchPhase.IDLE -> "NOT SCANNED"
                    SearchPhase.NOT_FOUND -> "NO ADDRESS"
                    SearchPhase.FOUND -> "READY"
                },
                color = statusColor,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "INTERFACE ADDRESS",
                    color = LichtblickTextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    text = option.ipAddress ?: "--",
                    color = if (found) LichtblickTextPrimary else LichtblickTextSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                    fontFamily = SpikiveMono,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            if (found) {
                Button(
                    onClick = onClick,
                    shape = HOST_SHAPE,
                    colors = primaryButtonColors(),
                ) {
                    Text("进入地面站")
                }
            } else {
                OutlinedButton(
                    onClick = onClick,
                    shape = HOST_SHAPE,
                    border = BorderStroke(1.dp, LichtblickDivider),
                    colors = outlineButtonColors(),
                ) {
                    Text("搜索 IP")
                }
            }
        }
    }
}

@Composable
private fun NetworkGlyph(kind: NetworkKind, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = size.minDimension * 0.085f, cap = StrokeCap.Round)
        when (kind) {
            NetworkKind.WIFI -> {
                drawArc(tint, 215f, 110f, false, style = stroke)
                drawArc(
                    color = tint,
                    startAngle = 215f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.22f, size.height * 0.28f),
                    size = Size(size.width * 0.56f, size.height * 0.56f),
                    style = stroke,
                )
                drawCircle(
                    color = tint,
                    radius = size.minDimension * 0.075f,
                    center = Offset(center.x, size.height * 0.78f),
                )
            }
            NetworkKind.USB_ETHERNET -> {
                val top = Offset(center.x, size.height * 0.24f)
                val junction = Offset(center.x, size.height * 0.53f)
                val left = Offset(size.width * 0.25f, size.height * 0.76f)
                val right = Offset(size.width * 0.75f, size.height * 0.76f)
                drawLine(tint, top, junction, strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, junction, left, strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, junction, right, strokeWidth = stroke.width, cap = StrokeCap.Round)
                listOf(top, left, right).forEach { drawCircle(tint, size.minDimension * 0.105f, it) }
            }
        }
    }
}

@Composable
private fun primaryButtonColors() = ButtonDefaults.buttonColors(
    containerColor = LichtblickPrimary,
    contentColor = LichtblickBackground,
)

@Composable
private fun outlineButtonColors() = ButtonDefaults.outlinedButtonColors(
    contentColor = LichtblickTextPrimary,
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun GroundStationWebView(url: String, onExit: () -> Unit) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var pageLoading by remember { mutableStateOf(true) }
    var pageError by remember { mutableStateOf<String?>(null) }
    var renderGeneration by remember { mutableStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    val currentWebView by rememberUpdatedState(webView)

    BackHandler {
        if (canGoBack) {
            currentWebView?.goBack()
        } else {
            onExit()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            currentWebView?.apply {
                stopLoading()
                loadUrl("about:blank")
                clearHistory()
                removeAllViews()
                destroy()
            }
            webView = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        key(renderGeneration) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        setBackgroundColor(android.graphics.Color.BLACK)
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = false
                            allowFileAccess = false
                            allowContentAccess = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            mediaPlaybackRequiresUserGesture = false
                            cacheMode = WebSettings.LOAD_DEFAULT
                            setSupportMultipleWindows(false)
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                                Log.d(
                                    WEB_TAG,
                                    "${consoleMessage.messageLevel()}: ${consoleMessage.message()} " +
                                        "(${consoleMessage.sourceId()}:${consoleMessage.lineNumber()})",
                                )
                                return true
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest,
                            ): Boolean = !isAllowedTopLevelUrl(request.url.toString(), url)

                            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                                pageLoading = true
                                pageError = null
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                pageLoading = false
                                canGoBack = view.canGoBack()
                            }

                            override fun doUpdateVisitedHistory(
                                view: WebView,
                                url: String?,
                                isReload: Boolean,
                            ) {
                                canGoBack = view.canGoBack()
                            }

                            override fun onReceivedError(
                                view: WebView,
                                request: WebResourceRequest,
                                error: WebResourceError,
                            ) {
                                Log.e(
                                    WEB_TAG,
                                    "Web resource failed: ${request.url} ${error.description}",
                                )
                                if (request.isForMainFrame) {
                                    pageLoading = false
                                    pageError = error.description?.toString() ?: "页面加载失败"
                                }
                            }

                            override fun onReceivedHttpError(
                                view: WebView,
                                request: WebResourceRequest,
                                errorResponse: WebResourceResponse,
                            ) {
                                Log.e(
                                    WEB_TAG,
                                    "HTTP ${errorResponse.statusCode} for ${request.url}",
                                )
                                if (request.isForMainFrame) {
                                    pageLoading = false
                                    pageError = "页面加载失败：HTTP ${errorResponse.statusCode}"
                                }
                            }

                            override fun onRenderProcessGone(
                                view: WebView,
                                detail: RenderProcessGoneDetail,
                            ): Boolean {
                                Log.e(WEB_TAG, "WebView renderer exited; crashed=${detail.didCrash()}")
                                view.destroy()
                                webView = null
                                canGoBack = false
                                pageLoading = false
                                pageError = "网页渲染进程已退出，请重新加载"
                                return true
                            }
                        }
                        webView = this
                        loadUrl(url)
                    }
                },
                update = { current ->
                    if (current.url == null || current.url == "about:blank") {
                        current.loadUrl(url)
                    }
                },
            )
        }

        if (pageLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp),
                color = LichtblickPrimary,
                strokeWidth = 3.dp,
            )
        }

        pageError?.let { error ->
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .widthIn(max = 420.dp)
                    .border(1.dp, LichtblickDivider, HOST_SHAPE)
                    .background(LichtblickPaper.copy(alpha = 0.96f), HOST_SHAPE)
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "网页加载失败",
                    color = LichtblickTextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = error,
                    color = LichtblickTextSecondary,
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = {
                        pageError = null
                        pageLoading = true
                        if (webView == null) {
                            renderGeneration += 1
                        } else {
                            webView?.reload()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    shape = HOST_SHAPE,
                    colors = primaryButtonColors(),
                ) {
                    Text("重新加载")
                }
            }
        }
    }
}

private fun NetworkKind.label(): String = when (this) {
    NetworkKind.USB_ETHERNET -> "USB 网口"
    NetworkKind.WIFI -> "Wi-Fi"
}

private fun isAllowedTopLevelUrl(candidate: String, applicationUrl: String): Boolean {
    if (candidate == "about:blank") {
        return true
    }
    val candidateUri = Uri.parse(candidate)
    val applicationUri = Uri.parse(applicationUrl)
    return candidateUri.scheme == applicationUri.scheme &&
        candidateUri.host == applicationUri.host &&
        candidateUri.port == applicationUri.port
}

private const val WEB_TAG = "SpikiveWebView"
private val HOST_SHAPE = RoundedCornerShape(2.dp)
