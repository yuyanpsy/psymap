package com.psymap.app

import android.app.*
import android.content.Intent
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class AudioPlaybackService : Service() {

    companion object {
        const val CHANNEL_ID = "psymap_audio"
        const val NOTIFICATION_ID = 1001
        var instance: AudioPlaybackService? = null
    }

    inner class AudioBinder : Binder() {
        fun getService(): AudioPlaybackService = this@AudioPlaybackService
    }

    private val binder = AudioBinder()
    var mediaPlayer: MediaPlayer? = null
    var playlist: List<String> = emptyList()  // file paths
    var currentIndex: Int = 0
    var isPlaying: Boolean = false
    var currentFileName: String = ""
    var onStateChanged: (() -> Unit)? = null

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "PLAY" -> {
                val paths = intent.getStringArrayListExtra("paths") ?: return START_NOT_STICKY
                playlist = paths
                currentIndex = 0
                playFile(0)
            }
            "PAUSE" -> { mediaPlayer?.pause(); isPlaying = false; updateNotification(); onStateChanged?.invoke() }
            "RESUME" -> { mediaPlayer?.start(); isPlaying = true; updateNotification(); onStateChanged?.invoke() }
            "NEXT" -> { if (currentIndex < playlist.size - 1) playFile(currentIndex + 1) }
            "PREV" -> { if (currentIndex > 0) playFile(currentIndex - 1) }
            "STOP" -> { stopPlayback(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
        }
        return START_NOT_STICKY
    }

    fun playFile(index: Int) {
        if (index >= playlist.size) { stopPlayback(); return }
        currentIndex = index
        val path = playlist[index]
        currentFileName = java.io.File(path).nameWithoutExtension

        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(path)
            prepare()
            start()
            setOnCompletionListener {
                if (currentIndex < playlist.size - 1) playFile(currentIndex + 1)
                else { this@AudioPlaybackService.isPlaying = false; onStateChanged?.invoke(); updateNotification() }
            }
        }
        this.isPlaying = true
        updateNotification()
        startForeground(NOTIFICATION_ID, buildNotification())
        onStateChanged?.invoke()
    }

    fun stopPlayback() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        isPlaying = false
        currentFileName = ""
        onStateChanged?.invoke()
    }

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
    }

    val duration: Int get() = mediaPlayer?.duration ?: 0
    val currentPosition: Int get() = try { mediaPlayer?.currentPosition ?: 0 } catch (_: Exception) { 0 }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "磨耳朵播放", NotificationManager.IMPORTANCE_LOW).apply {
                description = "音频播放通知"
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = PendingIntent.getService(this, 1,
            Intent(this, AudioPlaybackService::class.java).apply { action = "STOP" }, PendingIntent.FLAG_IMMUTABLE)
        val pauseResumeIntent = PendingIntent.getService(this, 2,
            Intent(this, AudioPlaybackService::class.java).apply { action = if (isPlaying) "PAUSE" else "RESUME" },
            PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("磨耳朵 - ${currentFileName}")
            .setContentText(if (isPlaying) "正在播放 ${currentIndex + 1}/${playlist.size}" else "已暂停")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .addAction(0, if (isPlaying) "暂停" else "播放", pauseResumeIntent)
            .addAction(0, "停止", stopIntent)
            .setOngoing(isPlaying)
            .build()
    }

    private fun updateNotification() {
        try {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIFICATION_ID, buildNotification())
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        stopPlayback()
        instance = null
        super.onDestroy()
    }
}
