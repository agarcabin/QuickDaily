package com.quickdaily.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.quickdaily.R
import com.quickdaily.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException
import java.net.SocketTimeoutException

data class ReleaseInfo(
    val version: String,
    val body: String,
    val apkUrl: String,
    val releaseUrl: String
)

/**
 * 检查更新的详细结果。
 */
sealed class UpdateResult {
    /** 正在尝试某个镜像源 */
    data class Progress(val message: UiText) : UpdateResult()

    /** 发现新版本 */
    data class UpdateAvailable(val info: ReleaseInfo) : UpdateResult()

    /** 当前已是最新版本 */
    object UpToDate : UpdateResult()

    /**
     * 失败。
     * @param errors 每个镜像源的失败原因
     */
    data class Failed(val errors: List<SourceError>) : UpdateResult()
}

data class SourceError(
    val source: UiText,
    val reason: UiText,
)

object UpdateChecker {
    private const val REPO_OWNER = "agarcabin"
    private const val REPO_NAME = "QuickDaily"

    private const val PREF_NAME = "QuickDaily"
    private const val PREF_LAST_SUCCESS_MIRROR = "last_success_mirror"
    private const val PREF_LAST_SUCCESS_TYPE = "last_success_type"  // "github" or "jsdelivr"

    /** 镜像源配置：名称 → URL */
    private data class Mirror(val name: UiText, val url: String, val type: String)

    private val ALL_MIRRORS = listOf(
        Mirror(UiText.Resource(R.string.qd_settings_update_mirror_proxy_1), "https://ghproxy.com/https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest", "github"),
        Mirror(UiText.Resource(R.string.qd_settings_update_mirror_proxy_2), "https://mirror.ghproxy.com/https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest", "github"),
        Mirror(UiText.Resource(R.string.qd_settings_update_mirror_proxy_3), "https://github.moeyy.xyz/https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest", "github"),
        Mirror(UiText.Resource(R.string.qd_settings_update_mirror_github), "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest", "github"),
        Mirror(UiText.Resource(R.string.qd_settings_update_mirror_jsdelivr_primary), "https://cdn.jsdelivr.net/gh/$REPO_OWNER/$REPO_NAME@main/version.json", "jsdelivr"),
        Mirror(UiText.Resource(R.string.qd_settings_update_mirror_jsdelivr_backup), "https://fastly.jsdelivr.net/gh/$REPO_OWNER/$REPO_NAME@main/version.json", "jsdelivr")
    )

    /**
     * 获取排序后的镜像源列表：上次成功的镜像源排在最前。
     */
    private fun getOrderedMirrors(context: Context?): List<Mirror> {
        if (context == null) return ALL_MIRRORS

        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val lastUrl = prefs.getString(PREF_LAST_SUCCESS_MIRROR, null) ?: return ALL_MIRRORS

        // 找到上次成功的镜像，放到最前面
        val lastMirror = ALL_MIRRORS.find { it.url == lastUrl } ?: return ALL_MIRRORS
        return listOf(lastMirror) + ALL_MIRRORS.filter { it.url != lastUrl }
    }

    /**
     * 记录成功的镜像源到 SharedPreferences。
     */
    private fun saveSuccessMirror(context: Context?, mirror: Mirror) {
        if (context == null) return
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(PREF_LAST_SUCCESS_MIRROR, mirror.url)
            putString(PREF_LAST_SUCCESS_TYPE, mirror.type)
            apply()
        }
    }

    /**
     * 检查更新，通过 onProgress 回调实时反馈每个镜像源的尝试状态。
     *
     * @param context 用于读取/保存上次成功的镜像源（可为 null）
     * @param onProgress 回调（消息文本），在每次尝试新镜像源时触发
     */
