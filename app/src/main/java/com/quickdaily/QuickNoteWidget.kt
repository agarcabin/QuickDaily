package com.quickdaily

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.widget.RemoteViews
import java.io.File

class QuickNoteWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        QuickNoteWidgetUpdatePolicy.launch(
            finishable = WidgetAsyncFinishable { pendingResult.finish() },
        ) {
            appWidgetIds.forEach { widgetId ->
                updateWidget(context.applicationContext, appWidgetManager, widgetId)
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        val pendingResult = goAsync()
        QuickNoteWidgetUpdatePolicy.launch(
            finishable = WidgetAsyncFinishable { pendingResult.finish() },
        ) {
            updateWidget(context.applicationContext, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        private const val CORNER_RADIUS_DP = 16f
        private const val IMAGE_SIZE = 300

        fun updateAllWidgets(context: Context) {
            val appContext = context.applicationContext
            QuickNoteWidgetUpdatePolicy.launch(
                finishable = WidgetAsyncFinishable {},
            ) {
                val appWidgetManager = AppWidgetManager.getInstance(appContext)
                val component = ComponentName(appContext, QuickNoteWidget::class.java)
                val widgetIds = appWidgetManager.getAppWidgetIds(component)
                widgetIds.forEach { id ->
                    updateWidget(appContext, appWidgetManager, id)
                }
            }
        }

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_quicknote)

            val intent = Intent(context, NoteEditActivity::class.java).apply {
                putExtra("floating_source", FloatingNoteSource.WIDGET.name)
            }
            val pendingIntent = PendingIntent.getActivity(
                context, widgetId, intent,
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.quicknote_root, pendingIntent)

            val imageFile = WidgetImageFileResolver.resolve(context)
            if (imageFile != null) {
                if (imageFile.exists()) {
                    try {
                        val bitmap: Bitmap? = BitmapFactory.decodeFile(imageFile.absolutePath)
                        if (bitmap != null) {
                            val square = centerCropSquare(bitmap)
                            val scaled = Bitmap.createScaledBitmap(square, IMAGE_SIZE, IMAGE_SIZE, true)
                            val radiusPx = (CORNER_RADIUS_DP * context.resources.displayMetrics.density).toInt()
                            val rounded = getRoundedBitmap(scaled, radiusPx)
                            views.setImageViewBitmap(R.id.quicknote_image, rounded)
                            views.setViewVisibility(R.id.quicknote_image, android.view.View.VISIBLE)
                            views.setViewVisibility(R.id.quicknote_label, android.view.View.GONE)
                            views.setInt(R.id.quicknote_root, "setBackgroundResource", R.drawable.widget_transparent)
                        } else {
                            resetToDefault(context, views)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("QuickDaily", "小部件图片加载失败: " + e.message)
                        resetToDefault(context, views)
                    }
                } else {
                    resetToDefault(context, views)
                }
            } else {
                resetToDefault(context, views)
            }

            appWidgetManager.updateAppWidget(widgetId, views)
        }

        private fun resetToDefault(context: Context, views: RemoteViews) {
            val colors = WidgetAppearance.colors(context)
            views.setViewVisibility(R.id.quicknote_image, android.view.View.GONE)
            views.setViewVisibility(R.id.quicknote_label, android.view.View.VISIBLE)
            WidgetAppearance.applyRoot(views, R.id.quicknote_root, colors)
            // quicknote_label is an ImageView, not a TextView. Calling setTextColor here
            // aborts the RemoteViews update on some launchers.
            views.setInt(R.id.quicknote_label, "setColorFilter", colors.foreground)
        }

        private fun centerCropSquare(src: Bitmap): Bitmap {
            val size = minOf(src.width, src.height)
            val x = (src.width - size) / 2
            val y = (src.height - size) / 2
            return Bitmap.createBitmap(src, x, y, size, size)
        }

        private fun getRoundedBitmap(bitmap: Bitmap, radiusPx: Int): Bitmap {
            val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val rect = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
            canvas.drawRoundRect(rect, radiusPx.toFloat(), radiusPx.toFloat(), paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(bitmap, 0f, 0f, paint)
            return output
        }
    }
}

internal object QuickNoteWidgetUpdatePolicy {
    fun launch(
        finishable: WidgetAsyncFinishable,
        block: () -> Unit,
    ) = WidgetAsyncWorkRunner.launch(finishable, block)
}
