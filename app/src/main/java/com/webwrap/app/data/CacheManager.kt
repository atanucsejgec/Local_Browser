package com.webwrap.app.data

import android.content.Context
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import java.io.File

object CacheManager {

    private const val PREFS_NAME = "webwrap_cache_manager"
    private const val KEY_LAST_CLEANUP = "last_cleanup"
    private const val KEY_LAST_DEEP_CLEANUP = "last_deep_cleanup"

    // Max cache sizes
    private const val MAX_CACHE_MB = 100L         // 100 MB cache limit
    private const val MAX_TOTAL_DATA_MB = 200L    // 200 MB total data limit
    private const val CLEANUP_INTERVAL_MS = 24 * 60 * 60 * 1000L        // 24 hours
    private const val DEEP_CLEANUP_INTERVAL_MS = 7 * 24 * 60 * 60 * 1000L // 7 days

    /**
     * Call this on every app start
     * Checks if cleanup is needed and performs it
     */
    fun performStartupCleanup(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()

        // ── Daily light cleanup ──────────────────────────
        val lastCleanup = prefs.getLong(KEY_LAST_CLEANUP, 0)
        if (now - lastCleanup > CLEANUP_INTERVAL_MS) {
            lightCleanup(context)
            prefs.edit().putLong(KEY_LAST_CLEANUP, now).apply()
        }

        // ── Weekly deep cleanup ──────────────────────────
        val lastDeepCleanup = prefs.getLong(KEY_LAST_DEEP_CLEANUP, 0)
        if (now - lastDeepCleanup > DEEP_CLEANUP_INTERVAL_MS) {
            deepCleanup(context)
            prefs.edit().putLong(KEY_LAST_DEEP_CLEANUP, now).apply()
        }

        // ── Size-based cleanup ───────────────────────────
        val totalSizeMB = getTotalDataSize(context) / (1024 * 1024)
        if (totalSizeMB > MAX_TOTAL_DATA_MB) {
            deepCleanup(context)
        }
    }

    /**
     * Light cleanup — removes only unnecessary data
     * Keeps: cookies (logins), essential cache
     * Removes: old cache files, temp files
     */
    private fun lightCleanup(context: Context) {
        try {
            // Clear WebView cache (not cookies)
            val cacheDir = context.cacheDir
            val cacheSizeMB = getDirSize(cacheDir) / (1024 * 1024)

            if (cacheSizeMB > MAX_CACHE_MB) {
                // Delete old cache files (older than 3 days)
                val threeDaysAgo = System.currentTimeMillis() - (3 * 24 * 60 * 60 * 1000L)
                deleteOldFiles(cacheDir, threeDaysAgo)
            }

            // Clear app-specific temp directory
            val tempDir = File(context.cacheDir, "WebView")
            if (tempDir.exists()) {
                val tempSizeMB = getDirSize(tempDir) / (1024 * 1024)
                if (tempSizeMB > 50) {
                    val oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000L)
                    deleteOldFiles(tempDir, oneDayAgo)
                }
            }

            // Clear code cache
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val codeCache = context.codeCacheDir
                if (codeCache.exists()) {
                    val codeCacheSizeMB = getDirSize(codeCache) / (1024 * 1024)
                    if (codeCacheSizeMB > 20) {
                        deleteOldFiles(codeCache, System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L))
                    }
                }
            }
        } catch (_: Exception) { }
    }

    /**
     * Deep cleanup — aggressive size reduction
     * Keeps: cookies (logins), bookmarks, session
     * Removes: all cache, web storage, databases
     */
    private fun deepCleanup(context: Context) {
        try {
            // Clear WebView cache
            clearWebViewCache(context)

            // Clear web storage (localStorage)
            // NOTE: This will clear some site preferences but NOT cookies
            WebStorage.getInstance().deleteAllData()

            // Clear old cache directories
            clearOldCacheFiles(context)

        } catch (_: Exception) { }
    }

    /**
     * Clear WebView cache without affecting cookies
     */
    fun clearWebViewCache(context: Context) {
        try {
            // Clear the cache directory
            val webViewCacheDir = File(context.cacheDir, "WebView")
            if (webViewCacheDir.exists()) {
                deleteDirectory(webViewCacheDir)
            }

            // Clear HTTP cache
            val httpCacheDir = File(context.cacheDir, "http_cache")
            if (httpCacheDir.exists()) {
                deleteDirectory(httpCacheDir)
            }

            // Also clear app_webview directory in files
            val appWebViewDir = File(context.filesDir.parent ?: "", "app_webview")
            if (appWebViewDir.exists()) {
                // Only delete cache subdirectories, keep cookies
                val cacheSubDirs = listOf("Cache", "GPUCache", "blob_storage", "Session Storage")
                cacheSubDirs.forEach { dirName ->
                    val dir = File(appWebViewDir, dirName)
                    if (dir.exists()) {
                        deleteDirectory(dir)
                    }
                }
            }
        } catch (_: Exception) { }
    }

    /**
     * Clear expired cookies (keeps active login cookies)
     */
    fun clearExpiredCookies() {
        try {
            val cookieManager = CookieManager.getInstance()
            cookieManager.removeExpiredCookie()
            cookieManager.flush()
        } catch (_: Exception) { }
    }

    /**
     * Get total data size used by app
     */
    fun getTotalDataSize(context: Context): Long {
        var total = 0L
        try {
            total += getDirSize(context.cacheDir)
            total += getDirSize(context.filesDir)

            val dataDir = context.filesDir.parentFile
            if (dataDir != null && dataDir.exists()) {
                total += getDirSize(dataDir)
            }
        } catch (_: Exception) { }
        return total
    }

    /**
     * Get formatted data size string
     */
    fun getFormattedDataSize(context: Context): String {
        val bytes = getTotalDataSize(context)
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${"%.1f".format(bytes / (1024.0 * 1024 * 1024))} GB"
        }
    }

    // ── File utility functions ───────────────────────────

    private fun getDirSize(dir: File): Long {
        var size = 0L
        try {
            if (dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    size += if (file.isDirectory) getDirSize(file) else file.length()
                }
            } else {
                size = dir.length()
            }
        } catch (_: Exception) { }
        return size
    }

    private fun deleteOldFiles(dir: File, olderThan: Long) {
        try {
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    deleteOldFiles(file, olderThan)
                    if (file.listFiles()?.isEmpty() == true) {
                        file.delete()
                    }
                } else if (file.lastModified() < olderThan) {
                    file.delete()
                }
            }
        } catch (_: Exception) { }
    }

    private fun deleteDirectory(dir: File) {
        try {
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    deleteDirectory(file)
                }
                file.delete()
            }
            dir.delete()
        } catch (_: Exception) { }
    }

    private fun clearOldCacheFiles(context: Context) {
        try {
            val cacheDir = context.cacheDir
            val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
            deleteOldFiles(cacheDir, sevenDaysAgo)
        } catch (_: Exception) { }
    }
}