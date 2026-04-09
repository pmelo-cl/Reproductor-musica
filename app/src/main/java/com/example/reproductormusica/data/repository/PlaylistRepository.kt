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

    /**
     * Creates a playlist with [songIds] in order. Name comes from [titleHint] if unique,
     * otherwise "Name (2)", …; if [titleHint] is null/blank, uses "Playlist N" with first free N.
     */
    suspend fun createPlaylistWithSongsFromDownload(titleHint: String?, songIds: List<Long>): Long {
        val name = resolveUniquePlaylistName(titleHint)
        val playlistId = playlistDao.insertPlaylist(Playlist(name = name))
        for (songId in songIds) {
            playlistDao.addSongToPlaylist(PlaylistSongCrossRef(playlistId, songId))
        }
        return playlistId
    }

    private suspend fun resolveUniquePlaylistName(titleHint: String?): String {
        val existing = playlistDao.getAllPlaylistNames().toMutableSet()
        val hint = titleHint?.trim()?.takeIf { it.isNotBlank() }
        if (hint != null) {
            if (hint !in existing) return hint
            var i = 2
            while (true) {
                val candidate = "$hint ($i)"
                if (candidate !in existing) return candidate
                i++
            }
        }
        var n = 1
        while (true) {
            val candidate = "Playlist $n"
            if (candidate !in existing) return candidate
            n++
        }
    }
}