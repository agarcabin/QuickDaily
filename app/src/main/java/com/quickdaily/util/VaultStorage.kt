package com.quickdaily.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Base64
import androidx.documentfile.provider.DocumentFile
import com.quickdaily.BetaLogger
import com.quickdaily.LocaleController
import com.quickdaily.UiText
import com.quickdaily.R
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes

/** Process-wide application context used by the legacy path-shaped APIs. */
object StorageContextHolder {
    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun get(): Context? = appContext
}

/**
 * A path-shaped identity for an SAF tree.  It is deliberately not a filesystem
 * path: the first segment contains the original tree URI and the rest is a
 * vault-relative document path.
 */
object SafVirtualPath {
    private const val PREFIX = "saf://"

    data class Parsed(
        val rootUri: Uri,
        val rootPath: String,
        val relativePath: String,
    )

    fun rootForUri(uri: Uri): String {
        val token = Base64.encodeToString(
            uri.toString().toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
        return PREFIX + token
    }

    fun isSafPath(path: String): Boolean = path.trim().startsWith(PREFIX)

    fun parse(path: String): Parsed? {
        val trimmed = path.trim()
        if (!trimmed.startsWith(PREFIX)) return null
        val payload = trimmed.removePrefix(PREFIX)
        val slash = payload.indexOf('/')
        val token = if (slash < 0) payload else payload.substring(0, slash)
        if (token.isBlank()) return null
        val uri = runCatching {
            val decoded = Base64.decode(token, Base64.URL_SAFE or Base64.NO_WRAP)
            Uri.parse(String(decoded, Charsets.UTF_8))
        }.getOrNull() ?: return null
        if (uri.scheme != ContentResolverSchemes.CONTENT) return null
        val relative = if (slash < 0) "" else normalizeRelative(payload.substring(slash + 1)) ?: return null
        return Parsed(
            rootUri = uri,
            rootPath = PREFIX + token,
            relativePath = relative,
        )
    }

    fun join(rootOrPath: String, relativePath: String): String? {
        val parsed = parse(rootOrPath) ?: return null
        val extra = normalizeRelative(relativePath) ?: return null
        val combined = listOf(parsed.relativePath, extra)
            .filter(String::isNotBlank)
            .joinToString("/")
        return if (combined.isBlank()) parsed.rootPath else "${parsed.rootPath}/$combined"
    }

    fun normalizeRelative(path: String): String? {
        val raw = path.replace('\\', '/').trim()
        if (
            raw.startsWith('/') ||
            raw.startsWith("//") ||
            raw.contains("://") ||
            Regex("^[A-Za-z]:/").containsMatchIn(raw)
        ) return null
        val normalized = raw.trim('/')
        if (normalized.isBlank()) return ""
        val pieces = normalized.split('/')
        if (pieces.any { it.isBlank() || it == "." || it == ".." }) return null
        return pieces.joinToString("/")
    }

    private object ContentResolverSchemes {
        const val CONTENT = "content"
    }
}

/**
 * Exact SAF document identity used for files discovered from a selected folder.
 *
 * A tree URI plus a filename is not stable when a provider contains duplicate
 * names or a file is renamed/replaced.  This path-shaped value keeps the
 * document URI, its provider document id, and the tree that granted access.
 */
object SafDocumentPath {
    private const val PREFIX = "safdoc://"
    private const val SEPARATOR = "\u0000"

    data class Parsed(
        val documentUri: Uri,
        val treeUri: Uri?,
        val documentId: String,
    )

    fun isPath(path: String): Boolean = path.trim().startsWith(PREFIX)

