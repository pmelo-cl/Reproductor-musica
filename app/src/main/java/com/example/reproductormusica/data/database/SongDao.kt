package com.example.reproductormusica.data.database

import androidx.room.*
import com.example.reproductormusica.models.Song
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(song: Song): Long

    @Update
    suspend fun update(song: Song)

    @Delete
    suspend fun delete(song: Song)

    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun getAllSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getSongById(id: Long): Song?

    @Query("SELECT * FROM songs WHERE uriString = :uriString LIMIT 1")
    suspend fun getSongByUri(uriString: String): Song?

    @Query("UPDATE songs SET title = :title, artist = :artist WHERE id = :id")
    suspend fun updateInfo(id: Long, title: String, artist: String)
}