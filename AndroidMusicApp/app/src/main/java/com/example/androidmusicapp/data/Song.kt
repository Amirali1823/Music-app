package com.example.androidmusicapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class Song(
    @PrimaryKey val id: Long,
    val uri: String,
    val title: String,
    val artist: String? = null,
    val duration: Long = 0,
    val albumId: Long? = null,
    val isFavorite: Boolean = false,
    val playCount: Int = 0
)
