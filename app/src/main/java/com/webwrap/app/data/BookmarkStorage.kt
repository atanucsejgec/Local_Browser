package com.webwrap.app.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object BookmarkStorage {

    private const val PREFS_NAME = "webwrap_bookmarks"
    private const val KEY_BOOKMARKS = "custom_bookmarks"
    private const val KEY_HISTORY = "browse_history"
    private val gson = Gson()

    // ── Save / Load Custom Bookmarks ───────────────────────
    fun saveBookmarks(context: Context, bookmarks: List<SiteBookmark>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(bookmarks)
        prefs.edit().putString(KEY_BOOKMARKS, json).apply()
    }

    fun loadBookmarks(context: Context): List<SiteBookmark> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_BOOKMARKS, null) ?: return emptyList()
        val type = object : TypeToken<List<SiteBookmark>>() {}.type
        return try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ── Save / Load History ────────────────────────────────
    fun saveHistory(context: Context, history: List<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(history.takeLast(50)) // keep last 50
        prefs.edit().putString(KEY_HISTORY, json).apply()
    }

    fun loadHistory(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        val type = object : TypeToken<List<String>>() {}.type
        return try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }
}