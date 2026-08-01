package com.quickdaily

import android.content.Context
import java.io.File

/** Resolves both the current PNG crop and the legacy JPG crop. */
object WidgetImageFileResolver {
    fun resolve(context: Context): File? {
        val configured = context.getSharedPreferences("QuickDaily", Context.MODE_PRIVATE)
            .getString("widget_image_uri", "")
            .orEmpty()
            .removePrefix("file://")
            .trim()
            .takeIf { it.isNotEmpty() }
            ?.let(::File)
            ?.takeIf(File::isFile)
        return configured
            ?: File(context.filesDir, "widget_image.png").takeIf(File::isFile)
            ?: File(context.filesDir, "widget_image.jpg").takeIf(File::isFile)
    }

    fun clearInternalCrops(context: Context) {
        File(context.filesDir, "widget_image.jpg").delete()
        File(context.filesDir, "widget_image.png").delete()
    }
}
