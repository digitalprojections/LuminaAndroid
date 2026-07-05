package com.oneimage.android.api

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

private const val DEFAULT_MAX_LONG_EDGE = 1080

data class PreparedImageTransfer(
    val uri: Uri,
    val fileInfo: OneImageFileInfo,
    val width: Int,
    val height: Int
)

suspend fun prepareImageTransfer(
    context: Context,
    sourceUri: Uri,
    prefix: String,
    maxLongEdge: Int = DEFAULT_MAX_LONG_EDGE,
    quality: Int = 90
): PreparedImageTransfer = withContext(Dispatchers.IO) {
    val original = decodeTransferBitmap(context, sourceUri)
    val ratio = min(
        maxLongEdge.toFloat() / original.width.toFloat(),
        maxLongEdge.toFloat() / original.height.toFloat()
    ).coerceAtMost(1f)
    val width = (original.width * ratio).roundToInt().coerceAtLeast(1)
    val height = (original.height * ratio).roundToInt().coerceAtLeast(1)
    val bitmap = if (width != original.width || height != original.height) {
        Bitmap.createScaledBitmap(original, width, height, true)
    } else {
        original
    }

    val file = File(context.cacheDir, "$prefix-${System.currentTimeMillis()}.jpg")
    file.outputStream().use { output ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), output)
    }
    if (bitmap !== original) bitmap.recycle()
    original.recycle()

    PreparedImageTransfer(
        uri = Uri.fromFile(file),
        fileInfo = OneImageFileInfo(
            filename = file.name,
            mimeType = "image/jpeg",
            size = file.length()
        ),
        width = width,
        height = height
    )
}

private fun decodeTransferBitmap(context: Context, uri: Uri): Bitmap {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
    } ?: error("Could not read image.")
}
