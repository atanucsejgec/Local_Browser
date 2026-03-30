package com.webwrap.app.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebView
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * DownloadHelper — Handles file downloads via Android DownloadManager
 * and offline website saving via WebView.saveWebArchive().
 */
object DownloadHelper {

    /**
     * Download a file from URL using Android DownloadManager.
     * Called from WebView's DownloadListener.
     */
    fun downloadFile(
        context: Context,
        url: String,
        contentDisposition: String?,
        mimeType: String?,
        userAgent: String?
    ) {
        try {
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(fileName)
                setDescription("Downloading...")
                setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS, fileName
                )
                // Attach cookies so authenticated downloads work
                val cookies = CookieManager.getInstance().getCookie(url)
                if (!cookies.isNullOrEmpty()) {
                    addRequestHeader("Cookie", cookies)
                }
                if (!userAgent.isNullOrEmpty()) {
                    addRequestHeader("User-Agent", userAgent)
                }
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            Toast.makeText(context, "⬇️ Downloading: $fileName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Save current webpage as MHTML archive for offline viewing.
     * Saved to Downloads/WebWrap_Offline/ folder.
     */
    fun saveWebsiteOffline(context: Context, webView: WebView?) {
        if (webView == null) {
            Toast.makeText(context, "No page to save", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "WebWrap_Offline"
            )
            if (!dir.exists()) dir.mkdirs()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val title = webView.title?.take(30)?.replace(Regex("[^a-zA-Z0-9]"), "_") ?: "page"
            val filePath = File(dir, "${title}_$timestamp.mht").absolutePath

            webView.saveWebArchive(filePath)
            Toast.makeText(context, "📥 Saved offline: $filePath", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}