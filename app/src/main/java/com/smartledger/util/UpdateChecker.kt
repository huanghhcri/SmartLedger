package com.smartledger.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 通过 GitHub Releases API 检查更新
 * 无需后端，纯客户端实现
 */
object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val REPO = "huanghhcri/SmartLedger"
    private const val API_URL = "https://api.github.com/repos/$REPO/releases/latest"

    data class UpdateInfo(
        val versionName: String,      // e.g. "v1.0.3"
        val releaseNotes: String,     // 发布说明
        val apkUrl: String?,          // APK 下载链接（如果有）
        val htmlUrl: String           // Release 页面链接
    )

    /**
     * 检查是否有新版本
     * @return UpdateInfo 如果有更新，null 如果已是最新
     */
    suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val currentVersion = context.packageManager
                .getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"

            val url = URL(API_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.setRequestProperty("User-Agent", "SmartLedger-Android")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            if (conn.responseCode != 200) {
                Log.w(TAG, "GitHub API returned ${conn.responseCode}")
                return@withContext null
            }

            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val json = JSONObject(body)
            val tagName = json.getString("tag_name")           // e.g. "v1.0.3"
            val releaseNotes = stripMarkdown(json.optString("body", ""))  // 去掉 Markdown 格式
            val htmlUrl = json.getString("html_url")            // Release 页面

            // 查找 APK 下载链接
            var apkUrl: String? = null
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.getString("name").endsWith(".apk")) {
                        apkUrl = asset.getString("browser_download_url")
                        break
                    }
                }
            }

            // 版本比较：去掉 "v" 前缀后按 . 分段比较
            val remote = tagName.removePrefix("v")
            val local = currentVersion.removePrefix("v")

            if (isNewerVersion(local, remote)) {
                UpdateInfo(
                    versionName = tagName,
                    releaseNotes = releaseNotes,
                    apkUrl = apkUrl,
                    htmlUrl = htmlUrl
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Check update failed", e)
            null
        }
    }

    /**
     * 去掉 Markdown 格式，保留纯文本
     */
    private fun stripMarkdown(md: String): String {
        return md
            .replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
            .replace(Regex("\\*(.+?)\\*"), "$1")
            .replace(Regex("~~(.+?)~~"), "$1")
            .replace(Regex("`(.+?)`"), "$1")
            .replace(Regex("```[\\s\\S]*?```"), "")
            .replace(Regex("!\\[.*?]\\(.*?\\)"), "")
            .replace(Regex("\\[(.+?)]\\(.*?\\)"), "$1")
            .replace(Regex("^[-*+]\\s+", RegexOption.MULTILINE), "• ")
            .replace(Regex("^\\d+\\.\\s+", RegexOption.MULTILINE), "")
            .replace(Regex("^>\\s*", RegexOption.MULTILINE), "")
            .replace(Regex("---+"), "")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    /**
     * 版本比较：1.0.2 vs 1.0.3 → true (remote 更新)
     */
    private fun isNewerVersion(local: String, remote: String): Boolean {
        val localParts = local.split(".").map { it.toIntOrNull() ?: 0 }
        val remoteParts = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(localParts.size, remoteParts.size)
        for (i in 0 until maxLen) {
            val l = localParts.getOrElse(i) { 0 }
            val r = remoteParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }

    /**
     * 打开下载页面（优先 APK，否则 Release 页面）
     */
    fun openDownloadPage(context: Context, info: UpdateInfo) {
        val uri = Uri.parse(info.apkUrl ?: info.htmlUrl)
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
