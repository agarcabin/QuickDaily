package com.quickdaily

import android.os.Environment
import java.io.File

/** Public files owned by QuickDaily. App settings remain in private storage. */
object ExternalStoragePaths {
    private const val APP_DIRECTORY = "QuickDaily"
    private val LEGACY_LOG_PATTERN = Regex("^QuickDaily_log_\\d{4}-\\d{2}-\\d{2}\\.txt$")

    fun diagnosticsDirectory(): File {
        @Suppress("DEPRECATION")
        val documents = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        return File(documents, APP_DIRECTORY)
    }

    /**
     * Move only dated logs created by QuickDaily from Documents into its own directory.
     * Existing destination files are never overwritten and the source is retained.
     */
    fun migrateLegacyLogs(): MigrationResult {
        return try {
            @Suppress("DEPRECATION")
            val documents = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val legacyFiles = documents.listFiles { file ->
                file.isFile && LEGACY_LOG_PATTERN.matches(file.name)
            }.orEmpty()
            if (legacyFiles.isEmpty()) return MigrationResult()

            val destinationDirectory = File(documents, APP_DIRECTORY)
            if (!destinationDirectory.exists() && !destinationDirectory.mkdirs()) {
                return MigrationResult(skipped = legacyFiles.size)
            }

            var moved = 0
            var skipped = 0
            legacyFiles.forEach { source ->
                val destination = File(destinationDirectory, source.name)
                if (destination.exists()) {
                    skipped++
                } else if (source.renameTo(destination)) {
                    moved++
                } else {
                    skipped++
                }
            }
            MigrationResult(moved = moved, skipped = skipped)
        } catch (_: Exception) {
            MigrationResult()
        }
    }

    data class MigrationResult(val moved: Int = 0, val skipped: Int = 0)
}
