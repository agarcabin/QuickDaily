package com.quickdaily

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/** Small chooser launched from one task widget instance. */
class TaskWidgetConfigActivity : ComponentActivity() {
    private var widgetId: Int = AppWidgetId.INVALID
    private var currentConfig: TaskWidgetConfig = TaskWidgetConfig()
    private var chooser: AlertDialog? = null

    private val filePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            showChooser()
            return@registerForActivityResult
        }
        val filePath = TaskWidgetConfigStore.filePathFromUri(this, uri)
        if (filePath == null) {
            Toast.makeText(this, "请选择可访问的 Markdown 文件", Toast.LENGTH_LONG).show()
            showChooser()
            return@registerForActivityResult
        }
        currentConfig = TaskWidgetConfig(TaskWidgetScope.CUSTOM, filePath)
        TaskWidgetConfigStore.save(this, widgetId, currentConfig)
        TaskWidget.refreshAllWidgets(this, immediate = true)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent.getIntExtra(AppWidgetId.EXTRA, AppWidgetId.INVALID)
        if (widgetId == AppWidgetId.INVALID) {
            finish()
            return
        }
        currentConfig = TaskWidgetConfigStore.load(this, widgetId)
        showChooser()
    }

    private fun showChooser() {
        chooser?.dismiss()

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), 0, dp(18), dp(8))
        }

        addChoiceRow(
            content,
            TaskWidgetScope.TODAY.label,
            currentConfig.scope == TaskWidgetScope.TODAY,
        ) {
            chooseScope(TaskWidgetScope.TODAY)
        }
        addChoiceRow(
            content,
            TaskWidgetScope.WEEK.label,
            currentConfig.scope == TaskWidgetScope.WEEK,
        ) {
            chooseScope(TaskWidgetScope.WEEK)
        }
        addChoiceRow(
            content,
            TaskWidgetScope.MONTH.label,
            currentConfig.scope == TaskWidgetScope.MONTH,
        ) {
            chooseScope(TaskWidgetScope.MONTH)
        }
        addChoiceRow(
            content,
            TaskWidgetScope.CUSTOM.label,
            currentConfig.scope == TaskWidgetScope.CUSTOM && currentConfig.customRelativePath.isBlank(),
        ) {
            chooser?.dismiss()
            filePicker.launch(arrayOf("text/*", "application/octet-stream", "*/*"))
        }

        val recentPages = TaskWidgetConfigStore.recentCustomPaths(this)
        if (recentPages.isNotEmpty()) {
            content.addView(TextView(this).apply {
                text = "最近的自定义页面"
                setTextColor(Color.GRAY)
                textSize = 12f
                setPadding(dp(16), dp(8), dp(16), dp(2))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
            })
        }
        recentPages.forEach { path ->
            val available = TaskWidgetConfigStore.customFilePath(
                this,
                TaskWidgetConfig(TaskWidgetScope.CUSTOM, path),
            )?.let { java.io.File(it).isFile } == true
            addCustomPageRow(
                content = content,
                path = path,
                checked = currentConfig.scope == TaskWidgetScope.CUSTOM &&
                    TaskWidgetConfigStore.customFilePath(this, currentConfig) ==
                    TaskWidgetConfigStore.customFilePath(this, TaskWidgetConfig(TaskWidgetScope.CUSTOM, path)),
                available = available,
            ) {
                currentConfig = TaskWidgetConfig(TaskWidgetScope.CUSTOM, path)
                TaskWidgetConfigStore.save(this, widgetId, currentConfig)
                TaskWidget.refreshAllWidgets(this, immediate = true)
                chooser?.dismiss()
                finish()
            }
        }

        chooser = AlertDialog.Builder(this)
            .setTitle("选择任务范围")
            .setView(content)
            .setOnCancelListener { finish() }
            .create()
        chooser?.show()
    }

    private fun chooseScope(scope: TaskWidgetScope) {
        currentConfig = TaskWidgetConfig(scope)
        TaskWidgetConfigStore.save(this, widgetId, currentConfig)
        TaskWidget.refreshAllWidgets(this, immediate = true)
        chooser?.dismiss()
        finish()
    }

    private fun addChoiceRow(
        parent: LinearLayout,
        label: String,
        checked: Boolean,
        onClick: () -> Unit,
    ) {
        val radio = RadioButton(this).apply {
            text = label
            isChecked = checked
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, dp(52), 1f)
        }
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(52)
            addView(radio)
            setOnClickListener { onClick() }
        }
        radio.setOnClickListener { onClick() }
        parent.addView(row)
    }

    private fun addCustomPageRow(
        content: LinearLayout,
        path: String,
        checked: Boolean,
        available: Boolean,
        onClick: () -> Unit,
    ) {
        val label = TaskWidgetConfigStore.displayName(path).ifBlank { path }
        val radio = RadioButton(this).apply {
            text = if (available) label else "$label（不可用）"
            isChecked = checked
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, dp(52), 1f)
        }
        val remove = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            contentDescription = "移除自定义页面"
            background = null
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            setOnClickListener {
                TaskWidgetConfigStore.removeCustomPage(this@TaskWidgetConfigActivity, path)
                showChooser()
            }
        }
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(52)
            addView(radio)
            addView(remove)
            setOnClickListener { onClick() }
        }
        radio.setOnClickListener { onClick() }
        content.addView(row)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private object AppWidgetId {
        const val EXTRA = "appWidgetId"
        const val INVALID = -1
    }
}
