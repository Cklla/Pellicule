package fr.cklla.pellicule

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.cklla.pellicule.data.local.AppDatabase
import fr.cklla.pellicule.data.local.MIGRATION_7_8
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration de la base sur un vrai SQLite : une base en version 7 avec du suivi et un épisode vu doit
 * s'ouvrir en version 8 sans rien perdre, avec la file d'envoi vide et prête à servir.
 */
@RunWith(AndroidJUnit4::class)
class MigrationInstrumentedTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun migrationDe7a8ConserveLeSuiviEtCreeLaFileDEnvoi() {
        helper.createDatabase(DB_NAME, 7).apply {
            execSQL("INSERT INTO media (id, title, type, status) VALUES ('m1', 'Fallout', 'SERIE', 'EN_COURS')")
            execSQL("INSERT INTO watched_episode (mediaId, seasonNumber, episodeNumber) VALUES ('m1', 1, 2)")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(DB_NAME, 8, true, MIGRATION_7_8)

        migrated.query("SELECT title FROM media WHERE id = 'm1'").use {
            assertEquals(1, it.count)
            it.moveToFirst()
            assertEquals("Fallout", it.getString(0))
        }
        migrated.query("SELECT COUNT(*) FROM watched_episode").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
        migrated.query("SELECT COUNT(*) FROM pending_operation").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
        migrated.execSQL("INSERT INTO pending_operation (kind, mediaId) VALUES ('UPSERT_MEDIA', 'm1')")
        migrated.query("SELECT seq FROM pending_operation").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
    }

    private companion object {
        const val DB_NAME = "migration-test"
    }
}
