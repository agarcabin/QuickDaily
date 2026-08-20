package com.quickdaily

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Rect
import android.view.View
import android.view.animation.DecelerateInterpolator

internal data class FloatingNoteScreenBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

internal data class FloatingNoteExitTransform(
    val scaleX: Float,
    val scaleY: Float,
    val translationX: Float,
    val translationY: Float,
    val alpha: Float,
)

internal object FloatingNoteExitAnimationPolicy {
    const val DURATION_MS = 220L
    private const val MIN_ALPHA = 0.08f

    fun isEligible(
        source: FloatingNoteSource,
        sourceBounds: FloatingNoteScreenBounds?,
        currentBounds: FloatingNoteScreenBounds,
        screenWidth: Int,
        screenHeight: Int,
    ): Boolean {
        val target = sourceBounds ?: return false
        return source == FloatingNoteSource.WIDGET &&
            currentBounds.width > 0 &&
            currentBounds.height > 0 &&
            target.width > 0 &&
            target.height > 0 &&
            target.right > 0 &&
            target.bottom > 0 &&
            target.left < screenWidth &&
            target.top < screenHeight
    }

    fun transform(
        currentBounds: FloatingNoteScreenBounds,
        targetBounds: FloatingNoteScreenBounds,
        progress: Float,
    ): FloatingNoteExitTransform {
        val fraction = progress.coerceIn(0f, 1f)
        val targetCenterX = targetBounds.centerX
        val targetCenterY = targetBounds.centerY
        val currentCenterX = currentBounds.centerX
        val currentCenterY = currentBounds.centerY
        val targetScaleX = targetBounds.width.toFloat() / currentBounds.width.toFloat()
        val targetScaleY = targetBounds.height.toFloat() / currentBounds.height.toFloat()
        return FloatingNoteExitTransform(
            scaleX = 1f + (targetScaleX - 1f) * fraction,
            scaleY = 1f + (targetScaleY - 1f) * fraction,
            translationX = (targetCenterX - currentCenterX) * fraction,
            translationY = (targetCenterY - currentCenterY) * fraction,
            alpha = 1f - (1f - MIN_ALPHA) * fraction,
        )
    }
}

internal object FloatingNoteExitAnimator {
    fun animateToSource(
        view: View,
        source: FloatingNoteSource,
        sourceBounds: Rect?,
        onEnd: () -> Unit,
    ): Boolean {
        val currentBounds = Rect()
        if (!view.getGlobalVisibleRect(currentBounds)) return false
        val targetBounds = sourceBounds?.let(::Rect) ?: return false
        val metrics = view.resources.displayMetrics
        val currentScreenBounds = currentBounds.toFloatingNoteScreenBounds()
        val targetScreenBounds = targetBounds.toFloatingNoteScreenBounds()
        if (!FloatingNoteExitAnimationPolicy.isEligible(
                source,
                targetScreenBounds,
                currentScreenBounds,
                metrics.widthPixels,
                metrics.heightPixels,
            )
        ) return false

        view.pivotX = currentBounds.width() / 2f
        view.pivotY = currentBounds.height() / 2f
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = FloatingNoteExitAnimationPolicy.DURATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { valueAnimator ->
                applyTransform(
                    view,
                    currentScreenBounds,
                    targetScreenBounds,
                    valueAnimator.animatedValue as Float,
                )
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onEnd()
                }
            })
        }
        animator.start()
        return true
    }

    fun applyProgress(
        view: View,
        currentBounds: Rect,
        targetBounds: Rect,
        progress: Float,
    ) {
        applyTransform(
            view,
            currentBounds.toFloatingNoteScreenBounds(),
            targetBounds.toFloatingNoteScreenBounds(),
            progress,
        )
    }

    private fun applyTransform(
        view: View,
        currentBounds: FloatingNoteScreenBounds,
        targetBounds: FloatingNoteScreenBounds,
        progress: Float,
    ) {
        val transform = FloatingNoteExitAnimationPolicy.transform(currentBounds, targetBounds, progress)
        view.scaleX = transform.scaleX
        view.scaleY = transform.scaleY
        view.translationX = transform.translationX
        view.translationY = transform.translationY
        view.alpha = transform.alpha
    }

    fun reset(view: View) {
        view.animate().cancel()
        view.scaleX = 1f
        view.scaleY = 1f
        view.translationX = 0f
        view.translationY = 0f
        view.alpha = 1f
    }

}

internal fun Rect.toFloatingNoteScreenBounds(): FloatingNoteScreenBounds =
    FloatingNoteScreenBounds(left = left, top = top, right = right, bottom = bottom)
