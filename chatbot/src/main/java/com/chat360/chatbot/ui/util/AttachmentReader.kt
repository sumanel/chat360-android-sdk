package com.chat360.chatbot.ui.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

data class AttachmentPayload(val bytes: ByteArray, val fileName: String, val mimeType: String)

/** Maximum size accepted for an uploaded attachment. */
const val MAX_ATTACHMENT_BYTES = 10_000_000

fun readAttachment(context: Context, uri: Uri): AttachmentPayload? {
    val resolver = context.contentResolver
    val mimeType = resolver.getType(uri) ?: "application/octet-stream"
    var name = "file"
    resolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) name = cursor.getString(nameIndex) ?: name
    }
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    if (bytes.size > MAX_ATTACHMENT_BYTES) return null
    return AttachmentPayload(bytes, name, mimeType)
}

/** Returns a launch function; call it to open the system file/gallery picker. */
@Composable
fun rememberAttachmentPicker(onPicked: (AttachmentPayload) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { readAttachment(context, it)?.let(onPicked) }
    }
    return { launcher.launch("*/*") }
}

/**
 * Offers a camera option alongside the file picker for a FILE_UPLOAD node with
 * `enableCameraInput`, using the platform's own Camera app instead of a custom live-preview UI -
 * this skips reimplementing a preview/flip-camera screen entirely. Returns a launch function;
 * call it to open the system camera app for a single photo.
 */
@Composable
fun rememberCameraCapture(onPicked: (AttachmentPayload) -> Unit): () -> Unit {
    val context = LocalContext.current
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingUri
        pendingUri = null
        if (success && uri != null) {
            readAttachment(context, uri)?.let(onPicked)
        }
    }
    return {
        val file = File(context.cacheDir, "chat360_camera_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.chat360.fileprovider", file)
        pendingUri = uri
        launcher.launch(uri)
    }
}
