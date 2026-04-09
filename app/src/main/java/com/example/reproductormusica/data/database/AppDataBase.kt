package com.example.reproductormusica.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context
import com.example.reproductormusica.models.Playlist
import com.example.reproductormusica.models.PlaylistSongCrossRef
import com.example.reproductormusica.models.Song

@Database(
    entities = [Song::class, Playlist::class, PlaylistSongCrossRef::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "music_database"
                )
                    .fallbackToDestructiveMigration(false)   // version 1 → 2
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}