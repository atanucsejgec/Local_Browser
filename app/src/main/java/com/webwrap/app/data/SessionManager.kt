package com.webwrap.app.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class SavedTab(
    val url: String,
    val title: String
)

data class SavedSession(
    val tabs: List<SavedTab>,
    val activeTabIndex: Int,
    val timestamp: Long = System.currentTimeMillis()
)

object SessionManager {

    private const val PREFS_NAME = "webwrap_session"
    private const val KEY_SESSION = "last_session"
    private val gson = Gson()

    /**
     * Save current browser session (all open tabs)
     */
    fun saveSession(context: Context, tabs: List<SavedTab>, activeIndex: Int) {
        try {
            val session = SavedSession(
                tabs = tabs,
                activeTabIndex = activeIndex
            )
            val json = gson.toJson(session)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SESSION, json)
                .apply()
        } catch (_: Exception) { }
    }

    /**
     * Load previous browser session
     */
    fun loadSession(context: Context): SavedSession? {
        return try {
            val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_SESSION, null) ?: return null

            gson.fromJson(json, SavedSession::class.java)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Clear saved session
     */
    fun clearSession(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_SESSION)
            .apply()
    }

    /**
     * Check if there's a saved session
     */
    fun hasSession(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .contains(KEY_SESSION)
    }
}