package com.smartledger.util

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 备份持久化：优先写入公共「下载/SmartLedger」，卸载后仍可扫描恢复。
 */
object BackupStorage {

    private const val TAG = "BackupStorage"
    private const val RELATIVE_DIR = "SmartLedger"
    private const val DOWNLOADS_REL =
        "${Environment.DIRECTORY_DOWNLOADS}/$RELATIVE_DIR"

    data class Entry(
        val fileName: String,
        val dateMs: Long,
        val sizeBytes: Long,
        /** MediaStore Uri；文件路径备份则为 null */
        val contentUri: Uri?,
        val file: File?
    )

    fun appFileDirs(context: Context): List<File> = listOf(
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            RELATIVE_DIR
        ),
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            RELATIVE_DIR
        ),
        File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), RELATIVE_DIR),
        File(context.filesDir, RELATIVE_DIR)
    )

    /**
     * 写入 CSV 文本，返回可分享 Uri（优先 MediaStore Downloads）。
     */
    fun writeCsv(context: Context, fileName: String, csvContent: String): Uri? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                // 同名先删旧（避免多份）
                deleteMediaByName(context, fileName)
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, DOWNLOADS_REL)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        out.write(csvContent.toByteArray(Charsets.UTF_8))
                        out.flush()
                    }
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    Log.d(TAG, "Wrote MediaStore backup: $fileName")
                    return uri
                }
            } catch (e: Exception) {
                Log.w(TAG, "MediaStore write failed, fallback to file", e)
            }
        }

        val file = resolveWritableFile(context, fileName) ?: return null
        FileWriter(file).use { it.write(csvContent) }
        return androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun listBackups(context: Context): List<Entry> {
        val map = linkedMapOf<String, Entry>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val projection = arrayOf(
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.DATE_MODIFIED,
                    MediaStore.MediaColumns.RELATIVE_PATH
                )
                val selection =
                    "(${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? OR ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?) AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
                val args = arrayOf(
                    "%$RELATIVE_DIR%",
                    "%SmartLedger%",
                    "SmartLedger%.csv"
                )
                context.contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    args,
                    "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
                )?.use { c ->
                    val idIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val nameIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val sizeIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                    val modIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                    while (c.moveToNext()) {
                        val name = c.getString(nameIdx) ?: continue
                        if (!name.endsWith(".csv")) continue
                        val id = c.getLong(idIdx)
                        val uri = ContentUris.withAppendedId(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI, id
                        )
                        val entry = Entry(
                            fileName = name,
                            dateMs = c.getLong(modIdx) * 1000L,
                            sizeBytes = c.getLong(sizeIdx),
                            contentUri = uri,
                            file = null
                        )
                        val old = map[name]
                        if (old == null || entry.dateMs > old.dateMs) map[name] = entry
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "MediaStore list failed", e)
            }
        }

        for (dir in appFileDirs(context)) {
            if (!dir.exists()) continue
            dir.listFiles()?.filter {
                it.isFile && it.name.endsWith(".csv") && it.name.startsWith("SmartLedger")
            }?.forEach { f ->
                val entry = Entry(
                    fileName = f.name,
                    dateMs = f.lastModified(),
                    sizeBytes = f.length(),
                    contentUri = null,
                    file = f
                )
                val old = map[f.name]
                if (old == null || entry.dateMs > old.dateMs) map[f.name] = entry
            }
        }

        return map.values.sortedByDescending { it.dateMs }
    }

    fun openReader(context: Context, entry: Entry): BufferedReader? {
        return try {
            when {
                entry.contentUri != null ->
                    BufferedReader(
                        InputStreamReader(
                            context.contentResolver.openInputStream(entry.contentUri),
                            Charsets.UTF_8
                        )
                    )
                entry.file != null && entry.file.exists() ->
                    BufferedReader(InputStreamReader(entry.file.inputStream(), Charsets.UTF_8))
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "openReader failed", e)
            null
        }
    }

    fun findLatest(context: Context): Entry? = listBackups(context).firstOrNull()

    fun formatDate(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(ms))

    fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format(Locale.CHINA, "%.1f MB", bytes / 1024.0 / 1024.0)
    }

    fun countCsvRecords(context: Context, entry: Entry): Int {
        return try {
            openReader(context, entry)?.use { reader ->
                var n = 0
                reader.readLine() // header
                reader.lineSequence().forEach { if (it.isNotBlank()) n++ }
                n
            } ?: 0
        } catch (_: Exception) {
            0
        }
    }

    private fun deleteMediaByName(context: Context, fileName: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        try {
            context.contentResolver.delete(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
                arrayOf(fileName, "%$RELATIVE_DIR%")
            )
        } catch (_: Exception) {
        }
    }

    private fun resolveWritableFile(context: Context, fileName: String): File? {
        for (dir in appFileDirs(context)) {
            try {
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                FileWriter(file, true).use { }
                return file
            } catch (_: Exception) {
            }
        }
        return null
    }
}