suspend fun checkUpdate(
        currentVersion: String,
        context: Context? = null,
    onProgress: (UiText) -> Unit = {}
    ): UpdateResult {
        return withContext(Dispatchers.IO) {
            val errors = mutableListOf<SourceError>()
            val mirrors = getOrderedMirrors(context)
            val total = mirrors.size

            for ((index, mirror) in mirrors.withIndex()) {
                val progressMsg = UiText.Resource(
                    R.string.qd_settings_checking_update_method,
                    listOf(index + 1, total, mirror.name),
                )
                onProgress(progressMsg)

                try {
                    val result = when (mirror.type) {
                        "github" -> tryGitHubApi(mirror.url)
                        else -> tryJsdelivr(mirror.url)
                    }

                    if (result != null) {
                        // 成功获取到版本信息
                        val (version, releaseUrl, body, apkUrl) = result
                        // 记录成功的镜像源，下次优先使用
                        saveSuccessMirror(context, mirror)

                        return@withContext if (version.isNotEmpty() &&
                            isNewerVersion(version, currentVersion)) {
                            UpdateResult.UpdateAvailable(
                                ReleaseInfo(version, body, apkUrl, releaseUrl)
                            )
                        } else {
                            UpdateResult.UpToDate
                        }
                    } else {
                        errors.add(
                            SourceError(
                                source = mirror.name,
                                reason = UiText.Resource(R.string.qd_settings_update_no_valid_data),
                            )
                        )
                    }
                } catch (e: Exception) {
                    errors.add(SourceError(mirror.name, describeException(e)))
                }
            }

            UpdateResult.Failed(errors)
        }
    }

    /**
     * 统一的返回类型：(version, releaseUrl, body, apkUrl)
     */
    private fun tryGitHubApi(urlStr: String): Quadruple<String, String, String, String>? {
        val url = URL(urlStr)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
        connection.setRequestProperty("User-Agent", "QuickDaily-App")
        connection.connectTimeout = 8000
        connection.readTimeout = 8000

        try {
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                return parseReleaseInfo(response)
            }
            return null
        } finally {
            connection.disconnect()
        }
    }

    private fun tryJsdelivr(urlStr: String): Quadruple<String, String, String, String>? {
        val url = URL(urlStr)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 8000
        connection.readTimeout = 8000

        try {
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(response)
                val version = root.optString("version", "")
                if (version.isNotEmpty()) {
                    val releaseUrl = root.optString("url",
                        "https://github.com/$REPO_OWNER/$REPO_NAME/releases/latest")
                val body = root.optString("body", "发现新版本 $version") // localization-legacy: external release-body fallback
                    return Quadruple(version, releaseUrl, body, "")
                }
            }
            return null
        } finally {
            connection.disconnect()
        }
    }

    private fun parseReleaseInfo(json: String): Quadruple<String, String, String, String>? {
        return try {
            val root = JSONObject(json)

            val tagName = root.optString("tag_name", "")
            if (tagName.isEmpty()) return null

            val body = root.optString("body", "")
            val releaseUrl = root.optString("html_url",
                "https://github.com/$REPO_OWNER/$REPO_NAME/releases/latest")

            // 从 assets 数组中取第一个 apk 的下载链接
            val assets = root.optJSONArray("assets")
            var apkUrl = ""
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk")) {
                        apkUrl = asset.optString("browser_download_url", "")
                        break
                    }
                }
                if (apkUrl.isEmpty() && assets.length() > 0) {
                    apkUrl = assets.getJSONObject(0).optString("browser_download_url", "")
                }
            }

            // 如果 APK 下载链接是 GitHub 直链，转换为 ghproxy 加速链接
            if (apkUrl.isNotEmpty() && apkUrl.contains("github.com")) {
                apkUrl = "https://mirror.ghproxy.com/$apkUrl"
            }

            Quadruple(tagName, releaseUrl, body, apkUrl)
        } catch (_: Exception) {
            null
        }
    }

    /** 简易四元组 */
    data class Quadruple<A, B, C, D>(
        val first: A, val second: B, val third: C, val fourth: D
    )

    /**
     * 把异常翻译成用户可读的中文原因。
     */
    private fun describeException(e: Exception): UiText {
        return when (e) {
            is UnknownHostException -> UiText.Resource(R.string.qd_settings_update_dns_failed)
            is SocketTimeoutException -> UiText.Resource(R.string.qd_settings_update_timeout)
            is java.net.ConnectException -> UiText.Resource(R.string.qd_settings_update_connection_refused)
            is javax.net.ssl.SSLException -> UiText.Resource(R.string.qd_settings_update_ssl_failed)
            is org.json.JSONException -> UiText.Resource(R.string.qd_settings_update_response_invalid)
            is java.net.SocketException -> e.message?.let(UiText::Raw)
                ?: UiText.Resource(R.string.qd_settings_update_network_error)
            else -> UiText.Raw(e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * 语义化版本比较：将 "v1.2.3" 或 "1.2.3" 拆为 [1,2,3] 逐段比较。 // localization-legacy: version parsing documentation
     */
    private fun isNewerVersion(remote: String, current: String): Boolean {
        val remoteParts = remote.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val currentParts = current.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r != c) return r > c
        }
        return false
    }

    fun openReleasePage(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }
}
