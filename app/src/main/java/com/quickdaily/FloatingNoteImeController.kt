package com.quickdaily

import android.os.Build
import android.os.SystemClock
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

/** Guards the one-primary/one-fallback contract for one overlay window lifetime. */
internal class FloatingNoteImeRequestTracker {
    private val primaryAccepted = AtomicBoolean(false)
    private val fallbackAccepted = AtomicBoolean(false)

    fun acceptPrimary(): Boolean =
        !fallbackAccepted.get() && primaryAccepted.compareAndSet(false, true)

    fun acceptFallback(): Boolean =
        !primaryAccepted.get() && fallbackAccepted.compareAndSet(false, true)

    fun hasAcceptedDisplayOperation(): Boolean = primaryAccepted.get() || fallbackAccepted.get()
}

/** Insets-first IME startup for TYPE_APPLICATION_OVERLAY windows. */
internal object FloatingNoteImeController {
    private const val CONTROL_TIMEOUT_MS = 160L

    private class OperationClock {
        private val startedAt = SystemClock.elapsedRealtime()

        fun detail(detail: String?): String = buildString {
            append("operationElapsedMs=")
            append(FloatingNoteTiming.elapsedMs(SystemClock.elapsedRealtime(), startedAt))
            detail?.takeIf { it.isNotBlank() }?.let {
                append(' ')
                append(it)
            }
        }
    }

