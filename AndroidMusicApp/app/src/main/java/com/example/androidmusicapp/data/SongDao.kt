package com.example.androidmusicapp.data

import androidx.room.*

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY title")
    fun getAll(): kotlinx.coroutines.flow.Flow<List<Song>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(songs: List<Song>)

    @Update
    suspend fun update(song: Song)

    @Query("UPDATE songs SET playCount = playCount + 1 WHERE id = :songId")
    suspend fun incrementPlayCount(songId: Long)

    @Query("SELECT * FROM songs WHERE id = :songId LIMIT 1")
    suspend fun getById(songId: Long): Song?

    // blocking helpers (used carefully by service)
    @Query("SELECT * FROM songs WHERE id = :songId LIMIT 1")
    fun getByIdSync(songId: Long): Song?

    @Query("SELECT * FROM songs ORDER BY title")
    fun getAllSync(): List<Song>
}
