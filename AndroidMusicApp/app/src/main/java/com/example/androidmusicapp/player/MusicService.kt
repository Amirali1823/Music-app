package com.example.androidmusicapp.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import com.example.androidmusicapp.data.AppDatabase
import com.example.androidmusicapp.data.MusicRepository
import com.example.androidmusicapp.data.Song
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MusicService : MediaBrowserServiceCompat() {
    companion object {
        private const val CHANNEL_ID = "music_playback_channel"
        private const val CHANNEL_NAME = "Music playback"
        private const val NOTIFICATION_ID = 412
        private const val SERVICE_ROOT_ID = "root"
    }

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSessionCompat
    private var mediaSessionConnector: MediaSessionConnector? = null
    private var notificationManager: PlayerNotificationManager? = null

    // Repository for play-count increments and song access
    private lateinit var repo: MusicRepository

    // Coroutine scope for DB work
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onCreate() {
        super.onCreate()

        // init DB & repo (create AppDatabase using Room)
        val db = AppDatabase.getInstance(applicationContext)
        repo = MusicRepository(applicationContext, db)

        // create ExoPlayer
        player = ExoPlayer.Builder(applicationContext).build()

        // Create MediaSession
        mediaSession = MediaSessionCompat(this, "MusicServiceSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    player.playWhenReady = true
                }

                override fun onPause() {
                    player.playWhenReady = false
                }

                override fun onStop() {
                    player.stop()
                    stopForeground(true)
                    isActive = false
                }

                override fun onSeekTo(pos: Long) {
                    player.seekTo(pos)
                }
            })
            isActive = true
        }
        sessionToken = mediaSession.sessionToken

        // MediaSessionConnector to auto-handle actions and queue
        mediaSessionConnector = MediaSessionConnector(mediaSession).also { connector ->
            connector.setPlayer(player)
        }

        // Notification channel
        createNotificationChannel()

        // PlayerNotificationManager (handles building and showing media style notification)
        notificationManager = PlayerNotificationManager.Builder(
            this,
            NOTIFICATION_ID,
            CHANNEL_ID
        ).setMediaDescriptionAdapter(object : PlayerNotificationManager.MediaDescriptionAdapter {
            override fun getCurrentContentTitle(player: Player): CharSequence {
                val current = getCurrentSongFromPlayer()
                return current?.title ?: "Playing"
            }

            override fun createCurrentContentIntent(player: Player): PendingIntent? {
                val intent = packageManager.getLaunchIntentForPackage(packageName)
                return PendingIntent.getActivity(
                    this@MusicService,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            }

            override fun getCurrentContentText(player: Player): CharSequence? {
                return getCurrentSongFromPlayer()?.artist
            }

            override fun getCurrentLargeIcon(player: Player, callback: PlayerNotificationManager.BitmapCallback): android.graphics.Bitmap? {
                // You can load album art asynchronously and call callback.onBitmap(...) when ready.
                return null
            }
        }).setNotificationListener(object : PlayerNotificationManager.NotificationListener {
            override fun onNotificationPosted(notificationId: Int, notification: Notification, ongoing: Boolean) {
                if (ongoing) {
                    startForeground(notificationId, notification)
                } else {
                    stopForeground(false)
                }
            }

            override fun onNotificationCancelled(notificationId: Int, dismissedByUser: Boolean) {
                stopSelf()
            }
        }).build()

        // Connect the player to notification manager
        notificationManager?.setPlayer(player)

        // Listen for player state to increment play count when playback starts
        player.addListener(object : Player.Listener {
            private var lastPlayedSongId: Long? = null

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    // find current song id and increment play count once per start
                    val current = getCurrentSongFromPlayer()
                    val songId = current?.id
                    if (songId != null && songId != lastPlayedSongId) {
                        lastPlayedSongId = songId
                        serviceScope.launch {
                            repo.incrementPlayCount(songId)
                        }
                    }
                }
                if (!isPlaying && player.playbackState == Player.STATE_ENDED) {
                    // optionally handle end
                }
            }
        })
    }

    private fun getCurrentSongFromPlayer(): Song? {
        val mediaItem = player.currentMediaItem ?: return null
        val mediaId = mediaItem.mediaId ?: return null
        return try {
            val idLong = mediaId.toLong()
            // This is a blocking lookup; if performance matters, cache or use coroutines.
            AppDatabase.getInstance(applicationContext).songDao().getByIdSync(idLong)
        } catch (e: Exception) {
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        notificationManager?.setPlayer(null)
        mediaSessionConnector?.setPlayer(null)
        mediaSession.release()
        player.release()
        serviceJob.cancel()
    }

    // Handle media browser root
    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot {
        // Allow all clients to connect for this simple example
        return BrowserRoot(SERVICE_ROOT_ID, null)
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        if (parentId == SERVICE_ROOT_ID) {
            // Load songs from DB and return as media items
            val items = mutableListOf<MediaBrowserCompat.MediaItem>()
            serviceScope.launch {
                val songs = repo.dbSongListSync() // synchronous helper (see notes)
                songs.forEach { song ->
                    val desc = MediaDescriptionCompat.Builder()
                        .setMediaId(song.id.toString())
                        .setTitle(song.title)
                        .setSubtitle(song.artist)
                        .setMediaUri(android.net.Uri.parse(song.uri))
                        .build()
                    val item = MediaBrowserCompat.MediaItem(desc, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
                    items += item
                }
                result.sendResult(items)
            }
            // indicate that we'll send result asynchronously
            result.detach()
        } else {
            result.sendResult(mutableListOf())
        }
    }

    // Helper to create notification channel
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW)
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(chan)
        }
    }

    // Public helper to prepare a list of MediaItems to the player (called by ViewModel or Activity)
    fun prepareAndPlay(songs: List<Song>, startIndex: Int = 0) {
        val mediaItems = songs.map { song ->
            MediaItem.Builder()
                .setUri(song.uri)
                .setMediaId(song.id.toString())
                .setTag(song)
                .build()
        }
        player.setMediaItems(mediaItems, startIndex, 0L)
        player.prepare()
        player.play()
    }

    // Optional onStartCommand to handle intents coming from notification (media buttons)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        return START_STICKY
    }
}
