package com.quickdaily

import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsAnimationControlListener
import android.view.WindowInsetsAnimationController
import android.view.inputmethod.InputMethodManager
import androidx.annotation.RequiresApi
import java.util.concurrent.atomic.AtomicBoolean

/** The editor host determines whether the platform Activity or an overlay owns IME startup. */
enum class FloatingNoteImePolicy {
    ActivityDefault,
    OverlayInstant,
}

/** Best-effort zero-duration IME handoff for TYPE_APPLICATION_OVERLAY windows. */
internal object FloatingNoteImeController {
    private const val CONTROL_TIMEOUT_MS = 80L

    fun show(view: View, onStage: (String, String?) -> Unit) {
        val earlyAccepted = requestShowSoftInput(view, onStage, "early")
        onStage(
            "ime_show_early",
            "attached=" + view.isAttachedToWindow + " accepted=" + earlyAccepted,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && tryControl(view, true, onStage)) return
        fallbackShow(view, onStage, "initial")
    }

    fun hide(view: View, onStage: (String, String?) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && tryControl(view, false, onStage)) return
        fallbackHide(view, onStage)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun tryControl(
        view: View,
        show: Boolean,
        onStage: (String, String?) -> Unit,
    ): Boolean {
        val windowController = view.windowInsetsController ?: return false
        val direction = if (show) "show" else "hide"
        val completed = AtomicBoolean(false)

        fun fallback(reason: String) {
            if (!completed.compareAndSet(false, true)) return
            onStage("ime_control_fallback", "direction=" + direction + " reason=" + reason)
            if (show) {
                fallbackShow(view, onStage, reason)
            } else {
                fallbackHide(view, onStage)
            }
        }

        return runCatching {
            onStage("ime_control_request", "direction=" + direction + " duration=0 timeoutMs=" + CONTROL_TIMEOUT_MS)
            windowController.controlWindowInsetsAnimation(
                WindowInsets.Type.ime(),
                0L,
                null,
                null,
                object : WindowInsetsAnimationControlListener {
                    override fun onReady(controller: WindowInsetsAnimationController, types: Int) {
                        if (completed.compareAndSet(false, true)) {
                            onStage("ime_control_ready", "direction=" + direction + " types=" + types)
                            if (show) {
                                val accepted = requestShowSoftInput(view, onStage, "control_ready")
                                onStage("ime_show_ready", "accepted=" + accepted)
                            }
                            runCatching { controller.finish(show) }
                                .onFailure {
                                    onStage(
                                        "ime_control_finish_error",
                                        "direction=$direction error=${it.javaClass.simpleName}",
                                    )
                                }
                        }
                    }

                    override fun onFinished(controller: WindowInsetsAnimationController) {
                        onStage("ime_control_finished", "direction=$direction")
                    }

                    override fun onCancelled(controller: WindowInsetsAnimationController?) {
                        fallback("cancelled")
                    }
                },
            )
            view.postDelayed({ fallback("timeout") }, CONTROL_TIMEOUT_MS)
            true
        }.getOrElse {
            onStage("ime_control_error", "direction=$direction error=${it.javaClass.simpleName}")
            false
        }
    }

    private fun fallbackShow(
        view: View,
        onStage: (String, String?) -> Unit,
        reason: String,
    ) {
        if (!view.isAttachedToWindow) {
            onStage("ime_show_fallback_deferred", "reason=" + reason + " attached=false")
            view.post {
                if (view.isAttachedToWindow) {
                    val accepted = requestShowSoftInput(view, onStage, "fallback_" + reason)
                    onStage("ime_show_fallback", "reason=" + reason + " accepted=" + accepted)
                } else {
                    onStage("ime_show_fallback_skipped", "reason=" + reason + " detached=true")
                }
            }
            return
        }
        val accepted = requestShowSoftInput(view, onStage, "fallback_" + reason)
        onStage("ime_show_fallback", "reason=" + reason + " accepted=" + accepted)
    }

    private fun requestShowSoftInput(
        view: View,
        onStage: (String, String?) -> Unit,
        reason: String,
    ): Boolean {
        if (!view.isAttachedToWindow) return false
        val manager = view.context.getSystemService(InputMethodManager::class.java)
        val accepted = manager?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT) == true
        onStage("ime_show_soft_input", "reason=" + reason + " accepted=" + accepted)
        return accepted
    }

    private fun fallbackHide(view: View, onStage: (String, String?) -> Unit) {
        val token = view.windowToken
        val hidden = token != null && view.context
            .getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(token, InputMethodManager.HIDE_NOT_ALWAYS) == true
        onStage("ime_hide_fallback", "accepted=$hidden token=${token != null}")
    }
}