    fun show(view: View, onStage: (String, String?) -> Unit) {
        val clock = OperationClock()
        val emit: (String, String?) -> Unit = { stage, detail ->
            onStage(stage, clock.detail(detail))
        }
        val tracker = FloatingNoteImeRequestTracker()
        emit("ime_show_start", "attached=${view.isAttachedToWindow} focus=${view.hasFocus()} primary=insets ${debugState(view)}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && tryControl(view, tracker, emit)) return
        requestFallbackIfInvisible(view, tracker, emit, "primary_unavailable")
    }

    fun hide(view: View, onStage: (String, String?) -> Unit) {
        val clock = OperationClock()
        val emit: (String, String?) -> Unit = { stage, detail ->
            onStage(stage, clock.detail(detail))
        }
        emit("ime_hide_start", "attached=${view.isAttachedToWindow} focus=${view.hasFocus()} ${debugState(view)}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && tryControlHide(view, emit)) return
        fallbackHide(view, emit)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun tryControl(
        view: View,
        tracker: FloatingNoteImeRequestTracker,
        onStage: (String, String?) -> Unit,
    ): Boolean {
        val windowController = view.windowInsetsController ?: return false
        return runCatching {
            onStage("ime_control_request", "direction=show duration=0 timeoutMs=$CONTROL_TIMEOUT_MS ${debugState(view)}")
            windowController.controlWindowInsetsAnimation(
                WindowInsets.Type.ime(),
                0L,
                null,
                null,
                object : WindowInsetsAnimationControlListener {
                    override fun onReady(controller: WindowInsetsAnimationController, types: Int) {
                        if (tracker.acceptPrimary()) {
                            onStage("ime_control_ready", "direction=show types=$types accepted=true ${debugState(view)}")
                            runCatching { controller.finish(true) }
                                .onFailure {
                                    onStage("ime_control_finish_error", "direction=show error=${it.javaClass.simpleName}")
                                }
                        } else {
                            onStage("ime_control_ready_ignored", "direction=show duplicate=true")
                        }
                    }

                    override fun onFinished(controller: WindowInsetsAnimationController) {
                        onStage("ime_control_finished", "direction=show ${debugState(view)}")
                    }

                    override fun onCancelled(controller: WindowInsetsAnimationController?) {
                        onStage("ime_control_cancelled", "direction=show ${debugState(view)}")
                        requestFallbackIfInvisible(view, tracker, onStage, "cancelled")
                    }
                },
            )
            postState(view, onStage, "show_control_request")
            view.postDelayed({
                if (isImeVisible(view)) {
                    onStage("ime_fallback_skipped", "reason=timeout visible=true ${debugState(view)}")
                } else {
                    requestFallbackIfInvisible(view, tracker, onStage, "timeout")
                }
            }, CONTROL_TIMEOUT_MS)
            true
        }.getOrElse {
            onStage("ime_control_error", "direction=show error=${it.javaClass.simpleName}")
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun tryControlHide(view: View, onStage: (String, String?) -> Unit): Boolean {
        val windowController = view.windowInsetsController ?: return false
        val completed = AtomicBoolean(false)
        return runCatching {
            onStage("ime_control_request", "direction=hide duration=0 timeoutMs=$CONTROL_TIMEOUT_MS ${debugState(view)}")
            windowController.controlWindowInsetsAnimation(
                WindowInsets.Type.ime(),
                0L,
                null,
                null,
                object : WindowInsetsAnimationControlListener {
                    override fun onReady(controller: WindowInsetsAnimationController, types: Int) {
                        if (completed.compareAndSet(false, true)) {
                            onStage("ime_control_ready", "direction=hide types=$types ${debugState(view)}")
                            runCatching { controller.finish(false) }
                        }
                    }

                    override fun onFinished(controller: WindowInsetsAnimationController) {
                        onStage("ime_control_finished", "direction=hide ${debugState(view)}")
                    }

                    override fun onCancelled(controller: WindowInsetsAnimationController?) {
                        if (completed.compareAndSet(false, true)) fallbackHide(view, onStage)
                    }
                },
            )
            postState(view, onStage, "hide_control_request")
            view.postDelayed({
                if (completed.compareAndSet(false, true)) fallbackHide(view, onStage)
            }, CONTROL_TIMEOUT_MS)
            true
        }.getOrElse {
            onStage("ime_control_error", "direction=hide error=${it.javaClass.simpleName}")
            false
        }
    }

    private fun requestFallbackIfInvisible(
        view: View,
        tracker: FloatingNoteImeRequestTracker,
        onStage: (String, String?) -> Unit,
        reason: String,
    ) {
        if (isImeVisible(view)) {
            onStage("ime_fallback_skipped", "reason=$reason visible=true ${debugState(view)}")
            return
        }
        if (!tracker.acceptFallback()) {
            onStage("ime_fallback_skipped", "reason=$reason visible=false acceptedOperation=${tracker.hasAcceptedDisplayOperation()} ${debugState(view)}")
            return
        }
        onStage("ime_fallback_start", "reason=$reason visible=false ${debugState(view)}")
        fallbackShow(view, onStage, reason)
    }

    private fun fallbackShow(view: View, onStage: (String, String?) -> Unit, reason: String) {
        if (!view.isAttachedToWindow) {
            onStage("ime_show_fallback_deferred", "reason=$reason attached=false")
            view.post {
                if (view.isAttachedToWindow) {
                    val accepted = requestShowSoftInput(view, onStage, "fallback_$reason")
                    onStage("ime_show_fallback", "reason=$reason accepted=$accepted ${debugState(view)}")
                    postState(view, onStage, "show_fallback_$reason")
                } else {
                    onStage("ime_show_fallback_skipped", "reason=$reason detached=true")
                }
            }
            return
        }
        val accepted = requestShowSoftInput(view, onStage, "fallback_$reason")
        onStage("ime_show_fallback", "reason=$reason accepted=$accepted ${debugState(view)}")
        postState(view, onStage, "show_fallback_$reason")
    }

    private fun requestShowSoftInput(view: View, onStage: (String, String?) -> Unit, reason: String): Boolean {
        if (!view.isAttachedToWindow) return false
        val manager = view.context.getSystemService(InputMethodManager::class.java)
        val accepted = manager?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT) == true
        onStage(
            "ime_show_soft_input",
            "reason=$reason accepted=$accepted managerAvailable=${manager != null} attached=${view.isAttachedToWindow} " +
                "focus=${view.hasFocus()} token=${view.windowToken != null} ${debugState(view)}",
        )
        return accepted
    }

    private fun fallbackHide(view: View, onStage: (String, String?) -> Unit) {
        val token = view.windowToken
        val manager = view.context.getSystemService(InputMethodManager::class.java)
        val hidden = token != null && manager?.hideSoftInputFromWindow(token, InputMethodManager.HIDE_NOT_ALWAYS) == true
        onStage(
            "ime_hide_fallback",
            "accepted=$hidden managerAvailable=${manager != null} token=${token != null} " +
                "attached=${view.isAttachedToWindow} focus=${view.hasFocus()} ${debugState(view)}",
        )
        postState(view, onStage, "hide_fallback")
    }

    private fun postState(view: View, onStage: (String, String?) -> Unit, reason: String) {
        view.post {
            onStage(
                if (reason.startsWith("hide_")) "ime_hide_post" else "ime_show_post",
                "reason=$reason attached=${view.isAttachedToWindow} focus=${view.hasFocus()} ${debugState(view)}",
            )
        }
    }

    internal fun debugState(view: View): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val insets = view.rootWindowInsets
        val visible = insets?.isVisible(WindowInsets.Type.ime()) == true
        val bottom = insets?.getInsets(WindowInsets.Type.ime())?.bottom ?: 0
        "imeVisible=$visible imeBottom=$bottom"
    } else {
        "imeVisible=unknown imeBottom=unknown"
    }

    private fun isImeVisible(view: View): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            view.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true
}
