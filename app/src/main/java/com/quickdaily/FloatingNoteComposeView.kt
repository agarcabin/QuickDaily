package com.quickdaily

import android.content.Context
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy

/** Delivers hardware Back to the Service window even while an IME owns the input connection. */
internal class FloatingNoteComposeView(
    context: Context,
    private val onBackRequested: () -> Unit
) : FrameLayout(context) {
    private val composeView = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    }
    private var backDown = false

    init {
        addView(
            composeView,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
    }

    fun setComposeContent(content: @Composable () -> Unit) {
        composeView.setContent(content)
    }

    fun disposeComposition() {
        composeView.disposeComposition()
    }

    override fun dispatchKeyEventPreIme(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            return consumeBack(event)
        }
        return super.dispatchKeyEventPreIme(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            return consumeBack(event)
        }
        return super.dispatchKeyEvent(event)
    }

    private fun consumeBack(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            backDown = true
        } else if (event.action == KeyEvent.ACTION_UP && backDown) {
            backDown = false
            onBackRequested()
        }
        return true
    }
}
