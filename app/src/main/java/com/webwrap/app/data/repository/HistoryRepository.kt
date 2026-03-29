package com.webwrap.app.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.webwrap.app.data.model.HistoryEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repository for browsing history.
 * Stores real timestamps for accurate time display.
 */
class HistoryRepository(
    private val context: Context
) {
    companion object {
        private const val PREFS = "webwrap_history_v2"
        private const val KEY = "history_entries"
        private const val MAX_ENTRIES = 100
    }

    private val gson = Gson()
    private val _history = MutableStateFlow<List<HistoryEntry>>(
        emptyList()
    )
    val history: StateFlow<List<HistoryEntry>> =
        _history.asStateFlow()

    init {
        loadFromDisk()
    }

    /** Add a URL to history with current timestamp. */
    fun addEntry(url: String, title: String = "") {
        val entry = HistoryEntry(
            url = url,
            title = title,
            timestamp = System.currentTimeMillis()
        )
        _history.value = (_history.value + entry)
            .takeLast(MAX_ENTRIES)
        saveToDisk()
    }

    /** Clear all history. */
    fun clearHistory() {
        _history.value = emptyList()
        saveToDisk()
    }

    /** Get last N history entries, newest first. */
    fun getRecent(count: Int = 5): List<HistoryEntry> {
        return _history.value
            .takeLast(count)
            .reversed()
    }

    /** Load history from SharedPreferences. */
    private fun loadFromDisk() {
        val prefs = context.getSharedPreferences(
            PREFS, Context.MODE_PRIVATE
        )
        val json = prefs.getString(KEY, null)
            ?: return
        val type = object :
            TypeToken<List<HistoryEntry>>() {}.type
        _history.value = try {
            gson.fromJson(json, type)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Save history to SharedPreferences. */
    private fun saveToDisk() {
        val prefs = context.getSharedPreferences(
            PREFS, Context.MODE_PRIVATE
        )
        prefs.edit()
            .putString(KEY, gson.toJson(_history.value))
            .apply()
    }
}