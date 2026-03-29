package com.webwrap.app.data.model

/**
 * Represents a single history entry with
 * real timestamp for accurate "time ago" display.
 */
data class HistoryEntry(
    val url: String,
    val title: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Format timestamp as human-readable "time ago".
     */
    fun getTimeAgo(): String {
        val diff = System.currentTimeMillis() - timestamp
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            seconds < 60 -> "Just now"
            minutes < 2 -> "1 minute ago"
            minutes < 60 -> "$minutes minutes ago"
            hours < 2 -> "1 hour ago"
            hours < 24 -> "$hours hours ago"
            days < 2 -> "Yesterday"
            days < 7 -> "$days days ago"
            else -> "Over a week ago"
        }
    }
}