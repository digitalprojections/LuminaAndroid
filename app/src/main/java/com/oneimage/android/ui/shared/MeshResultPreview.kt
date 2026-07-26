package com.oneimage.android.ui.shared

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import com.oneimage.android.api.OneImageTaskResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Locale

private const val MAX_ANDROID_GLB_PREVIEW_BYTES = 96L * 1024L * 1024L
private const val MESH_VIEWER_HOST = "appassets.androidplatform.net"

@Composable
fun MeshResultPreview(
    result: OneImageTaskResult,
    modifier: Modifier = Modifier,
    background: String = "#050507",
    onRestore: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val canPreviewResult = canPreviewMeshResult(result)
    val resultNeedsRestore = result.url.startsWith("webrtc://")
    val resultTooLargeToPreview = isOversizedMeshPreviewResult(result)
    var viewerSource by remember(result.url) { mutableStateOf<MeshViewerSource?>(null) }
    var isPreparingViewer by remember(result.url) { mutableStateOf(false) }

    LaunchedEffect(result.url, canPreviewResult) {
        val url = result.url
        if (!canPreviewResult) {
            viewerSource = null
            return@LaunchedEffect
        }

        when {
            url.startsWith("http://") || url.startsWith("https://") -> {
                viewerSource = MeshViewerSource(renderUrl = url)
            }

            url.startsWith("file:") || url.startsWith("content:") -> {
                isPreparingViewer = true
                viewerSource = withContext(Dispatchers.IO) {
                    runCatching { cacheMeshViewerFile(context, url.toUri()) }.getOrNull()
                }
                isPreparingViewer = false
            }

            else -> {
                viewerSource = null
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(android.graphics.Color.parseColor(background)))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        when {
            isPreparingViewer -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)

            viewerSource != null -> {
                MeshViewerWebView(
                    viewerSource = viewerSource,
                    background = background,
                    modifier = Modifier.fillMaxSize()
                )
            }

            resultNeedsRestore -> {
                MeshPreviewPlaceholder(
                    title = "Model file ready",
                    subtitle = result.filename.ifBlank { "Restore result to preview." },
                    actionLabel = if (onRestore == null) null else "Restore",
                    onAction = onRestore
                )
            }

            resultTooLargeToPreview -> {
                MeshPreviewPlaceholder(
                    title = "Large model file ready",
                    subtitle = "${formatBytes(result.size)} GLB. Save and open in a desktop 3D tool."
                )
            }

            else -> {
                MeshPreviewPlaceholder(
                    title = "Model file ready",
                    subtitle = result.filename.ifBlank { "This result cannot be previewed on-device." }
                )
            }
        }
    }
}

fun isMeshModelResult(result: OneImageTaskResult): Boolean =
    meshResultExtension(result) in setOf("glb", "gltf")

@Composable
private fun MeshViewerWebView(
    viewerSource: MeshViewerSource?,
    background: String,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { ctx ->
            MeshPreviewWebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val source = (view as? MeshPreviewWebView)?.viewerSource ?: return null
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
                            Log.e("MeshPreviewWebView", "Could not serve local mesh file", error)
                            null
                        }
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        Log.e(
                            "MeshPreviewWebView",
                            "Load error ${error?.errorCode}: ${error?.description} for ${request?.url}"
                        )
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        val webView = view as? MeshPreviewWebView ?: return
                        webView.shellReady = true
                        webView.viewerSource?.let { source ->
                            applyMeshViewerSource(webView, source.renderUrl)
                            webView.loadedRenderUrl = source.renderUrl
                        }
                        webView.loadedBackground?.let { loadedBackground ->
                            applyMeshViewerBackground(webView, loadedBackground)
                        }
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        consoleMessage?.let {
                            Log.d(
                                "MeshPreviewWebView",
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
                webView.shellReady = false
                val baseUrl = "https://$MESH_VIEWER_HOST/"
                webView.loadDataWithBaseURL(baseUrl, MESH_VIEWER_HTML, "text/html", "UTF-8", null)
            }
            val source = viewerSource
            if (webView.shellReady && source != null && webView.loadedRenderUrl != source.renderUrl) {
                webView.loadedRenderUrl = source.renderUrl
                applyMeshViewerSource(webView, source.renderUrl)
            }
            if (webView.shellReady && webView.loadedBackground != background) {
                applyMeshViewerBackground(webView, background)
            }
            webView.loadedBackground = background
        },
        modifier = modifier
    )
}

@Composable
private fun MeshPreviewPlaceholder(
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Default.ViewInAr, contentDescription = null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(
            subtitle,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(actionLabel)
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
    val file = when (uri.scheme?.lowercase(Locale.US)) {
        "file" -> uri.path?.let { File(it).takeIf(File::isFile) }
        "content" -> {
            val cachedFile = File(directory, "viewer-${System.currentTimeMillis()}$extension")
            context.contentResolver.openInputStream(uri)?.use { input ->
                cachedFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            cachedFile
        }
        else -> null
    } ?: return null
    val mimeType = when (extension.lowercase(Locale.US)) {
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

private fun canPreviewMeshResult(result: OneImageTaskResult): Boolean {
    if (isOversizedMeshPreviewResult(result)) return false
    return isMeshModelResult(result)
}

private fun isOversizedMeshPreviewResult(result: OneImageTaskResult): Boolean {
    val extension = meshResultExtension(result)
    return extension == "glb" && result.size > MAX_ANDROID_GLB_PREVIEW_BYTES
}

private fun meshResultExtension(result: OneImageTaskResult): String =
    listOf(result.filename, result.url)
        .asSequence()
        .map { value ->
            value
                .substringBefore('?')
                .substringBefore('#')
                .substringAfterLast('/')
                .substringAfterLast('\\')
                .substringAfterLast('.', "")
                .lowercase(Locale.US)
        }
        .firstOrNull { it.isNotBlank() }
        .orEmpty()

private fun formatBytes(size: Long): String {
    if (size <= 0L) return "Large"
    val mib = size.toDouble() / (1024.0 * 1024.0)
    return "${String.format(Locale.US, "%.1f", mib)} MB"
}

private data class MeshViewerSource(
    val renderUrl: String,
    val localFile: File? = null,
    val localPath: String? = null,
    val mimeType: String? = null
)

private class MeshPreviewWebView(context: Context) : WebView(context) {
    var viewerSource: MeshViewerSource? = null
    var loadedShell: Boolean = false
    var shellReady: Boolean = false
    var loadedRenderUrl: String? = null
    var loadedBackground: String? = null
}

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
