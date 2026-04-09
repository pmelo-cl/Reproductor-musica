package com.example.reproductormusica.data.repository

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.reproductormusica.data.database.SongDao
import com.example.reproductormusica.models.Song
import com.example.reproductormusica.utils.sanitizedFileName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class MusicRepository(
    private val songDao: SongDao,
    private val contentResolver: ContentResolver
) {
    fun getAllSongs(): Flow<List<Song>> = songDao.getAllSongs()

    suspend fun addSongFromUri(uri: Uri, context: Context): Long {
        return withContext(Dispatchers.IO) {
            val existing = songDao.getSongByUri(uri.toString())
            if (existing != null) {
                return@withContext -1L
            }

            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                e.printStackTrace()
            }

            val retriever = MediaMetadataRetriever()
            return@withContext try {
                retriever.setDataSource(context, uri)
                val extractedTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                val extractedArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val duration = durationStr?.toLongOrNull() ?: 0L

                val title = extractedTitle?.takeIf { it.isNotBlank() } ?: uri.sanitizedFileName()
                val artist = extractedArtist?.takeIf { it.isNotBlank() } ?: "Artista desconocido"

                val song = Song(
                    title = title,
                    artist = artist,
                    uriString = uri.toString(),
                    duration = duration
                )
                songDao.insert(song)
            } catch (e: Exception) {
                e.printStackTrace()
                val song = Song(
                    title = uri.sanitizedFileName(),
                    artist = "Desconocido",
                    uriString = uri.toString(),
                    duration = 0L
                )
                songDao.insert(song)
            } finally {
                retriever.release()
            }
        }
    }

    suspend fun deleteSong(song: Song) {
        songDao.delete(song)
    }

    suspend fun updateSongAlbumArt(song: Song, artUri: Uri) {
        val updated = song.copy(albumArtUriString = artUri.toString())
        songDao.update(updated)
    }

    suspend fun updateSongInfo(songId: Long, title: String, artist: String) {
        songDao.updateInfo(songId, title, artist)
    }
}