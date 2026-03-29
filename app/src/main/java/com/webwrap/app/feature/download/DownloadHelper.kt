package com.webwrap.app.feature.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.widget.Toast

/**
 * Handles file downloads from WebView.
 * Uses Android DownloadManager for reliable downloads.
 */
object DownloadHelper {

    /**
     * Start a download using Android DownloadManager.
     * Called from WebView's setDownloadListener.
     *
     * @param context Application context
     * @param url Download URL
     * @param userAgent User agent string
     * @param contentDisposition Content disposition header
     * @param mimeType MIME type of file
     * @param contentLength File size in bytes
     */
    fun download(
        context: Context,
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimeType: String,
        contentLength: Long
    ) {
        try {
            val fileName = guessFileName(
                url, contentDisposition, mimeType
            )
            val cookie = CookieManager.getInstance()
                .getCookie(url)

            val request = DownloadManager.Request(
                Uri.parse(url)
            ).apply {
                setMimeType(mimeType)
                addRequestHeader("Cookie", cookie)
                addRequestHeader("User-Agent", userAgent)
                setTitle(fileName)
                setDescription("Downloading file...")
                setNotificationVisibility(
                    DownloadManager.Request
                        .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    fileName
                )
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }

            val dm = context.getSystemService(
                Context.DOWNLOAD_SERVICE
            ) as DownloadManager
            dm.enqueue(request)

            Toast.makeText(
                context,
                "⬇️ Downloading: $fileName",
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Download failed: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Guess filename from URL and headers.
     */
    private fun guessFileName(
        url: String,
        contentDisposition: String,
        mimeType: String
    ): String {
        return URLUtil.guessFileName(
            url, contentDisposition, mimeType
        )
    }
}