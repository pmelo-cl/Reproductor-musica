package com.example.reproductormusica.data.repository

import android.net.Uri
import com.example.reproductormusica.data.database.PlaylistDao
import com.example.reproductormusica.models.Playlist
import com.example.reproductormusica.models.PlaylistSongCrossRef
import com.example.reproductormusica.models.PlaylistWithSongs
import kotlinx.coroutines.flow.Flow

class PlaylistRepository(private val playlistDao: PlaylistDao) {

    fun getAllPlaylists(): Flow<List<Playlist>> = playlistDao.getAllPlaylists()

    fun getAllPlaylistsWithSongs(): Flow<List<PlaylistWithSongs>> =
        playlistDao.getAllPlaylistsWithSongs()

    fun getPlaylistWithSongs(playlistId: Long): Flow<PlaylistWithSongs?> =
        playlistDao.getPlaylistWithSongs(playlistId)

    suspend fun createPlaylist(name: String, coverUri: Uri? = null): Long =
        playlistDao.insertPlaylist(
            Playlist(name = name, coverUriString = coverUri?.toString())
        )

    suspend fun updatePlaylist(playlist: Playlist) = playlistDao.updatePlaylist(playlist)

    suspend fun deletePlaylist(playlist: Playlist) {
        playlistDao.clearPlaylist(playlist.id)
        playlistDao.deletePlaylist(playlist)
    }

    suspend fun addSongToPlaylist(playlistId: Long, songId: Long) =
        playlistDao.addSongToPlaylist(PlaylistSongCrossRef(playlistId, songId))

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) =
        playlistDao.removeSongFromPlaylist(PlaylistSongCrossRef(playlistId, songId))

    suspend fun updatePlaylistCover(playlist: Playlist, coverUri: Uri) =
        playlistDao.updatePlaylist(playlist.copy(coverUriString = coverUri.toString()))

    suspend fun getPlaylistByName(name: String): Playlist? =
        playlistDao.getPlaylistByName(name)

    suspend fun getPlaylistById(playlistId: Long): Playlist? {
        return playlistDao.getPlaylistById(playlistId)
    }
}