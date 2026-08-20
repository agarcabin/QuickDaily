package com.quickdaily

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.quickdaily.util.DateUtil

internal object FloatingNoteObsidianLaunchPolicy {
    const val PREF_KEY = "open_obsidian_after_floating_save"
    const val DEFAULT_ENABLED = false
    const val OBSIDIAN_PACKAGE = "md.obsidian"

    fun isBackgroundProcessImportance(importance: Int): Boolean =
        importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND

    fun savedRelativePath(
        targetRelativePath: String?,
        diaryFolder: String,
        today: String,
    ): String {
        targetRelativePath?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        val folder = diaryFolder.trim().trim('/')
        return if (folder.isBlank()) "$today.md" else "$folder/$today.md"
    }

    fun vaultName(vaultPath: String): String = vaultPath
        .trim()
        .trimEnd('/', '\\')
        .substringAfterLast('/')
        .substringAfterLast('\\')
}

/** Opens the saved note only when Obsidian is not already in the background, then returns home. */
internal object FloatingNoteObsidianLauncher {
    fun openAfterSuccessfulSave(context: Context, targetRelativePath: String?): Boolean {
        val prefs = context.getSharedPreferences("QuickDaily", Context.MODE_PRIVATE)
        if (!prefs.getBoolean(
                FloatingNoteObsidianLaunchPolicy.PREF_KEY,
                FloatingNoteObsidianLaunchPolicy.DEFAULT_ENABLED,
            )
        ) {
            return false
        }

        if (isObsidianInBackground(context)) {
            BetaLogger.log("FloatingNote/Obsidian", "skip already_background")
            return false
        }

        val vaultPath = prefs.getString("vault_path", "").orEmpty()
        val vaultName = FloatingNoteObsidianLaunchPolicy.vaultName(vaultPath)
        if (vaultName.isBlank()) return false

        val dateFormat = prefs.getString("date_format", "YYYY-MM-DD").orEmpty()
        val relativePath = FloatingNoteObsidianLaunchPolicy.savedRelativePath(
            targetRelativePath = targetRelativePath,
            diaryFolder = prefs.getString("diary_folder", "Daily").orEmpty(),
            today = DateUtil.todayStr(dateFormat),
        )
        val uri = Uri.Builder()
            .scheme("obsidian")
            .authority("open")
            .appendQueryParameter("vault", vaultName)
            .appendQueryParameter("file", relativePath)
            .build()
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_HOME)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.onFailure { error ->
                BetaLogger.logException("FloatingNote/Obsidian", "home_failed", error)
            }
            BetaLogger.log(
                "FloatingNote/Obsidian",
                "opened saved target=${targetRelativePath.orEmpty()} then_home=true",
            )
            true
        } catch (error: Exception) {
            BetaLogger.logException("FloatingNote/Obsidian", "open_failed", error)
            Toast.makeText(context, "未安装 Obsidian", Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun isObsidianInBackground(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return false
        return manager.runningAppProcesses.orEmpty()
            .filter { process ->
                process.pkgList?.contains(FloatingNoteObsidianLaunchPolicy.OBSIDIAN_PACKAGE) == true
            }
            .any { process ->
                FloatingNoteObsidianLaunchPolicy.isBackgroundProcessImportance(process.importance)
            }
    }
}
