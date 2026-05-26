package com.example.reproductormusica.data.repository

import com.example.reproductormusica.data.database.AlbumDao
import com.example.reproductormusica.models.Album
import com.example.reproductormusica.models.Song
import kotlinx.coroutines.flow.Flow

class AlbumRepository(private val albumDao: AlbumDao) {

    fun getAllAlbums(): Flow<List<Album>> = albumDao.getAllAlbums()

    suspend fun getOrCreateAlbum(name: String, artist: String, coverUri: String? = null): Long {
        val existing = albumDao.getAlbum(name, artist)
        return if (existing != null) {
            existing.id
        } else {
            albumDao.insert(Album(name = name, artist = artist, coverUriString = coverUri))
        }
    }

    fun getSongsFromAlbum(albumName: String, artist: String): Flow<List<Song>> =
        albumDao.getSongsFromAlbum(albumName, artist)
}