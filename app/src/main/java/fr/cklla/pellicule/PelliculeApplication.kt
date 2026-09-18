package fr.cklla.pellicule

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Point d'entrée de l'injection de dépendances Hilt : cette classe déclenche
 * la génération du graphe de dépendances au démarrage de l'application.
 */
@HiltAndroidApp
class PelliculeApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // App Check doit être installé avant le premier appel à Firestore ou à Auth, sans quoi
        // les requêtes partiraient sans jeton d'attestation. `installAppCheck` a une implémentation
        // par type de build (voir src/debug et src/release) : Play Integrity en release, jeton de
        // debug sinon.
        installAppCheck(this)
    }
}
