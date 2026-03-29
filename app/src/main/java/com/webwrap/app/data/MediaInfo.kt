package com.webwrap.app.data

data class MediaInfo(
    val title: String = "",
    val artist: String = "",
    val thumbnailUrl: String = "",
    val duration: Long = 0L,
    val currentPosition: Long = 0L,
    val isPlaying: Boolean = false,
    val isLooping: Boolean = false
)