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

/** Ajoute l'id de l'item correspondant sur un serveur Jellyfin connecté, résolu et mis en cache une fois trouvé (voir `JellyfinRepository`). */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `media` ADD COLUMN `jellyfinId` TEXT")
    }
}

/** Ajoute la note personnelle (1 à 5) éditable sur la fiche détail. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `media` ADD COLUMN `rating` INTEGER")
    }
}

/**
 * Ajoute l'horodatage du passage au statut "vu", pour permettre le filtre par année de
 * visionnage sur la Bibliothèque. Additive uniquement : un contenu déjà marqué "vu" avant cette
 * migration démarre avec `watchedAt` à `NULL` (date de visionnage historique inconnue), il reste
 * visible sous le filtre "Vu" mais n'apparaît sous aucun filtre par année tant que son statut n'est
 * pas de nouveau modifié.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `media` ADD COLUMN `watchedAt` INTEGER")
    }
}
