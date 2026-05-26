package com.example.reproductormusica.data.repository

import com.example.reproductormusica.data.database.ArtistDao
import com.example.reproductormusica.models.Artist
import com.example.reproductormusica.models.Song
import kotlinx.coroutines.flow.Flow

class ArtistRepository(private val artistDao: ArtistDao) {

    fun getAllArtists(): Flow<List<Artist>> = artistDao.getAllArtists()

    suspend fun getOrCreateArtist(name: String, coverUri: String? = null): Long {
        val existing = artistDao.getArtistByName(name)
        return if (existing != null) {
            existing.id
        } else {
            artistDao.insert(Artist(name = name, coverUriString = coverUri))
        }
    }

    fun getSongsFromArtist(artistName: String): Flow<List<Song>> =
        artistDao.getSongsFromArtist(artistName)

    fun getAlbumsFromArtist(artistName: String): Flow<List<String>> =
        artistDao.getAlbumsFromArtist(artistName)
}