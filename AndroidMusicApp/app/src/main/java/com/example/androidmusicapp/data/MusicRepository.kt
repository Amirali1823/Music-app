package com.example.androidmusicapp.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.flow.Flow

class MusicRepository(
    private val context: Context,
    val db: AppDatabase
) {
    val songsFlow: Flow<List<Song>> = db.songDao().getAll()

    suspend fun scanDeviceAndPopulate() {
        val resolver: ContentResolver = context.contentResolver
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC}=1"
        val cursor = resolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null, null)
        val list = mutableListOf<Song>()
        cursor?.use {
            val idIndex = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleIndex = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistIndex = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durIndex = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIndex = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            while (it.moveToNext()) {
                val id = it.getLong(idIndex)
                val title = it.getString(titleIndex) ?: "Unknown"
                val artist = it.getString(artistIndex)
                val duration = it.getLong(durIndex)
                val albumId = it.getLong(albumIndex)
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString()
                list += Song(
                    id = id,
                    uri = uri,
                    title = title,
                    artist = artist,
                    duration = duration,
                    albumId = albumId
                )
            }
        }
        if (list.isNotEmpty()) db.songDao().insertAll(list)
    }

    suspend fun incrementPlayCount(songId: Long) {
        db.songDao().incrementPlayCount(songId)
    }

    suspend fun updateSong(song: Song) {
        db.songDao().update(song)
    }

    // blocking helpers for service usage
    fun dbSongListSync(): List<Song> = db.songDao().getAllSync()
}
