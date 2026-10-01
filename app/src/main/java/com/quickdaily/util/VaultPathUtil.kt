package com.quickdaily.util

import java.io.File

/** Safe conversion between a vault-relative Markdown path and a filesystem path. */
object VaultPathUtil {
    /** Resolve either an absolute filesystem path or a vault-relative path. */
    fun resolveTarget(vaultPath: String, targetPath: String): String? {
        if (targetPath.isBlank()) return null
        if (SafDocumentPath.isPath(targetPath)) {
            return SafDocumentPath.parse(targetPath)?.let { targetPath.trimEnd('/') }
        }
        if (SafVirtualPath.isSafPath(vaultPath)) {
            if (SafVirtualPath.isSafPath(targetPath)) {
                val vault = SafVirtualPath.parse(vaultPath) ?: return null
                val target = SafVirtualPath.parse(targetPath) ?: return null
                if (vault.rootPath != target.rootPath) return null
                return targetPath.trimEnd('/')
            }
            return SafVirtualPath.join(vaultPath, targetPath)
        }
        return try {
            val target = File(targetPath)
            if (target.isAbsolute) target.canonicalPath else resolve(vaultPath, targetPath)
        } catch (_: Exception) {
            null
        }
    }

    fun resolve(vaultPath: String, relativePath: String): String? {
        if (vaultPath.isBlank() || relativePath.isBlank()) return null
        if (SafVirtualPath.isSafPath(vaultPath)) {
            return SafVirtualPath.join(vaultPath, relativePath)
        }
        return try {
            val root = File(vaultPath).canonicalFile
            val target = File(root, relativePath.replace('/', File.separatorChar)).canonicalFile
            if (!isWithin(root, target)) null else target.path
        } catch (_: Exception) {
            null
        }
    }

    fun relativePath(vaultPath: String, filePath: String): String? {
        if (vaultPath.isBlank() || filePath.isBlank()) return null
        if (SafVirtualPath.isSafPath(vaultPath) || SafVirtualPath.isSafPath(filePath)) {
            val root = SafVirtualPath.parse(vaultPath) ?: return null
            val target = SafVirtualPath.parse(filePath) ?: return null
            if (root.rootPath != target.rootPath || target.relativePath.isBlank()) return null
            return target.relativePath
        }
        return try {
            val root = File(vaultPath).canonicalFile
            val target = File(filePath).canonicalFile
            if (!isWithin(root, target) || root.path == target.path) return null
            target.relativeTo(root).invariantSeparatorsPath
        } catch (_: Exception) {
            null
        }
    }

    private fun isWithin(root: File, target: File): Boolean =
        target.path == root.path || target.path.startsWith(root.path + File.separator)
}
