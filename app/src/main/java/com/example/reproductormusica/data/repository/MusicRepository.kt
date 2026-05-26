package com.example.reproductormusica.data.repository

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import com.example.reproductormusica.data.database.SongDao
import com.example.reproductormusica.models.Song
import com.example.reproductormusica.utils.MetadataFetcher
import com.example.reproductormusica.utils.sanitizedFileName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

class MusicRepository(
    private val songDao: SongDao,
    private val contentResolver: ContentResolver
) {
    private lateinit var albumRepository: AlbumRepository
    private lateinit var artistRepository: ArtistRepository

    fun setAlbumAndArtistRepos(albumRepo: AlbumRepository, artistRepo: ArtistRepository) {
        albumRepository = albumRepo
        artistRepository = artistRepo
    }

    fun getAllSongs(): Flow<List<Song>> = songDao.getAllSongs()

    suspend fun addSongFromUri(uri: Uri, context: Context, skipPermissionRequest: Boolean = false): Long {
        return withContext(Dispatchers.IO) {
            val existing = songDao.getSongByUri(uri.toString())
            if (existing != null) {
                return@withContext -1L
            }

            if (!skipPermissionRequest) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: SecurityException) {
                    e.printStackTrace()
                }
            }

            val retriever = MediaMetadataRetriever()
            return@withContext try {
                retriever.setDataSource(context, uri)
                val extractedTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                val extractedArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                val extractedAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val duration = durationStr?.toLongOrNull() ?: 0L

                val title = extractedTitle?.takeIf { it.isNotBlank() } ?: uri.sanitizedFileName()
                val artist = extractedArtist?.takeIf { it.isNotBlank() } ?: "Artista desconocido"

                val embedded = retriever.embeddedPicture
                var albumArtUri: String? = null
                if (embedded != null && embedded.isNotEmpty()) {
                    albumArtUri = saveEmbeddedArt(context, embedded)
                }

                val album = extractedAlbum?.takeIf { it.isNotBlank() }

                val song = Song(
                    title = title,
                    artist = artist,
                    album = album,
                    uriString = uri.toString(),
                    albumArtUriString = albumArtUri,
                    duration = duration
                )
                val insertId = songDao.insert(song)
                if (insertId == -1L) return@withContext -1L

                val current = song.copy(id = insertId)

                // Actualizar álbumes y artistas
                if (::albumRepository.isInitialized && ::artistRepository.isInitialized) {
                    updateAlbumsAndArtists(current)
                }

                val shouldFetchOnline = isNetworkAvailable(context) && (
                        albumArtUri == null ||
                                album.isNullOrBlank() ||
                                artist == "Artista desconocido" ||
                                artist == "Desconocido"
                        )
                if (shouldFetchOnline) {
                    try {
                        val meta = MetadataFetcher.getInstance(context).fetchTrackMetadata(title, artist, album)
                        if (meta != null) {
                            val updated = current.copy(
                                albumArtUriString = meta.albumArtUriString ?: current.albumArtUriString,
                                artist = meta.artistDisplayName ?: current.artist,
                                album = meta.albumTitle ?: current.album
                            )
                            if (updated != current) {
                                songDao.update(updated)
                                if (::albumRepository.isInitialized && ::artistRepository.isInitialized) {
                                    updateAlbumsAndArtists(updated)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                insertId
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

    private suspend fun updateAlbumsAndArtists(song: Song) {
        if (song.artist.isNotBlank() && song.artist != "Artista desconocido" && song.artist != "Desconocido") {
            artistRepository.getOrCreateArtist(song.artist, song.albumArtUriString)
        }
        if (song.album != null && song.artist.isNotBlank()) {
            albumRepository.getOrCreateAlbum(song.album, song.artist, song.albumArtUriString)
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

    private fun saveEmbeddedArt(context: Context, bytes: ByteArray): String {
        val dir = File(context.cacheDir, "album_art").apply { mkdirs() }
        val name = "emb_${abs(bytes.contentHashCode())}.jpg"
        val f = File(dir, name)
        f.writeBytes(bytes)
        return Uri.fromFile(f).toString()
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}