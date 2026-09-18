package fr.cklla.pellicule

import android.app.Application
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Version debug de l'installation d'App Check.
 *
 * Play Integrity ne sait rien attester sur un émulateur ou sur un build signé localement : le
 * fournisseur de debug le remplace en générant un jeton fixe, affiché dans logcat au premier
 * lancement et à déclarer dans la console Firebase (App Check > l'app Android > menu ... >
 * "Gérer les jetons de débogage"). Sans cette déclaration, les builds de debug se feront refuser
 * dès qu'App Check passera en mode appliqué.
 */
fun installAppCheck(application: Application) {
    FirebaseAppCheck.getInstance()
        .installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
}
