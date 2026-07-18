package com.oneimage.android.ui.meshmodel

import android.content.Context
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebResourceResponse
import android.webkit.ConsoleMessage
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.oneimage.android.api.OneImageTask
import com.oneimage.android.api.OneImageTaskResult
import com.oneimage.android.ui.shared.CancelTaskConfirmationDialog
import com.oneimage.android.ui.shared.WorkflowHistoryList
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private val MESH_VIEWER_BACKGROUND_PRESETS = listOf(
    "#050507", "#111827", "#f8fafc", "#e5e7eb", "#0f766e", "#7c2d12"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshModelScreen(
    onBack: () -> Unit,
    onHistory: () -> Unit,
    viewModel: MeshModelViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val clientId = remember {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "device"
        "android-$androidId"
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.selectImage(context, uri)
    }

    val prefs = remember { context.getSharedPreferences("lumina_mesh_prefs", Context.MODE_PRIVATE) }
    var viewerBackground by remember {
        mutableStateOf(prefs.getString("viewer_background", MESH_VIEWER_BACKGROUND_PRESETS.first()) ?: MESH_VIEWER_BACKGROUND_PRESETS.first())
    }

    val result = state.results.firstOrNull()
    val modelUrl = result?.url
    var viewerSource by remember(modelUrl) { mutableStateOf<MeshViewerSource?>(null) }
    var isPreparingViewer by remember(modelUrl) { mutableStateOf(false) }
    var cancelAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(modelUrl) {
        val url = modelUrl
        if (url == null) {
            viewerSource = null
            return@LaunchedEffect
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            viewerSource = MeshViewerSource(renderUrl = url)
        } else if (url.startsWith("file:") || url.startsWith("content:")) {
            isPreparingViewer = true
            viewerSource = withContext(Dispatchers.IO) {
                runCatching { cacheMeshViewerFile(context, Uri.parse(url)) }.getOrNull()
            }
            isPreparingViewer = false
        } else {
            viewerSource = null
        }
    }

    CancelTaskConfirmationDialog(
        visible = cancelAction != null,
        onDismiss = { cancelAction = null },
        onConfirm = {
            val action = cancelAction
            cancelAction = null
            action?.invoke()
        }
    )

    val canGenerate = state.sourceImageUri != null &&
            state.transferImageUri != null &&
            !state.isBusy &&
            state.engineReady &&
            state.hasEnoughCredits

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Game Mesh", fontWeight = FontWeight.Bold)
                        Text("Draft 3D model from one image", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = onHistory) {
                        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("History")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatusStrip(state)

            SourcePanel(
                state = state,
                onPickImage = { imagePicker.launch("image/*") },
                onClear = viewModel::clearSource
            )

            BuildPanel(
                state = state,
                canGenerate = canGenerate,
                onGenerate = { viewModel.generateMesh(context, clientId) },
                onCancel = { cancelAction = { viewModel.cancelCurrentTask(clientId) } }
            )

            StatusPanel(state)

            OutputPanel(
                state = state,
                result = result,
                viewerSource = viewerSource,
                isPreparingViewer = isPreparingViewer,
                viewerBackground = viewerBackground,
                onBackgroundChange = { color ->
                    viewerBackground = color
                    prefs.edit().putString("viewer_background", color).apply()
                },
                onSave = { res -> viewModel.saveResult(context, res) },
                onRestore = { task -> if (task != null) viewModel.restoreTask(context, clientId, task) },
                onReset = viewModel::clearSource
            )
        }
    }
}

