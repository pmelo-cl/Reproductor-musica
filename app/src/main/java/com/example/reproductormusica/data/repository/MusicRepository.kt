package com.example.reproductormusica.data.repository

import android.content.Context
import android.net.Uri
import com.example.reproductormusica.data.database.AppDatabase
import com.example.reproductormusica.models.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class MusicRepository(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val songDao = database.songDao()

    // Obtener canciones desde la base de datos local (con Flow para observar cambios)
    fun getAllSongs(): Flow<List<Song>> = songDao.getAllSongs()

    // Insertar una canción desde un URI de archivo (p.ej., desde el SAF)
    suspend fun insertSongFromUri(uri: Uri): Long = withContext(Dispatchers.IO) {
        val retriever = android.media.MediaMetadataRetriever()
        var title = "Desconocido"
        var artist = "Artista desconocido"
        var album: String? = null
        var duration = 0L
        try {
            retriever.setDataSource(context, uri)
            title = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?: uri.lastPathSegment ?: "Desconocido"
            artist = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: "Artista desconocido"
            album = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            duration = durationStr?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            retriever.release()
        }

        val song = Song(
            title = title,
            artist = artist,
            album = album,
            duration = duration,
            uriString = uri.toString(),
            albumArtUriString = null
        )
        songDao.insert(song)
    }

    // Actualizar una canción completa
    suspend fun updateSong(song: Song) = withContext(Dispatchers.IO) {
        songDao.update(song)
    }

    // Actualizar portada de una canción
    suspend fun updateAlbumArt(songId: Long, artUri: Uri) = withContext(Dispatchers.IO) {
        val song = songDao.getSongById(songId) ?: return@withContext
        val updatedSong = song.copy(albumArtUriString = artUri.toString())
        songDao.update(updatedSong)
    }

    // Eliminar canción
    suspend fun deleteSong(song: Song) = withContext(Dispatchers.IO) {
        songDao.delete(song)
    }

    // Obtener todas las canciones como lista (para la cola de reproducción)
    suspend fun getSongsSnapshot(): List<Song> = withContext(Dispatchers.IO) {
        songDao.getAllSongsSnapshot()
    }
}