    fun forDocumentUri(documentUri: Uri, treeUri: Uri? = null): String? {
        val documentId = runCatching { DocumentsContract.getDocumentId(documentUri) }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val payload = listOf(treeUri?.toString().orEmpty(), documentUri.toString(), documentId)
            .joinToString(SEPARATOR)
        val token = Base64.encodeToString(
            payload.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
        return PREFIX + token
    }

    fun parse(path: String): Parsed? {
        val token = path.trim().removePrefix(PREFIX).takeIf { it.isNotBlank() } ?: return null
        val parts = runCatching {
            String(Base64.decode(token, Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
                .split(SEPARATOR)
        }.getOrNull() ?: return null
        if (parts.size != 3 || parts[1].isBlank() || parts[2].isBlank()) return null
        val documentUri = runCatching { Uri.parse(parts[1]) }.getOrNull()
            ?.takeIf { it.scheme.equals("content", ignoreCase = true) }
            ?: return null
        val treeUri = parts[0].takeIf { it.isNotBlank() }?.let { raw ->
            runCatching { Uri.parse(raw) }.getOrNull()
                ?.takeIf { it.scheme.equals("content", ignoreCase = true) }
        }
        return Parsed(documentUri, treeUri, parts[2])
    }

    /** Confirm the current document identity is still inside its granting tree. */
    fun isWithinTree(parsed: Parsed): Boolean {
        val treeUri = parsed.treeUri ?: return true
        if (!treeUri.authority.equals(parsed.documentUri.authority, ignoreCase = true)) return false
        val treeId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return false
        return parsed.documentId == treeId || parsed.documentId.startsWith("$treeId/")
    }

    fun leafName(context: Context?, path: String): String? {
        val parsed = parse(path) ?: return null
        val directName = context?.let {
            DocumentFile.fromSingleUri(it, parsed.documentUri)?.name
        }
        return directName?.takeIf { it.isNotBlank() }
            ?: parsed.documentId.substringAfterLast('/').takeIf { it.isNotBlank() }
    }

    fun leafName(path: String): String? = parse(path)
        ?.documentId
        ?.substringAfterLast('/')
        ?.takeIf { it.isNotBlank() }
}

enum class VaultBackend {
    FILE,
    SAF,
}

data class VaultStorageInfo(
    val backend: VaultBackend,
    val rootPath: String,
    val treeUri: String = "",
    val displayName: String = "",
)

enum class VaultValidationStatus {
    VALID,
    NOT_CONFIGURED,
    LEGACY_PATH_RESELECT,
    URI_INVALID,
    DIRECTORY_NOT_FOUND,
    NOT_READABLE,
    NOT_WRITABLE,
}

data class VaultValidationResult(
    val status: VaultValidationStatus,
    val message: UiText,
    val info: VaultStorageInfo,
)

/** Persisted vault identity and validation helpers. */
object VaultStoragePrefs {
    const val PREFS = "QuickDaily"
    const val BACKEND_KEY = "vault_backend"
    const val TREE_URI_KEY = "vault_tree_uri"
    const val DISPLAY_NAME_KEY = "vault_display_name"
    const val OBSIDIAN_VAULT_ID_KEY = "obsidian_vault_id"

    fun current(context: Context): VaultStorageInfo {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val treeUri = prefs.getString(TREE_URI_KEY, "").orEmpty().trim()
        val backend = prefs.getString(BACKEND_KEY, "").orEmpty().trim().lowercase()
        val storedPath = prefs.getString("vault_path", "").orEmpty().trim()
        val storedVirtual = SafVirtualPath.parse(storedPath)
        val parsedTreeUri = treeUri.takeIf { it.isNotBlank() }?.let {
            runCatching { Uri.parse(it) }
                .getOrNull()
                ?.takeIf { uri -> uri.scheme.equals("content", ignoreCase = true) }
        }
        val safUri = parsedTreeUri ?: storedVirtual?.rootUri
        val useSaf = if (backend == "file") {
            // A stale tree key from an older build must not override an
            // explicitly configured physical vault. A virtual vault path is
            // still treated as SAF so an incomplete migration remains safe.
            storedVirtual != null
        } else {
            backend == "saf" || safUri != null || storedVirtual != null
        }
        val rootPath = if (useSaf && safUri != null) {
            SafVirtualPath.rootForUri(safUri)
        } else if (useSaf) {
            // Keep a malformed SAF configuration visibly in the SAF backend so
            // validation can report URI_INVALID instead of treating it as a
            // physical path.
            storedPath.ifBlank { "saf://invalid" }
        } else {
            storedPath
        }
        return VaultStorageInfo(
            backend = if (useSaf) VaultBackend.SAF else VaultBackend.FILE,
            rootPath = rootPath,
            treeUri = if (useSaf) {
                treeUri.ifBlank { safUri?.toString().orEmpty() }
            } else "",
            displayName = prefs.getString(DISPLAY_NAME_KEY, "").orEmpty().trim(),
        )
    }

    fun saveSaf(context: Context, uri: Uri, displayName: String? = null): String? {
        val root = DocumentFile.fromTreeUri(context, uri) ?: return null
        val name = displayName?.trim().orEmpty().ifBlank { root.name.orEmpty() }
        val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        }.onFailure {
            BetaLogger.logException("VaultStorage", "persist_permission_failed uri=$uri", it)
        }
        val rootPath = SafVirtualPath.rootForUri(uri)
        QuickDailyConfigMutationLock.withLock {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(BACKEND_KEY, "saf")
                .putString(TREE_URI_KEY, uri.toString())
                .putString(DISPLAY_NAME_KEY, name)
                .putString("vault_path", rootPath)
                .apply()
        }
        BetaLogger.log(
            "VaultStorage",
            "configured backend=saf treeUri=$uri root=$rootPath displayName=$name canRead=${root.canRead()} canWrite=${root.canWrite()}",
        )
        return rootPath
    }

    fun saveFile(context: Context, path: String, displayName: String? = null) {
        val cleanPath = path.trim()
        QuickDailyConfigMutationLock.withLock {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(BACKEND_KEY, "file")
                .remove(TREE_URI_KEY)
                .putString(DISPLAY_NAME_KEY, displayName.orEmpty().ifBlank { File(cleanPath).name })
                .putString("vault_path", cleanPath)
                .apply()
        }
        BetaLogger.log("VaultStorage", "configured backend=file path=$cleanPath")
    }

    fun isLegacyHomePath(path: String): Boolean =
        path.trim().replace('\\', '/').startsWith("/storage/home", ignoreCase = true)

    fun validate(context: Context): VaultValidationResult {
        val info = current(context)
        if (info.rootPath.isBlank()) {
            return VaultValidationResult(
                VaultValidationStatus.NOT_CONFIGURED,
                UiText.Resource(R.string.qd_vault_not_configured),
                info,
            )
        }
        if (info.backend == VaultBackend.FILE && isLegacyHomePath(info.rootPath)) {
            return VaultValidationResult(
                VaultValidationStatus.LEGACY_PATH_RESELECT,
                UiText.Resource(R.string.qd_vault_legacy_path),
                info,
            )
        }
        if (info.backend == VaultBackend.SAF) {
            val uri = runCatching { Uri.parse(info.treeUri) }.getOrNull()
            val root = uri?.let { DocumentFile.fromTreeUri(context, it) }
            return when {
                uri == null || root == null -> VaultValidationResult(
                    VaultValidationStatus.URI_INVALID,
                    UiText.Resource(R.string.qd_vault_saf_uri_invalid),
                    info,
                )
                !root.exists() -> VaultValidationResult(
                    VaultValidationStatus.DIRECTORY_NOT_FOUND,
                    UiText.Resource(R.string.qd_vault_saf_directory_missing),
                    info,
                )
                !root.canRead() -> VaultValidationResult(
                    VaultValidationStatus.NOT_READABLE,
                    UiText.Resource(R.string.qd_vault_saf_not_readable),
                    info,
                )
                !root.canWrite() -> VaultValidationResult(
                    VaultValidationStatus.NOT_WRITABLE,
                    UiText.Resource(R.string.qd_vault_saf_not_writable),
                    info,
                )
                else -> VaultValidationResult(
                    VaultValidationStatus.VALID,
                    UiText.Resource(R.string.qd_vault_accessible),
                    info,
                )
            }
        }
        val directory = File(info.rootPath)
        return when {
            !directory.exists() -> VaultValidationResult(
                VaultValidationStatus.DIRECTORY_NOT_FOUND,
                UiText.Resource(R.string.qd_vault_directory_missing),
                info,
            )
            !directory.isDirectory -> VaultValidationResult(
                VaultValidationStatus.DIRECTORY_NOT_FOUND,
                UiText.Resource(R.string.qd_vault_not_directory),
                info,
            )
            !directory.canRead() -> VaultValidationResult(
                VaultValidationStatus.NOT_READABLE,
                UiText.Resource(R.string.qd_vault_not_readable),
                info,
            )
            !directory.canWrite() -> VaultValidationResult(
                VaultValidationStatus.NOT_WRITABLE,
                UiText.Resource(R.string.qd_vault_not_writable),
                info,
            )
            else -> VaultValidationResult(
                VaultValidationStatus.VALID,
                UiText.Resource(R.string.qd_vault_accessible),
                info,
            )
        }
    }

    fun displayName(context: Context): String {
        val info = current(context)
        if (info.displayName.isNotBlank()) return info.displayName
        return when (info.backend) {
            VaultBackend.FILE -> File(info.rootPath).name
            VaultBackend.SAF -> DocumentFile.fromTreeUri(context, Uri.parse(info.treeUri))?.name.orEmpty()
        }
    }

    /** Convert a picked document/folder URI to a vault-relative virtual path. */
    fun virtualPathForDocumentUri(context: Context, vaultPath: String, uri: Uri): String? {
        val vault = SafVirtualPath.parse(vaultPath) ?: return null
        if (!uri.authority.equals(vault.rootUri.authority, ignoreCase = true)) return null
        val vaultDocumentId = runCatching {
            DocumentsContract.getTreeDocumentId(vault.rootUri)
        }.getOrNull() ?: return null
        val documentId = runCatching {
            DocumentsContract.getDocumentId(uri)
        }.getOrNull() ?: return null
        if (documentId == vaultDocumentId) return vault.rootPath
        val prefix = "$vaultDocumentId/"
        if (!documentId.startsWith(prefix)) return null
        val relative = documentId.removePrefix(prefix)
        return SafVirtualPath.join(vault.rootPath, relative)
    }

    fun relativePathForTreeUri(vaultPath: String, treeUri: Uri): String? {
        val vault = SafVirtualPath.parse(vaultPath) ?: return null
        if (!treeUri.authority.equals(vault.rootUri.authority, ignoreCase = true)) return null
        val vaultId = runCatching { DocumentsContract.getTreeDocumentId(vault.rootUri) }.getOrNull() ?: return null
        val selectedId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return null
        if (selectedId == vaultId) return ""
        val prefix = "$vaultId/"
        return selectedId.removePrefix(prefix).takeIf { selectedId.startsWith(prefix) }
    }
}

data class VaultFileMetadata(
    val sourcePath: String,
    val relativePath: String,
    val displayName: String,
    val isDirectory: Boolean,
    val creationTime: Long?,
    val lastModified: Long,
)

class VaultStorageException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Unified file operations for both physical files and SAF documents. */
object VaultStorage {
    private const val MAX_SCAN_DEPTH = 50

    internal fun documentForPath(context: Context, path: String): DocumentFile? {
        SafDocumentPath.parse(path)?.let { exact ->
            if (!SafDocumentPath.isWithinTree(exact)) return null
            val document = DocumentFile.fromSingleUri(context, exact.documentUri) ?: return null
            val currentId = runCatching { DocumentsContract.getDocumentId(document.uri) }.getOrNull()
            return document.takeIf {
                currentId == exact.documentId && it.exists()
            }
        }
        val parsed = SafVirtualPath.parse(path) ?: return null
        val root = DocumentFile.fromTreeUri(context, parsed.rootUri) ?: return null
        return resolveDocument(root, parsed.relativePath)
    }

    internal fun readText(context: Context, path: String): String? {
        val document = documentForPath(context, path) ?: return null
        return runCatching {
            context.contentResolver.openInputStream(document.uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        }.getOrNull()
    }

    internal fun writeText(context: Context, path: String, content: String): Boolean {
        SafDocumentPath.parse(path)?.let { exact ->
            val target = documentForPath(context, path)
                ?.takeIf { it.isFile && it.canWrite() }
                ?: return false
            return try {
                context.contentResolver.openOutputStream(target.uri, "w")?.use { output ->
                    content.toByteArray(Charsets.UTF_8).inputStream().use { input -> input.copyTo(output) }
                } != null
            } catch (error: Exception) {
                BetaLogger.logException("VaultStorage", "saf_exact_write_failed path=$path", error)
                false
            }
        }
        val parsed = SafVirtualPath.parse(path) ?: return false
        val relative = parsed.relativePath
        if (relative.isBlank()) return false
        val pieces = relative.split('/')
        val fileName = pieces.last()
        val parentRelative = pieces.dropLast(1).joinToString("/")
        val root = DocumentFile.fromTreeUri(context, parsed.rootUri) ?: return false
        val parent = ensureDirectory(root, parentRelative) ?: return false
        val target = parent.findFile(fileName)
        val tempName = ".${fileName}.${System.nanoTime()}.quickdaily.tmp"
        val temporary = parent.createFile("text/plain", tempName) ?: return false
        return try {
            val wrote = context.contentResolver.openOutputStream(temporary.uri, "w")?.use { output ->
                content.toByteArray(Charsets.UTF_8).inputStream().use { input -> input.copyTo(output) }
                true
            } == true
            if (!wrote) return false
            fun copyTemporaryTo(destination: DocumentFile): Boolean =
                context.contentResolver.openInputStream(temporary.uri)?.use { input ->
                    context.contentResolver.openOutputStream(destination.uri, "w")?.use { output ->
                        input.copyTo(output)
                        true
                    } ?: false
                } == true

            if (target == null) {
                if (temporary.renameTo(fileName)) {
                    true
                } else {
                    val replacement = parent.createFile("text/plain", fileName) ?: return false
                    val copied = copyTemporaryTo(replacement)
                    if (!copied) replacement.delete()
                    copied
                }
            } else {
                // Keep the existing document recoverable while the provider
                // performs its rename. Some providers do not support rename;
                // only then fall back to writing the existing document.
                val backupName = ".${fileName}.${System.nanoTime()}.quickdaily.bak"
                if (target.renameTo(backupName)) {
                    if (temporary.renameTo(fileName)) {
                        parent.findFile(backupName)?.delete()
                        true
                    } else {
                        val replacement = parent.createFile("text/plain", fileName)
                        val copied = replacement?.let(::copyTemporaryTo) == true
                        if (copied) {
                            parent.findFile(backupName)?.delete()
                        } else {
                            replacement?.delete()
                            parent.findFile(backupName)?.renameTo(fileName)
                        }
                        copied
                    }
                } else {
                    BetaLogger.log("VaultStorage", "saf_write_direct_fallback path=$path")
                    copyTemporaryTo(target)
                }
            }
        } catch (error: Exception) {
            BetaLogger.logException("VaultStorage", "saf_write_failed path=$path", error)
            false
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    internal fun exists(context: Context, path: String): Boolean =
        documentForPath(context, path)?.exists() == true

    internal fun canWrite(context: Context, path: String): Boolean =
        documentForPath(context, path)?.canWrite() == true

    internal fun isDirectory(context: Context, path: String): Boolean =
        documentForPath(context, path)?.isDirectory == true

    internal fun lastModified(context: Context, path: String): Long =
        documentForPath(context, path)?.lastModified() ?: 0L

    internal fun fingerprint(context: Context, path: String): FileFingerprint? {
        val document = documentForPath(context, path) ?: return FileFingerprint(false, 0L, "", 0L)
        return runCatching {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            var length = 0L
            context.contentResolver.openInputStream(document.uri)?.use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    length += read
                    digest.update(buffer, 0, read)
                }
            } ?: return null
            FileFingerprint(
                exists = true,
                length = length,
                sha256 = digest.digest().joinToString("") { byte -> "%02x".format(byte) },
                lastModified = document.lastModified(),
            )
        }.getOrNull()
    }

    fun listMarkdownFiles(context: Context, rootPath: String, recursive: Boolean): List<VaultFileMetadata> {
        val saf = SafVirtualPath.parse(rootPath)
        if (saf != null) return listSaf(context, saf, recursive)
        val root = File(rootPath)
        if (!root.isDirectory) throw VaultStorageException("仓库目录不可读：$rootPath") // localization-legacy: internal scan diagnostic
        val result = mutableListOf<VaultFileMetadata>()
        walkPhysical(root, root, recursive, 0, result)
        return result
    }

    fun listMarkdownFilesFromTree(
        context: Context,
        treeUri: Uri,
        recursive: Boolean,
    ): List<VaultFileMetadata> {
        val rootPath = SafVirtualPath.rootForUri(treeUri)
        val parsed = SafVirtualPath.parse(rootPath) ?: throw VaultStorageException("文件夹 URI 无效") // localization-legacy: internal scan diagnostic
        return listSaf(context, parsed, recursive)
    }

    fun folderDisplayName(context: Context, treeUri: Uri): String =
        DocumentFile.fromTreeUri(context, treeUri)?.name.orEmpty().ifBlank {
            treeUri.lastPathSegment.orEmpty().substringAfterLast('/').ifBlank {
                LocaleController.localizedContext(context).getString(R.string.qd_widget_custom_folder_name)
            }
        }

    fun copyUriToPath(context: Context, sourceUri: Uri, destinationPath: String): Boolean =
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            copyInputStreamToPath(context, input, destinationPath)
        } ?: false

    fun copyLocalFileToPath(context: Context, sourceFile: File, destinationPath: String): Boolean =
        sourceFile.inputStream().use { input -> copyInputStreamToPath(context, input, destinationPath) }

    private fun copyInputStreamToPath(
        context: Context,
        input: java.io.InputStream,
        destinationPath: String,
    ): Boolean {
        val parsed = SafVirtualPath.parse(destinationPath)
        if (parsed != null) {
            val pieces = parsed.relativePath.split('/').filter(String::isNotBlank)
            if (pieces.isEmpty()) return false
            val root = DocumentFile.fromTreeUri(context, parsed.rootUri) ?: return false
            val parent = ensureDirectory(root, pieces.dropLast(1).joinToString("/")) ?: return false
            val fileName = pieces.last()
            val target = parent.findFile(fileName) ?: parent.createFile("application/octet-stream", fileName)
                ?: return false
            return runCatching {
                context.contentResolver.openOutputStream(target.uri, "w")?.use { output ->
                    input.copyTo(output)
                    true
                } ?: false
            }.getOrDefault(false)
        }
        return runCatching {
            val destination = File(destinationPath)
            destination.parentFile?.mkdirs()
            destination.outputStream().use { output -> input.copyTo(output) }
            true
        }.getOrDefault(false)
    }

    private fun resolveDocument(root: DocumentFile, relativePath: String): DocumentFile? {
        if (relativePath.isBlank()) return root
        var current = root
        relativePath.split('/').forEach { name ->
            current = current.findFile(name) ?: return null
        }
        return current
    }

    private fun ensureDirectory(root: DocumentFile, relativePath: String): DocumentFile? {
        if (relativePath.isBlank()) return root
        var current = root
        relativePath.split('/').forEach { name ->
            current = current.findFile(name)?.takeIf { it.isDirectory }
                ?: current.createDirectory(name)
                ?: return null
        }
        return current
    }

    private fun listSaf(
        context: Context,
        root: SafVirtualPath.Parsed,
        recursive: Boolean,
    ): List<VaultFileMetadata> {
        val documentRoot = DocumentFile.fromTreeUri(context, root.rootUri)
            ?: throw VaultStorageException("SAF 文件夹不可访问：${root.rootUri}") // localization-legacy: internal scan diagnostic
        val selectedRoot = resolveDocument(documentRoot, root.relativePath)
            ?: throw VaultStorageException("SAF 文件夹不存在：${root.relativePath}") // localization-legacy: internal scan diagnostic
        if (!selectedRoot.exists() || !selectedRoot.isDirectory) {
            throw VaultStorageException("SAF 文件夹不是目录：${root.relativePath}") // localization-legacy: internal scan diagnostic
        }
        val result = mutableListOf<VaultFileMetadata>()
        walkSaf(selectedRoot, root.rootUri, "", recursive, 0, result)
        return result
    }

    private fun walkSaf(
        directory: DocumentFile,
        treeUri: Uri,
        relativeDirectory: String,
        recursive: Boolean,
        depth: Int,
        result: MutableList<VaultFileMetadata>,
    ) {
        if (depth > MAX_SCAN_DEPTH) {
            throw VaultStorageException("SAF 文件夹层级超过限制：$MAX_SCAN_DEPTH") // localization-legacy: internal scan diagnostic
        }
        val children = try {
            directory.listFiles()
        } catch (error: Exception) {
            throw VaultStorageException("SAF 子文件夹不可访问", error) // localization-legacy: internal scan diagnostic
        }
        children.forEach { child ->
            val name = child.name.orEmpty()
            if (name.isBlank() || name.startsWith(".")) return@forEach
            val relative = listOf(relativeDirectory, name).filter(String::isNotBlank).joinToString("/")
            if (child.isDirectory) {
                if (recursive) walkSaf(child, treeUri, relative, true, depth + 1, result)
            } else if (name.endsWith(".md", ignoreCase = true)) {
                val path = SafDocumentPath.forDocumentUri(child.uri, treeUri)
                    ?: throw VaultStorageException("SAF 文件身份不可用：$relative") // localization-legacy: internal scan diagnostic
                val lastModified = child.lastModified()
                // DocumentsContract does not expose a portable creation-time
                // column through DocumentFile, so the caller intentionally
                // records this as a modification-time fallback.
                result += VaultFileMetadata(path, relative, name, false, null, lastModified)
            }
        }
    }

    private fun walkPhysical(
        root: File,
        directory: File,
        recursive: Boolean,
        depth: Int,
        result: MutableList<VaultFileMetadata>,
    ) {
        if (depth > MAX_SCAN_DEPTH) return
        val children = directory.listFiles()
            ?: throw VaultStorageException("文件夹不可访问：${directory.path}") // localization-legacy: internal scan diagnostic
        children.forEach { child ->
            if (child.isHidden) return@forEach
            val relative = runCatching { root.toPath().relativize(child.toPath()).toString().replace(File.separatorChar, '/') }
                .getOrNull() ?: return@forEach
            if (child.isDirectory) {
                if (recursive) walkPhysical(root, child, true, depth + 1, result)
            } else if (child.name.endsWith(".md", ignoreCase = true)) {
                val creationTime = runCatching {
                    Files.readAttributes(child.toPath(), BasicFileAttributes::class.java).creationTime().toMillis()
                }.getOrNull()?.takeIf { it > 0L }
                result += VaultFileMetadata(
                    sourcePath = child.path,
                    relativePath = relative,
                    displayName = child.name,
                    isDirectory = false,
                    creationTime = creationTime,
                    lastModified = child.lastModified(),
                )
            }
        }
    }
}
