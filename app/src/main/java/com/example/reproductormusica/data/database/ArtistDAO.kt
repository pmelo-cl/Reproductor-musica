package com.example.reproductormusica.data.database

import androidx.room.*
import com.example.reproductormusica.models.Artist
import com.example.reproductormusica.models.Song
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtistDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(artist: Artist): Long

    @Update
    suspend fun update(artist: Artist)

    @Delete
    suspend fun delete(artist: Artist)

    @Query("SELECT * FROM artists ORDER BY name ASC")
    fun getAllArtists(): Flow<List<Artist>>

    @Query("SELECT * FROM artists WHERE name = :name LIMIT 1")
    suspend fun getArtistByName(name: String): Artist?

    @Query("SELECT * FROM songs WHERE artist = :artistName ORDER BY album ASC, title ASC")
    fun getSongsFromArtist(artistName: String): Flow<List<Song>>

    @Query("SELECT DISTINCT album FROM songs WHERE artist = :artistName AND album IS NOT NULL AND album != '' ORDER BY album ASC")
    fun getAlbumsFromArtist(artistName: String): Flow<List<String>>
}