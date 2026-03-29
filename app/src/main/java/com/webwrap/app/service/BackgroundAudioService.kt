package com.webwrap.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import com.webwrap.app.MainActivity
import com.webwrap.app.R
import com.webwrap.app.data.MediaInfo
import com.webwrap.app.webview.WebViewHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class BackgroundAudioService : Service() {

    companion object {
        const val CHANNEL_ID = "webwrap_media_channel"
        const val NOTIFICATION_ID = 101

        // Action constants
        const val ACTION_PLAY = "com.webwrap.ACTION_PLAY"
        const val ACTION_PAUSE = "com.webwrap.ACTION_PAUSE"
        const val ACTION_NEXT = "com.webwrap.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.webwrap.ACTION_PREVIOUS"
        const val ACTION_STOP = "com.webwrap.ACTION_STOP"
        const val ACTION_REPEAT = "com.webwrap.ACTION_REPEAT"

        private var isRunning = false

        fun isServiceRunning(): Boolean = isRunning

        fun start(context: Context) {
            if (isRunning) return
            val intent = Intent(context, BackgroundAudioService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, BackgroundAudioService::class.java)
            context.stopService(intent)
        }
    }

    // Core
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // MediaSession
    private var mediaSession: MediaSessionCompat? = null

    // Thumbnail cache
    private var lastThumbnailUrl: String = ""
    private var cachedThumbnail: Bitmap? = null
    private var defaultThumbnail: Bitmap? = null

    // Last known media info (persists across polls)
    private var lastMediaInfo: MediaInfo = MediaInfo()

    // ================================================================
    // PERIODIC UPDATE — Keeps audio alive + updates notification
    // ================================================================
    private val mediaUpdateRunnable = object : Runnable {
        override fun run() {
            if (!isRunning || !WebViewHolder.backgroundAudioEnabled) return

            // Check if user manually paused
            WebViewHolder.checkManualPause()

            handler.postDelayed({
                // Force play if not manually paused
                if (!WebViewHolder.isManuallyPaused) {
                    WebViewHolder.forcePlay()
                }

                // Get media info and update notification
                WebViewHolder.getMediaInfo { info ->
                    handler.post { handleMediaInfoUpdate(info) }
                }
            }, 200)

            handler.postDelayed(this, 2500)
        }
    }

    // ================================================================
    // SERVICE LIFECYCLE
    // ================================================================

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        initMediaSession()
        acquireWakeLock()
        defaultThumbnail = createDefaultThumbnail()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> handlePlay()
            ACTION_PAUSE -> handlePause()
            ACTION_NEXT -> handleNext()
            ACTION_PREVIOUS -> handlePrevious()
            ACTION_REPEAT -> handleRepeat()
            ACTION_STOP -> {
                handleStop()
                return START_NOT_STICKY
            }
            else -> {
                // Normal start — show initial notification and begin polling
                val notification = buildNotification(MediaInfo(
                    title = "🎵 Audio Playing",
                    artist = "Tap to return to WebWrap"
                ))
                startForeground(NOTIFICATION_ID, notification)

                WebViewHolder.isManuallyPaused = false
                handler.removeCallbacks(mediaUpdateRunnable)
                handler.postDelayed(mediaUpdateRunnable, 1500)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        handler.removeCallbacks(mediaUpdateRunnable)
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
        releaseWakeLock()
        serviceScope.cancel()
        cachedThumbnail?.recycle()
        defaultThumbnail?.recycle()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ================================================================
    // MEDIA SESSION — For lock screen, Bluetooth, system media controls
    // ================================================================

    private fun initMediaSession() {
        mediaSession = MediaSessionCompat(this, "WebWrapMedia").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { handlePlay() }
                override fun onPause() { handlePause() }
                override fun onSkipToNext() { handleNext() }
                override fun onSkipToPrevious() { handlePrevious() }
                override fun onStop() { handleStop() }
                override fun onSeekTo(pos: Long) {
                    WebViewHolder.seekTo(pos)
                    // Quick update after seek
                    handler.postDelayed({
                        WebViewHolder.getMediaInfo { info ->
                            handler.post { handleMediaInfoUpdate(info) }
                        }
                    }, 500)
                }
            })

            isActive = true
        }
    }

    // ================================================================
    // ACTION HANDLERS
    // ================================================================

    private fun handlePlay() {
        WebViewHolder.playMedia()
        WebViewHolder.isManuallyPaused = false
        // Quick update
        handler.postDelayed({
            WebViewHolder.getMediaInfo { info ->
                handler.post { handleMediaInfoUpdate(info.copy(isPlaying = true)) }
            }
        }, 300)
    }

    private fun handlePause() {
        WebViewHolder.pauseMedia()
        WebViewHolder.isManuallyPaused = true
        // Quick update
        handler.postDelayed({
            WebViewHolder.getMediaInfo { info ->
                handler.post { handleMediaInfoUpdate(info.copy(isPlaying = false)) }
            }
        }, 300)
    }

    private fun handleNext() {
        WebViewHolder.nextTrack()
        WebViewHolder.isManuallyPaused = false
        // Delay for page load, then update
        handler.postDelayed({
            WebViewHolder.getMediaInfo { info ->
                handler.post { handleMediaInfoUpdate(info) }
            }
        }, 1500)
    }

    private fun handlePrevious() {
        WebViewHolder.previousTrack()
        // Delay then update
        handler.postDelayed({
            WebViewHolder.getMediaInfo { info ->
                handler.post { handleMediaInfoUpdate(info) }
            }
        }, 800)
    }

    private fun handleRepeat() {
        WebViewHolder.toggleRepeat()
        // Update notification with new repeat state
        val updatedInfo = lastMediaInfo.copy(isLooping = WebViewHolder.isRepeatEnabled)
        handleMediaInfoUpdate(updatedInfo)
    }

    private fun handleStop() {
        WebViewHolder.backgroundAudioEnabled = false
        WebViewHolder.isManuallyPaused = false
        WebViewHolder.isRepeatEnabled = false
        mediaSession?.isActive = false
        handler.removeCallbacks(mediaUpdateRunnable)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    // ================================================================
    // MEDIA INFO UPDATE — Updates session + notification
    // ================================================================

    private fun handleMediaInfoUpdate(info: MediaInfo) {
        // Keep last known valid info
        val effectiveInfo = if (info.title.isNotEmpty() && info.title != "WebWrap Audio") {
            lastMediaInfo = info
            info
        } else if (lastMediaInfo.title.isNotEmpty() && lastMediaInfo.title != "WebWrap Audio") {
            // Keep old title but update playback state
            lastMediaInfo.copy(
                isPlaying = info.isPlaying,
                currentPosition = if (info.currentPosition > 0) info.currentPosition else lastMediaInfo.currentPosition,
                isLooping = WebViewHolder.isRepeatEnabled
            )
        } else {
            info
        }

        updatePlaybackState(effectiveInfo)
        updateMediaMetadata(effectiveInfo)
        updateNotificationDisplay(effectiveInfo)
    }

    // ================================================================
    // PLAYBACK STATE — Position, play/pause state, available actions
    // ================================================================

    private fun updatePlaybackState(info: MediaInfo) {
        val state = if (info.isPlaying) {
            PlaybackStateCompat.STATE_PLAYING
        } else {
            PlaybackStateCompat.STATE_PAUSED
        }

        val playbackState = PlaybackStateCompat.Builder()
            .setState(state, info.currentPosition, if (info.isPlaying) 1f else 0f)
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_SEEK_TO
            )
            .build()

        mediaSession?.setPlaybackState(playbackState)
    }

    // ================================================================
    // MEDIA METADATA — Title, artist, album art, duration
    // ================================================================

    private fun updateMediaMetadata(info: MediaInfo) {
        val builder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE,
                info.title.ifEmpty { "🎵 WebWrap Audio" })
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST,
                info.artist.ifEmpty { "Playing in background" })
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "WebWrap")
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, info.duration)

        // Add thumbnail bitmap
        val thumbnail = cachedThumbnail ?: defaultThumbnail
        if (thumbnail != null) {
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, thumbnail)
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, thumbnail)
        }

        mediaSession?.setMetadata(builder.build())

        // Download new thumbnail if URL changed
        if (info.thumbnailUrl.isNotEmpty() && info.thumbnailUrl != lastThumbnailUrl) {
            lastThumbnailUrl = info.thumbnailUrl
            serviceScope.launch {
                val bitmap = downloadBitmap(info.thumbnailUrl)
                if (bitmap != null) {
                    cachedThumbnail?.recycle()
                    cachedThumbnail = bitmap
                    // Re-update with new bitmap
                    updateMediaMetadata(info)
                    updateNotificationDisplay(info)
                }
            }
        }
    }

    // ================================================================
    // NOTIFICATION — Build the rich media notification
    // ================================================================

    private fun updateNotificationDisplay(info: MediaInfo) {
        try {
            val notification = buildNotification(info)
            val nm = getSystemService(NotificationManager::class.java)
            nm?.notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) { }
    }

    private fun buildNotification(info: MediaInfo): Notification {
        val isPlaying = info.isPlaying

        // Open app intent
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ---- ACTION BUTTONS ----

        // 0: Previous
        val previousAction = NotificationCompat.Action(
            android.R.drawable.ic_media_previous,
            "Previous",
            createActionPendingIntent(ACTION_PREVIOUS)
        )

        // 1: Play or Pause
        val playPauseAction = if (isPlaying) {
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause,
                "Pause",
                createActionPendingIntent(ACTION_PAUSE)
            )
        } else {
            NotificationCompat.Action(
                android.R.drawable.ic_media_play,
                "Play",
                createActionPendingIntent(ACTION_PLAY)
            )
        }

        // 2: Next
        val nextAction = NotificationCompat.Action(
            android.R.drawable.ic_media_next,
            "Next",
            createActionPendingIntent(ACTION_NEXT)
        )

        // 3: Repeat toggle
        val repeatIcon = if (WebViewHolder.isRepeatEnabled) {
            R.drawable.ic_notif_repeat_active
        } else {
            R.drawable.ic_notif_repeat
        }
        val repeatLabel = if (WebViewHolder.isRepeatEnabled) "Repeat: ON" else "Repeat: OFF"
        val repeatAction = NotificationCompat.Action(
            repeatIcon,
            repeatLabel,
            createActionPendingIntent(ACTION_REPEAT)
        )

        // 4: Stop / Close
        val stopAction = NotificationCompat.Action(
            R.drawable.ic_notif_stop,
            "Stop",
            createActionPendingIntent(ACTION_STOP)
        )

        // ---- BUILD NOTIFICATION ----

        val displayTitle = info.title.ifEmpty { "🎵 Audio Playing" }
        val displayArtist = info.artist.ifEmpty { "WebWrap • Background audio" }
        val thumbnail = cachedThumbnail ?: defaultThumbnail

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(displayTitle)
            .setContentText(displayArtist)
            .setSubText(if (WebViewHolder.isRepeatEnabled) "🔁 Repeat" else null)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setLargeIcon(thumbnail)
            .setContentIntent(openIntent)
            .setDeleteIntent(createActionPendingIntent(ACTION_STOP))
            .setOngoing(isPlaying)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColorized(true)
            .setColor(android.graphics.Color.parseColor("#1A1A2E"))
            // Add all 5 actions
            .addAction(previousAction)   // Index 0
            .addAction(playPauseAction)  // Index 1
            .addAction(nextAction)       // Index 2
            .addAction(repeatAction)     // Index 3
            .addAction(stopAction)       // Index 4
            // MediaStyle — shows 3 actions in compact view
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2) // Previous, Play/Pause, Next
                    .setShowCancelButton(true)
                    .setCancelButtonIntent(createActionPendingIntent(ACTION_STOP))
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .build()
    }

    // ================================================================
    // HELPER — Create PendingIntent for notification actions
    // ================================================================

    private fun createActionPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, BackgroundAudioService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // ================================================================
    // NOTIFICATION CHANNEL
    // ================================================================

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Media Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls for background media playback"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    // ================================================================
    // THUMBNAIL DOWNLOAD
    // ================================================================

    private suspend fun downloadBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.instanceFollowRedirects = true
            connection.doInput = true
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                connection.disconnect()
                return@withContext null
            }

            val inputStream = connection.inputStream
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            connection.disconnect()

            // Scale down if too large (saves memory)
            if (bitmap != null && (bitmap.width > 512 || bitmap.height > 512)) {
                val scale = 512f / maxOf(bitmap.width, bitmap.height)
                val scaled = Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt(),
                    (bitmap.height * scale).toInt(),
                    true
                )
                if (scaled !== bitmap) bitmap.recycle()
                return@withContext scaled
            }

            bitmap
        } catch (e: Exception) {
            null
        }
    }

    // ================================================================
    // DEFAULT THUMBNAIL — Used when no artwork is available
    // ================================================================

    private fun createDefaultThumbnail(): Bitmap {
        val size = 256
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Dark background
        val bgPaint = Paint().apply {
            color = android.graphics.Color.parseColor("#1A1A2E")
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)

        // Music note icon (circle + stem)
        val notePaint = Paint().apply {
            color = android.graphics.Color.parseColor("#4FC3F7")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        // Note head (oval)
        canvas.drawOval(
            size * 0.3f, size * 0.55f,
            size * 0.55f, size * 0.72f,
            notePaint
        )
        // Note stem
        val stemPaint = Paint().apply {
            color = android.graphics.Color.parseColor("#4FC3F7")
            style = Paint.Style.STROKE
            strokeWidth = size * 0.04f
            isAntiAlias = true
        }
        canvas.drawLine(
            size * 0.545f, size * 0.62f,
            size * 0.545f, size * 0.28f,
            stemPaint
        )
        // Flag
        canvas.drawLine(
            size * 0.545f, size * 0.28f,
            size * 0.7f, size * 0.38f,
            stemPaint
        )

        // "WebWrap" text
        val textPaint = Paint().apply {
            color = android.graphics.Color.parseColor("#666688")
            textSize = size * 0.08f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("WebWrap", size / 2f, size * 0.92f, textPaint)

        return bitmap
    }

    // ================================================================
    // WAKE LOCK
    // ================================================================

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "WebWrap::MediaWakeLock"
        ).apply {
            acquire(10 * 60 * 60 * 1000L) // 10 hours max
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) { }
        wakeLock = null
    }
}