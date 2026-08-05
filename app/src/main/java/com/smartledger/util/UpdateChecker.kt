package com.smartledger.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 通过 GitHub Releases API 检查更新，并支持应用内下载安装 APK
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

    sealed class CheckResult {
        data class HasUpdate(val info: UpdateInfo) : CheckResult()
        data class UpToDate(val currentVersion: String, val latestTag: String) : CheckResult()
        data class Failed(val message: String) : CheckResult()
    }

    sealed class DownloadResult {
        data class Success(val apkFile: File) : DownloadResult()
        data class Failed(val message: String) : DownloadResult()
        /** 无 APK 附件，需打开网页 */
        data class NeedBrowser(val url: String) : DownloadResult()
    }

    /** 当前安装版本展示文案，如 "v1.0.10" */
    fun currentVersionLabel(context: Context): String {
        val name = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (_: Exception) {
            null
        } ?: "0.0.0"
        return if (name.startsWith("v")) name else "v$name"
    }

    /**
     * 检查是否有新版本（读取 GitHub Releases 的 latest，与本地 versionName 比较）
     */
    suspend fun checkForUpdate(context: Context): UpdateInfo? {
        return when (val result = checkUpdate(context)) {
            is CheckResult.HasUpdate -> result.info
            else -> null
        }
    }

    suspend fun checkUpdate(context: Context): CheckResult = withContext(Dispatchers.IO) {
        val currentLabel = currentVersionLabel(context)
        val local = currentLabel.removePrefix("v")
        try {
            val url = URL(API_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.setRequestProperty("User-Agent", "SmartLedger-Android")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            val code = conn.responseCode
            if (code != 200) {
                val err = try {
                    conn.errorStream?.bufferedReader()?.readText()
                } catch (_: Exception) {
                    null
                }
                conn.disconnect()
                Log.w(TAG, "GitHub API returned $code $err")
                return@withContext CheckResult.Failed("无法连接更新服务（$code），请稍后重试")
            }

            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val json = JSONObject(body)
            val tagName = json.getString("tag_name")           // e.g. "v1.0.3"
            val releaseNotes = stripMarkdown(json.optString("body", ""))
            val htmlUrl = json.getString("html_url")

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

            val remote = tagName.removePrefix("v")
            Log.d(TAG, "local=$local remote=$remote tag=$tagName")

            if (isNewerVersion(local, remote)) {
                CheckResult.HasUpdate(
                    UpdateInfo(
                        versionName = tagName,
                        releaseNotes = releaseNotes,
                        apkUrl = apkUrl,
                        htmlUrl = htmlUrl
                    )
                )
            } else {
                CheckResult.UpToDate(currentLabel, tagName)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Check update failed", e)
            CheckResult.Failed("检查更新失败：${e.message ?: "网络异常"}")
        }
    }

    private fun apkCacheFiles(context: Context, info: UpdateInfo): Triple<File, File, File> {
        val dir = File(context.cacheDir, "apk").apply { mkdirs() }
        val safeName = info.versionName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val outFile = File(dir, "SmartLedger-$safeName.apk")
        val tmp = File(dir, "SmartLedger-$safeName.tmp")
        val meta = File(dir, "SmartLedger-$safeName.len")
        return Triple(outFile, tmp, meta)
    }

    /**
     * 本地未完成下载的进度（0～99）；无缓存返回 0。
     */
    fun partialDownloadPercent(context: Context, info: UpdateInfo): Int {
        if (info.apkUrl.isNullOrBlank()) return 0
        val (_, tmp, meta) = apkCacheFiles(context, info)
        if (!tmp.exists() || tmp.length() <= 0L) return 0
        val total = meta.takeIf { it.exists() }?.readText()?.trim()?.toLongOrNull() ?: return 0
        if (total <= 0L) return 0
        return ((tmp.length() * 100) / total).toInt().coerceIn(0, 99)
    }

    /**
     * 应用内下载 APK 到缓存目录；失败保留 .tmp，下次用 HTTP Range 断点续传。
     * @param onProgress 0～100
     */
    suspend fun downloadApk(
        context: Context,
        info: UpdateInfo,
        onProgress: (Int) -> Unit = {}
    ): DownloadResult = withContext(Dispatchers.IO) {
        val apkUrl = info.apkUrl
        if (apkUrl.isNullOrBlank()) {
            return@withContext DownloadResult.NeedBrowser(info.htmlUrl)
        }
        try {
            val (outFile, tmp, meta) = apkCacheFiles(context, info)

            // 已下过完整包则复用（>1MB）
            if (outFile.exists() && outFile.length() > 1_000_000L) {
                withContext(Dispatchers.Main) { onProgress(100) }
                return@withContext DownloadResult.Success(outFile)
            }

            var existing = if (tmp.exists()) tmp.length().coerceAtLeast(0L) else 0L
            val knownTotal = meta.takeIf { it.exists() }?.readText()?.trim()?.toLongOrNull() ?: 0L
            if (existing > 0L && knownTotal > 0L) {
                val pct = ((existing * 100) / knownTotal).toInt().coerceIn(0, 99)
                withContext(Dispatchers.Main) { onProgress(pct) }
            }

            val conn = openDownloadConnection(apkUrl, if (existing > 0L) existing else null)
            val code = conn.responseCode

            // 416：本地片段无效，清空后整包重下
            if (code == 416 && existing > 0L) {
                conn.disconnect()
                tmp.delete()
                meta.delete()
                existing = 0L
                return@withContext downloadApk(context, info, onProgress)
            }

            if (code !in 200..299) {
                conn.disconnect()
                return@withContext DownloadResult.Failed("下载失败（HTTP $code）")
            }

            val append: Boolean
            val total: Long
            when (code) {
                206 -> {
                    append = true
                    total = parseContentRangeTotal(conn.getHeaderField("Content-Range"))
                        ?: (existing + conn.contentLengthLong.coerceAtLeast(0L))
                }
                else -> {
                    // 200：不支持 Range 或首下；丢弃旧片段从 0 开始
                    if (existing > 0L) {
                        Log.w(TAG, "server returned 200 for Range request, restart full download")
                        tmp.delete()
                        existing = 0L
                    }
                    append = false
                    total = conn.contentLengthLong.coerceAtLeast(0L)
                }
            }

            if (total > 0L) {
                meta.writeText(total.toString())
            }
            if (existing > 0L && total > 0L && existing >= total) {
                conn.disconnect()
                // 本地已完整，直接收尾
                finalizeApk(tmp, outFile, meta)
                withContext(Dispatchers.Main) { onProgress(100) }
                return@withContext DownloadResult.Success(outFile)
            }

            var readTotal = existing
            var lastPct = if (total > 0L) {
                ((readTotal * 100) / total).toInt().coerceIn(0, 99)
            } else -1
            if (lastPct >= 0) {
                withContext(Dispatchers.Main) { onProgress(lastPct) }
            }

            try {
                conn.inputStream.use { input ->
                    FileOutputStream(tmp, append).use { output ->
                        val buf = ByteArray(8192)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            readTotal += n
                            if (total > 0L) {
                                val pct = ((readTotal * 100) / total).toInt().coerceIn(0, 100)
                                if (pct != lastPct) {
                                    lastPct = pct
                                    withContext(Dispatchers.Main) { onProgress(pct) }
                                }
                            }
                        }
                        output.flush()
                    }
                }
            } finally {
                conn.disconnect()
            }

            if (total > 0L && tmp.length() < total) {
                val pct = ((tmp.length() * 100) / total).toInt().coerceIn(0, 99)
                return@withContext DownloadResult.Failed("下载中断（已完成 ${pct}%），可点重新下载继续")
            }

            finalizeApk(tmp, outFile, meta)
            withContext(Dispatchers.Main) { onProgress(100) }
            Log.d(TAG, "APK downloaded: ${outFile.absolutePath} size=${outFile.length()}")
            DownloadResult.Success(outFile)
        } catch (e: Exception) {
            Log.e(TAG, "downloadApk failed", e)
            // 保留 .tmp，供下次 Range 续传
            DownloadResult.Failed("下载失败：${e.message ?: "网络异常"}")
        }
    }

    private fun finalizeApk(tmp: File, outFile: File, meta: File) {
        if (outFile.exists()) outFile.delete()
        if (!tmp.renameTo(outFile)) {
            tmp.copyTo(outFile, overwrite = true)
            tmp.delete()
        }
        if (meta.exists()) meta.delete()
    }

    /** Content-Range: bytes 0-99/1234 → 1234 */
    private fun parseContentRangeTotal(header: String?): Long? {
        if (header.isNullOrBlank()) return null
        val slash = header.lastIndexOf('/')
        if (slash < 0 || slash >= header.length - 1) return null
        val totalPart = header.substring(slash + 1).trim()
        if (totalPart == "*") return null
        return totalPart.toLongOrNull()?.takeIf { it > 0L }
    }

    private fun openDownloadConnection(apkUrl: String, resumeFrom: Long?): HttpURLConnection {
        var currentUrl = apkUrl
        // 跟随重定向（GitHub → objects.githubusercontent.com）
        repeat(5) {
            val conn = URL(currentUrl).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", "SmartLedger-Android")
            conn.setRequestProperty("Accept", "application/octet-stream")
            if (resumeFrom != null && resumeFrom > 0L) {
                conn.setRequestProperty("Range", "bytes=$resumeFrom-")
            }
            conn.connectTimeout = 15000
            conn.readTimeout = 60000
            val code = conn.responseCode
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location")
                conn.disconnect()
                if (loc.isNullOrBlank()) error("下载重定向失败")
                currentUrl = loc
            } else {
                return conn
            }
        }
        error("下载重定向次数过多")
    }

    /**
     * 调起系统安装界面。若未授权「安装未知应用」，先跳转设置。
     * @return true 已调起安装；false 需用户先开权限
     */
    fun installApk(context: Context, apkFile: File): Boolean {
        if (!apkFile.exists()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                openUnknownSourcesSettings(context)
                return false
            }
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
        return true
    }

    fun openUnknownSourcesSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    /**
     * 打开下载页面（无 APK 附件时的兜底）
     */
    fun openDownloadPage(context: Context, info: UpdateInfo) {
        val uri = Uri.parse(info.apkUrl ?: info.htmlUrl)
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

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
}
