package com.quickdaily.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.net.HttpURLConnection

data class ReleaseInfo(
    val version: String,
    val body: String,
    val apkUrl: String,
    val releaseUrl: String
)

object UpdateChecker {
    private const val GITHUB_API_URL = "https://api.github.com/repos/agarcabin/QuickDaily/releases/latest"
    private const val CURRENT_VERSION = "1.0.2"

    suspend fun checkUpdate(): ReleaseInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(GITHUB_API_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    parseReleaseInfo(response)
                } else {
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun parseReleaseInfo(json: String): ReleaseInfo? {
        try {
            // 简单的 JSON 解析，避免使用第三方库
            val tagName = json.substringAfter("\"tag_name\":\"").substringBefore("\"")
            val body = json.substringAfter("\"body\":\"").substringBefore("\",\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\\"", "\"")
            val apkUrl = json.substringAfter("\"browser_download_url\":\"").substringBefore("\"")
            val releaseUrl = json.substringAfter("\"html_url\":\"").substringBefore("\",\"")

            if (tagName.isNotEmpty() && tagName > CURRENT_VERSION) {
                return ReleaseInfo(tagName, body, apkUrl, releaseUrl)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun openReleasePage(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    }

    fun openApkDownload(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    }
}
