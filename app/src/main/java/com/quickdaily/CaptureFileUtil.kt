package com.quickdaily

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object CaptureFileUtil {
    private fun captureDirectory(context: Context): File =
        File(context.filesDir, "quickdaily-capture").also { check(it.isDirectory || it.mkdirs()) }

    fun newImageFile(context: Context): File =
        File.createTempFile("qd_camera_", ".jpg", captureDirectory(context))

    fun newAudioFile(context: Context): File =
        File.createTempFile("qd_recording_", ".m4a", captureDirectory(context))

    fun fileUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