@Composable
private fun StatusStrip(state: MeshModelUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        StatusChip(
            label = if (state.engineReady) "ENGINE READY" else "ENGINE SYNC",
            positive = state.engineReady,
            modifier = Modifier.weight(1f)
        )
        val queue = state.queueStatus
        StatusChip(
            label = "${(queue?.totalPending ?: 0) + (queue?.totalProcessing ?: 0)} queued",
            positive = true,
            modifier = Modifier.weight(1f)
        )
        StatusChip(
            label = state.profile?.creditBalanceText ?: "Sign in",
            positive = state.profile != null && state.hasEnoughCredits,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatusChip(label: String, positive: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.height(36.dp),
        shape = RoundedCornerShape(18.dp),
        color = if (positive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(
                label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (positive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun SourcePanel(
    state: MeshModelUiState,
    onPickImage: () -> Unit,
    onClear: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("01 / SOURCE", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Asset Image", fontWeight = FontWeight.Bold)
                }
                if (state.sourceImageUri != null && !state.isBusy) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Default.Close, contentDescription = "Clear source")
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    .clickable(enabled = !state.isBusy) { onPickImage() },
                contentAlignment = Alignment.Center
            ) {
                if (state.sourceImageUri != null) {
                    AsyncImage(
                        model = state.sourceImageUri,
                        contentDescription = "Source",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    if (state.phase == MeshModelPhase.Preparing) {
                        Surface(color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f), modifier = Modifier.fillMaxSize()) {
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                        Surface(modifier = Modifier.size(48.dp), color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Upload Image", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("PNG, JPEG, or WebP", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun BuildPanel(
    state: MeshModelUiState,
    canGenerate: Boolean,
    onGenerate: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("02 / BUILD", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Textured Mesh", fontWeight = FontWeight.Bold)

            Button(
                onClick = onGenerate,
                enabled = canGenerate,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (state.isBusy && state.phase != MeshModelPhase.Restoring) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(state.statusMessage, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    Text("Create Model · ${state.estimatedCredits} credits", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }

            if (state.currentTaskId != null && state.phase == MeshModelPhase.Running) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Abort Mesh")
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${state.estimatedCredits} credits per run", fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(
                    "${state.profile?.creditBalanceText ?: "0"} available",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (state.hasEnoughCredits) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
            if (!state.hasEnoughCredits) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("This account does not have enough credits.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun StatusPanel(state: MeshModelUiState) {
    if (state.phase == MeshModelPhase.Idle && state.error == null && state.saveMessage == null) return
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        if (state.phase == MeshModelPhase.Error) Icons.Default.Warning else Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (state.phase == MeshModelPhase.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(state.statusMessage, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
            state.saveMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun OutputPanel(
    state: MeshModelUiState,
    result: OneImageTaskResult?,
    viewerSource: MeshViewerSource?,
    isPreparingViewer: Boolean,
    viewerBackground: String,
    onBackgroundChange: (String) -> Unit,
    onSave: (OneImageTaskResult) -> Unit,
    onRestore: (OneImageTask?) -> Unit,
    onReset: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("3D Model Output", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (result != null) {
                    Text("ready", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                } else if (state.sourceImageUri != null) {
                    Text("source loaded", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("waiting", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(android.graphics.Color.parseColor(viewerBackground)))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isPreparingViewer -> {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                    viewerSource != null -> {
                        AndroidView(
                            factory = { ctx ->
                                MeshViewerWebView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.allowFileAccess = true
                                    settings.allowContentAccess = true
                                    settings.allowFileAccessFromFileURLs = true
                                    settings.allowUniversalAccessFromFileURLs = true
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldInterceptRequest(
                                            view: WebView?,
                                            request: WebResourceRequest?
                                        ): WebResourceResponse? {
                                            val source = (view as? MeshViewerWebView)?.viewerSource ?: return null
                                            val localFile = source.localFile ?: return null
                                            val requestUri = request?.url ?: return null
                                            if (requestUri.scheme != "https" ||
                                                requestUri.host != MESH_VIEWER_HOST ||
                                                requestUri.path != source.localPath
                                            ) {
                                                return null
                                            }
                                            return runCatching {
                                                WebResourceResponse(
                                                    source.mimeType ?: "application/octet-stream",
                                                    null,
                                                    localFile.inputStream()
                                                )
                                            }.getOrElse { error ->
                                                Log.e("MeshModelWebView", "Could not serve local mesh file", error)
                                                null
                                            }
                                        }

                                        override fun onReceivedError(
                                            view: WebView?,
                                            request: WebResourceRequest?,
                                            error: WebResourceError?
                                        ) {
                                            Log.e(
                                                "MeshModelWebView",
                                                "Load error ${error?.errorCode}: ${error?.description} for ${request?.url}"
                                            )
                                        }
                                    }
                                    webChromeClient = object : WebChromeClient() {
                                        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                            consoleMessage?.let {
                                                Log.d(
                                                    "MeshModelWebView",
                                                    "${it.messageLevel()} ${it.sourceId()}:${it.lineNumber()} ${it.message()}"
                                                )
                                            }
                                            return true
                                        }
                                    }
                                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                }
                            },
                            update = { webView ->
                                webView.viewerSource = viewerSource
                                if (!webView.loadedShell) {
                                    webView.loadedShell = true
                                    val baseUrl = "https://$MESH_VIEWER_HOST/"
                                    webView.loadDataWithBaseURL(baseUrl, MESH_VIEWER_HTML, "text/html", "UTF-8", null)
                                }
                                if (webView.loadedRenderUrl != viewerSource.renderUrl) {
                                    webView.loadedRenderUrl = viewerSource.renderUrl
                                    applyMeshViewerSource(webView, viewerSource.renderUrl)
                                }
                                if (webView.loadedBackground != viewerBackground) {
                                    applyMeshViewerBackground(webView, viewerBackground)
                                }
                                webView.loadedBackground = viewerBackground
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    state.phase == MeshModelPhase.Running || state.phase == MeshModelPhase.Restoring -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator()
                            Text("Building 3D Mesh...", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    result?.url?.startsWith("webrtc://") == true -> {
                        TextButton(onClick = { onRestore(state.currentTask) }) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Restore")
                        }
                    }
                    else -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.ViewInAr, contentDescription = null, size = 44.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Awaiting Asset Image", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Upload a single clear asset image.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (result != null && viewerSource != null) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Palette, contentDescription = null, size = 16.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Background", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.weight(1f))
                        MESH_VIEWER_BACKGROUND_PRESETS.forEach { colorStr ->
                            val color = Color(android.graphics.Color.parseColor(colorStr))
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (viewerBackground.lowercase() == colorStr.lowercase()) 2.dp else 1.dp,
                                        color = if (viewerBackground.lowercase() == colorStr.lowercase()) MaterialTheme.colorScheme.primary else Color.LightGray,
                                        shape = CircleShape
                                    )
                                    .clickable { onBackgroundChange(colorStr) }
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { onSave(result) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save to Downloads")
                        }
                        OutlinedButton(
                            onClick = onReset,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("New Mesh")
                        }
                    }
                }
            }
        }
    }
}

private fun cacheMeshViewerFile(context: Context, uri: Uri): MeshViewerSource? {
    val directory = File(context.cacheDir, "mesh-viewer").apply { mkdirs() }
    val extension = when (context.contentResolver.getType(uri)) {
        "model/gltf-binary" -> ".glb"
        "model/gltf+json" -> ".gltf"
        else -> null
    } ?: uri.lastPathSegment
        ?.substringAfterLast('.', missingDelimiterValue = "")
        ?.takeIf { it.isNotBlank() }
        ?.let { ".$it" }
        ?: ".glb"
    val file = File(directory, "viewer-${System.currentTimeMillis()}$extension")
    val inputStream = when (uri.scheme?.lowercase()) {
        "content" -> context.contentResolver.openInputStream(uri)
        "file" -> uri.path?.let { File(it).inputStream() }
        else -> null
    }
    inputStream?.use { input ->
        file.outputStream().use { output -> input.copyTo(output) }
    } ?: return null
    val mimeType = when (extension.lowercase()) {
        ".glb" -> "model/gltf-binary"
        ".gltf" -> "model/gltf+json"
        ".obj" -> "text/plain"
        ".fbx" -> "application/octet-stream"
        else -> context.contentResolver.getType(uri) ?: "application/octet-stream"
    }
    val localPath = "/oneimage-mesh/${file.name}"
    return MeshViewerSource(
        renderUrl = "https://$MESH_VIEWER_HOST$localPath",
        localFile = file,
        localPath = localPath,
        mimeType = mimeType
    )
}

private data class MeshViewerSource(
    val renderUrl: String,
    val localFile: File? = null,
    val localPath: String? = null,
    val mimeType: String? = null
)

private class MeshViewerWebView(context: Context) : WebView(context) {
    var viewerSource: MeshViewerSource? = null
    var loadedShell: Boolean = false
    var loadedRenderUrl: String? = null
    var loadedBackground: String? = null
}

private const val MESH_VIEWER_HOST = "appassets.androidplatform.net"

private const val MESH_VIEWER_HTML =
    "<!DOCTYPE html><html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
        "<script type=\"module\" src=\"https://ajax.googleapis.com/ajax/libs/model-viewer/3.5.0/model-viewer.min.js\"></script>" +
        "<style>:root{--viewer-bg:#050507}body,html{margin:0;padding:0;width:100%;height:100%;overflow:hidden;background-color:var(--viewer-bg)}model-viewer{width:100%;height:100%;background-color:var(--viewer-bg)}</style>" +
        "</head><body><model-viewer id=\"mesh-viewer\" camera-controls auto-rotate shadow-intensity=\"0.7\" exposure=\"1\" tone-mapping=\"neutral\" environment-image=\"neutral\" interaction-prompt=\"none\"></model-viewer>" +
        "<script type=\"module\">const attach=()=>{const viewer=document.getElementById('mesh-viewer');if(!viewer)return;viewer.addEventListener('load',()=>console.log('Mesh model loaded'));viewer.addEventListener('error',(event)=>{const detail=event?.detail;const sourceError=detail?.sourceError;console.error('Mesh model failed',sourceError?.message||detail?.type||'unknown error');});};if(customElements.get('model-viewer')){attach();}else{customElements.whenDefined('model-viewer').then(attach);}</script>" +
        "</body></html>"

private fun applyMeshViewerSource(webView: WebView, renderUrl: String) {
    webView.evaluateJavascript(
        "(() => { const viewer = document.getElementById('mesh-viewer'); if (viewer) viewer.src = ${JSONObject.quote(renderUrl)}; return true; })();",
        null
    )
}

private fun applyMeshViewerBackground(webView: WebView, viewerBackground: String) {
    val background = JSONObject.quote(viewerBackground)
    webView.evaluateJavascript(
        "(() => { document.documentElement.style.setProperty('--viewer-bg', $background); const viewer = document.getElementById('mesh-viewer'); if (viewer) viewer.style.backgroundColor = $background; document.body.style.backgroundColor = $background; return true; })();",
        null
    )
}

// Extension function to help display custom sized icons.
@Composable
private fun Icon(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    size: androidx.compose.ui.unit.Dp,
    tint: Color = Color.Unspecified
) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = Modifier.size(size),
        tint = tint
    )
}
