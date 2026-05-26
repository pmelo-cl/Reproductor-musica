package com.example.reproductormusica.data.database

import androidx.room.*
import com.example.reproductormusica.models.Album
import com.example.reproductormusica.models.Song
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(album: Album): Long

    @Update
    suspend fun update(album: Album)

    @Delete
    suspend fun delete(album: Album)

    @Query("SELECT * FROM albums ORDER BY name ASC")
    fun getAllAlbums(): Flow<List<Album>>

    @Query("SELECT * FROM albums WHERE name = :name AND artist = :artist LIMIT 1")
    suspend fun getAlbum(name: String, artist: String): Album?

    @Query("SELECT * FROM songs WHERE album = :albumName AND artist = :artist ORDER BY title ASC")
    fun getSongsFromAlbum(albumName: String, artist: String): Flow<List<Song>>
}