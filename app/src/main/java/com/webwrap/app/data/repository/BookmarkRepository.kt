package com.webwrap.app.data.repository

import android.content.Context
import com.webwrap.app.data.BookmarkStorage
import com.webwrap.app.data.SiteBookmark
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton repository for bookmark operations.
 * Shared between HomeViewModel and BrowserViewModel.
 */
class BookmarkRepository(
    private val context: Context
) {
    companion object {
        @Volatile
        private var INSTANCE: BookmarkRepository? = null

        /** Get or create singleton instance. */
        fun getInstance(context: Context): BookmarkRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BookmarkRepository(
                    context.applicationContext
                ).also { INSTANCE = it }
            }
        }
    }

    private val _bookmarks = MutableStateFlow<List<SiteBookmark>>(
        emptyList()
    )
    val bookmarks: StateFlow<List<SiteBookmark>> =
        _bookmarks.asStateFlow()

    init { loadFromDisk() }

    /** Load bookmarks from SharedPreferences. */
    private fun loadFromDisk() {
        _bookmarks.value =
            BookmarkStorage.loadBookmarks(context)
    }

    /** Add a new bookmark and persist. */
    fun addBookmark(bookmark: SiteBookmark) {
        if (_bookmarks.value.none {
                it.url == bookmark.url
            }
        ) {
            _bookmarks.value = _bookmarks.value + bookmark
            saveToDisk()
        }
    }

    /** Delete a bookmark and persist. */
    fun deleteBookmark(bookmark: SiteBookmark) {
        _bookmarks.value = _bookmarks.value.filter {
            it.url != bookmark.url
        }
        saveToDisk()
    }

    /** Persist bookmarks to disk. */
    private fun saveToDisk() {
        BookmarkStorage.saveBookmarks(
            context, _bookmarks.value
        )
    }
}