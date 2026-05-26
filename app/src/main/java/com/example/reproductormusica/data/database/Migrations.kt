package com.example.reproductormusica.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Añadir columna duration a songs si no existe (versión 2)
            database.execSQL("ALTER TABLE songs ADD COLUMN duration INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Añadir posición en playlist_song_cross_ref
            database.execSQL("ALTER TABLE playlist_song_cross_ref ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Crear tablas de álbumes y artistas
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS albums (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    artist TEXT NOT NULL,
                    coverUriString TEXT,
                    year INTEGER
                )
            """)
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS artists (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    coverUriString TEXT
                )
            """)
        }
    }

    fun getAllMigrations(): Array<Migration> = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4
    )
}