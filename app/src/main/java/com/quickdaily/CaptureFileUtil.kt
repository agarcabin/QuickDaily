package com.quickdaily

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object CaptureFileUtil {
    fun newImageFile(context: Context): File =
        File.createTempFile("qd_camera_", ".jpg", context.cacheDir)

    fun newAudioFile(context: Context): File =
        File.createTempFile("qd_recording_", ".m4a", context.cacheDir)

    fun fileUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
