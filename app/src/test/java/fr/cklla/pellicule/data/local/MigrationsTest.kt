package fr.cklla.pellicule.data.local

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import java.io.File
import java.lang.reflect.Proxy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vérifie la migration 7 → 8 contre le schéma que Room a exporté pour la version 8 : les instructions
 * exécutées doivent produire exactement les tables et index attendus par la base, faute de quoi Room
 * refuse d'ouvrir la base au lancement.
 */
class MigrationsTest {

    /** Connexion factice qui enregistre le SQL qu'on lui demande d'exécuter. */
    private fun recordingConnection(executed: MutableList<String>): SQLiteConnection {
        val statement = Proxy.newProxyInstance(
            SQLiteStatement::class.java.classLoader,
            arrayOf(SQLiteStatement::class.java),
        ) { _, method, _ ->
            when (method.returnType) {
                Boolean::class.javaPrimitiveType -> false
                Int::class.javaPrimitiveType -> 0
                Long::class.javaPrimitiveType -> 0L
                Double::class.javaPrimitiveType -> 0.0
                String::class.java -> ""
                else -> null
            }
        } as SQLiteStatement
        return Proxy.newProxyInstance(
            SQLiteConnection::class.java.classLoader,
            arrayOf(SQLiteConnection::class.java),
        ) { _, method, args ->
            if (method.name == "prepare") {
                executed += args[0] as String
                statement
            } else {
                null
            }
        } as SQLiteConnection
    }

    private fun exportedSchema(version: Int): JsonObject {
        val file = File("schemas/fr.cklla.pellicule.data.local.AppDatabase/$version.json")
        assertTrue("schéma exporté introuvable : ${file.absolutePath}", file.exists())
        return Json.parseToJsonElement(file.readText()).jsonObject.getValue("database").jsonObject
    }

    private fun entities(schema: JsonObject): JsonArray = schema.getValue("entities").jsonArray

    private fun createSql(schema: JsonObject, table: String): String {
        val entity = entities(schema).map { it.jsonObject }.firstOrNull { it.getValue("tableName").jsonPrimitive.content == table }
            ?: error("table $table absente du schéma")
        return entity.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table)
    }

    @Test
    fun `la migration 7 vers 8 cree la table de la file d'envoi telle que Room l'attend`() {
        val executed = mutableListOf<String>()

        MIGRATION_7_8.migrate(recordingConnection(executed))

        assertEquals(listOf(createSql(exportedSchema(8), "pending_operation")), executed)
    }

    @Test
    fun `la migration 7 vers 8 va de la version 7 a la version 8`() {
        assertEquals(7, MIGRATION_7_8.startVersion)
        assertEquals(8, MIGRATION_7_8.endVersion)
    }

    @Test
    fun `la version 8 ne differe de la version 7 que par la file d'envoi`() {
        fun tables(version: Int): Set<String> =
            entities(exportedSchema(version)).map { it.jsonObject.getValue("tableName").jsonPrimitive.content }.toSet()

        assertEquals(setOf("pending_operation"), tables(8) - tables(7))
        assertEquals(emptySet<String>(), tables(7) - tables(8))
    }
}
