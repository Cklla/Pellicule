package fr.cklla.pellicule

import android.app.Application
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Version release de l'installation d'App Check.
 *
 * Chaque appel à Firestore et à Firebase Auth part accompagné d'un jeton attestant que la requête
 * vient bien de cette app, installée depuis le Play Store sur un appareil Android légitime. C'est
 * ce qui empêche un tiers d'utiliser le projet Firebase depuis un script ou une app reconstruite à
 * partir du binaire : la configuration Firebase embarquée dans l'APK ne suffit plus.
 */
fun installAppCheck(application: Application) {
    FirebaseAppCheck.getInstance()
        .installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
}
