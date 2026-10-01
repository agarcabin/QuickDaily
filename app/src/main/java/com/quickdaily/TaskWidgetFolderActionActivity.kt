package com.quickdaily

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Resolves a folder widget's newest eligible page immediately before Add/Open. */
class TaskWidgetFolderActionActivity : LocalizedComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, INVALID_WIDGET_ID)
        val expectedGeneration = intent.getLongExtra(WidgetFillInContract.EXTRA_GENERATION, 0L)
        val action = intent.action
        if (widgetId < 0 || action !in setOf(ACTION_ADD, ACTION_OPEN)) {
            finishWithMessage(UiText.Resource(R.string.qd_task_action_invalid))
            return
        }

        val manager = AppWidgetManager.getInstance(this)
        val installed = manager.getAppWidgetIds(
            ComponentName(this, TaskWidget::class.java),
        ).contains(widgetId)
        if (!installed) {
            finishWithMessage(UiText.Resource(R.string.qd_task_widget_removed))
            return
        }
        if (!WidgetGeneration.isCurrent(
                this,
                WidgetFillInContract.KIND_TASK,
                widgetId,
                expectedGeneration,
            )
        ) {
            finishWithMessage(UiText.Resource(R.string.qd_task_widget_config_updated_retry))
            return
        }

        val config = TaskWidgetConfigStore.peek(this, widgetId)
        if (config.scope != TaskWidgetScope.CUSTOM_FOLDER) {
            finishWithMessage(UiText.Resource(R.string.qd_widget_not_folder_mode))
            return
        }

        lifecycleScope.launch {
            val resolution = withContext(Dispatchers.IO) {
                TaskWidgetFolderResolver.resolveForAction(this@TaskWidgetFolderActionActivity, config)
            }
            if (isFinishing) return@launch
            if (!WidgetGeneration.isCurrent(
                    this@TaskWidgetFolderActionActivity,
                    WidgetFillInContract.KIND_TASK,
                    widgetId,
                    expectedGeneration,
                )
            ) {
                finishWithMessage(UiText.Resource(R.string.qd_task_widget_config_updated_retry))
                return@launch
            }
            when (resolution) {
                is TaskWidgetFolderActionResolution.Failure -> finishWithMessage(resolution.message)
                is TaskWidgetFolderActionResolution.Target -> {
                    when (action) {
                        ACTION_OPEN -> openEditor(resolution.sourcePath)
                        ACTION_ADD -> openQuickNote(resolution.sourcePath, resolution.displayName)
                    }
                }
            }
        }
    }

    private fun openEditor(sourcePath: String) {
        startActivity(MainActivity.editorIntent(this, sourcePath))
        finish()
    }

    private fun openQuickNote(sourcePath: String, displayName: String) {
        val title = "$displayName ${getString(R.string.qd_editor_quick_capture_suffix)}"
        val request = FloatingNoteRequest(
            source = FloatingNoteSource.WIDGET,
            prefillText = "- [ ] ",
            returnToHomeAfterClose = false,
            targetRelativePath = sourcePath,
            displayTitle = title,
            rememberTarget = false,
        )
        val shown = FloatingNoteControllerProvider.forContext(this).showOrFocus(request)
        if (!shown) {
            startActivity(Intent(this, NoteEditActivity::class.java).apply {
                putExtra("prefill_text", "- [ ] ")
                putExtra(NoteEditActivity.EXTRA_TARGET_RELATIVE_PATH, sourcePath)
                putExtra(NoteEditActivity.EXTRA_DIALOG_TITLE, title)
                putExtra("floating_source", FloatingNoteSource.WIDGET.name)
                putExtra(NoteEditActivity.EXTRA_REMEMBER_TARGET, false)
            })
        }
        finish()
    }

    private fun finishWithMessage(message: UiText) {
        Toast.makeText(this, message.resolve(this), Toast.LENGTH_SHORT).show()
        finish()
    }

    companion object {
        const val ACTION_ADD = "com.quickdaily.TASK_FOLDER_ADD"
        const val ACTION_OPEN = "com.quickdaily.TASK_FOLDER_OPEN"
        private const val INVALID_WIDGET_ID = -1
    }
}
