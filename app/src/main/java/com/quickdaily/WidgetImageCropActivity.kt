package com.quickdaily

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import java.io.File
import java.io.FileOutputStream

/** OEM-independent square cropper used for the Quick Note widget image. */
class WidgetImageCropActivity : Activity() {
    private lateinit var image: ImageView
    private var sourceBitmap: Bitmap? = null
    private val matrix = Matrix()
    private var lastX = 0f
    private var lastY = 0f
    private lateinit var scaler: ScaleGestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.data ?: run { finish(); return }
        val bitmap = decodeSampledBitmap(uri)
            ?: run { finish(); return }
        sourceBitmap = bitmap

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        image = ImageView(this).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.MATRIX
            setOnTouchListener(::onImageTouch)
        }
        root.addView(image, FrameLayout.LayoutParams(-1, -1))
        root.addView(CropOverlay(this), FrameLayout.LayoutParams(-1, -1))
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(24, 16, 24, 32)
            setBackgroundColor(0xCC000000.toInt())
            addView(Button(this@WidgetImageCropActivity).apply { text = "取消"; setOnClickListener { finish() } }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(Button(this@WidgetImageCropActivity).apply { text = "确定"; setOnClickListener { saveCrop() } }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(actions, FrameLayout.LayoutParams(-1, -2, android.view.Gravity.BOTTOM))
        setContentView(root)
        image.post {
            val scale = maxOf(image.width.toFloat() / bitmap.width, image.height.toFloat() / bitmap.height)
            matrix.setScale(scale, scale)
            matrix.postTranslate((image.width - bitmap.width * scale) / 2f, (image.height - bitmap.height * scale) / 2f)
            constrainMatrix()
            image.imageMatrix = matrix
        }
        scaler = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                matrix.postScale(detector.scaleFactor, detector.scaleFactor, detector.focusX, detector.focusY)
                constrainMatrix()
                image.imageMatrix = matrix
                return true
            }
        })
    }

    private fun onImageTouch(view: View, event: MotionEvent): Boolean {
        scaler.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { lastX = event.x; lastY = event.y }
            MotionEvent.ACTION_MOVE -> if (!scaler.isInProgress) {
                matrix.postTranslate(event.x - lastX, event.y - lastY)
                constrainMatrix()
                image.imageMatrix = matrix
                lastX = event.x; lastY = event.y
            }
        }
        return true
    }

    /** Keeps the selected bitmap covering the full square crop area at every zoom/pan. */
    private fun constrainMatrix() {
        val bitmap = sourceBitmap ?: return
        if (image.width <= 0 || image.height <= 0) return
        val cropSize = minOf(image.width, image.height) * 0.82f
        val crop = RectF(
            (image.width - cropSize) / 2f,
            (image.height - cropSize) / 2f,
            (image.width + cropSize) / 2f,
            (image.height + cropSize) / 2f
        )
        val mapped = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        matrix.mapRect(mapped)
        val scaleUp = maxOf(crop.width() / mapped.width(), crop.height() / mapped.height())
        if (scaleUp > 1f) {
            matrix.postScale(scaleUp, scaleUp, crop.centerX(), crop.centerY())
            mapped.set(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
            matrix.mapRect(mapped)
        }
        val dx = when {
            mapped.left > crop.left -> crop.left - mapped.left
            mapped.right < crop.right -> crop.right - mapped.right
            else -> 0f
        }
        val dy = when {
            mapped.top > crop.top -> crop.top - mapped.top
            mapped.bottom < crop.bottom -> crop.bottom - mapped.bottom
            else -> 0f
        }
        if (dx != 0f || dy != 0f) matrix.postTranslate(dx, dy)
    }

    private fun saveCrop() {
        try {
            if (image.width <= 0 || image.height <= 0) throw IllegalStateException("Crop view is not laid out")
            val size = (minOf(image.width, image.height) * 0.82f).toInt().coerceAtLeast(1)
            val left = (image.width - size) / 2
            val top = (image.height - size) / 2
            val output = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
            Canvas(output).apply {
                scale(512f / size, 512f / size)
                translate(-left.toFloat(), -top.toFloat())
                image.draw(this)
            }
            val file = File(filesDir, "widget_image.jpg")
            FileOutputStream(file).use { output.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            setResult(RESULT_OK, android.content.Intent().putExtra(EXTRA_RESULT_PATH, file.absolutePath))
            android.util.Log.i("QuickDailyCrop", "crop saved path=${file.absolutePath} bytes=${file.length()}")
        } catch (t: Throwable) {
            android.util.Log.e("QuickDailyCrop", "crop save failed", t)
            setResult(RESULT_CANCELED)
        } finally {
            finish()
        }
    }

    private fun decodeSampledBitmap(uri: Uri): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        } catch (t: Throwable) {
            android.util.Log.e("QuickDailyCrop", "crop decode failed", t)
            null
        }
    }

    override fun onDestroy() {
        sourceBitmap?.takeIf { !it.isRecycled }?.recycle()
        sourceBitmap = null
        super.onDestroy()
    }

    private class CropOverlay(context: android.content.Context) : View(context) {
        private val shade = Paint().apply { color = 0x99000000.toInt() }
        private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 3f }
        override fun onDraw(canvas: Canvas) {
            val s = minOf(width, height) * .82f; val l = (width - s) / 2f; val t = (height - s) / 2f
            canvas.drawRect(0f, 0f, width.toFloat(), t, shade); canvas.drawRect(0f, t + s, width.toFloat(), height.toFloat(), shade)
            canvas.drawRect(0f, t, l, t + s, shade); canvas.drawRect(l + s, t, width.toFloat(), t + s, shade)
            canvas.drawRect(l, t, l + s, t + s, border)
        }
    }

    companion object { const val EXTRA_RESULT_PATH = "widget_crop_path" }
}
