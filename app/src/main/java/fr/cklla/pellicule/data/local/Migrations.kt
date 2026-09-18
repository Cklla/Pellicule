package fr.cklla.pellicule.data.local

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Migrations Room de la base `pellicule.db`.
 *
 * Nouvelle table plutôt qu'une modification de `media` : `CREATE TABLE` direct suffit, pas besoin
 * de la recréation de table nécessaire pour renommer/retyper une colonne existante.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `watched_episode` (" +
                "`mediaId` TEXT NOT NULL, " +
                "`seasonNumber` INTEGER NOT NULL, " +
                "`episodeNumber` INTEGER NOT NULL, " +
                "PRIMARY KEY(`mediaId`, `seasonNumber`, `episodeNumber`), " +
                "FOREIGN KEY(`mediaId`) REFERENCES `media`(`id`) ON DELETE CASCADE)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_watched_episode_mediaId` ON `watched_episode` (`mediaId`)",
        )
    }
}
