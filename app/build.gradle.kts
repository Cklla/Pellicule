import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.androidx.room)
    // Lit google-services.json et génère les ressources/config nécessaires aux SDK Firebase
    // (Auth, Firestore) à la compilation.
    alias(libs.plugins.google.services)
}

// La clé API TMDB vit uniquement dans local.properties, jamais dans le code source. On l'expose
// au code Kotlin via un champ BuildConfig généré à la compilation.
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        FileInputStream(localPropertiesFile).use { load(it) }
    }
}

// Identifiants du keystore de release : absent en configuration debug, donc chargé de façon
// optionnelle : un simple ./gradlew assembleDebug ne nécessite pas ce fichier.
val keystoreProperties = Properties().apply {
    val keystorePropertiesFile = rootProject.file("keystore.properties")
    if (keystorePropertiesFile.exists()) {
        FileInputStream(keystorePropertiesFile).use { load(it) }
    }
}

android {
    namespace = "fr.cklla.pellicule"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "fr.cklla.pellicule"
        minSdk = 24
        targetSdk = 37
        versionCode = 14
        versionName = "1.2.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "TMDB_API_KEY",
            "\"${localProperties.getProperty("TMDB_API_KEY", "")}\"",
        )
        // Configuration Supabase, injectée comme la clé TMDB : jamais dans le code source. La clé
        // publishable est publique par conception (la sécurité repose sur le RLS), mais reste
        // propre à ce projet Supabase ; l'identifiant client Web est celui déclaré côté Supabase
        // pour la connexion par jeton Google.
        buildConfigField(
            "String",
            "SUPABASE_URL",
            "\"${localProperties.getProperty("SUPABASE_URL", "")}\"",
        )
        buildConfigField(
            "String",
            "SUPABASE_PUBLISHABLE_KEY",
            "\"${localProperties.getProperty("SUPABASE_PUBLISHABLE_KEY", "")}\"",
        )
        buildConfigField(
            "String",
            "GOOGLE_WEB_CLIENT_ID",
            "\"${localProperties.getProperty("GOOGLE_WEB_CLIENT_ID", "")}\"",
        )
    }

    signingConfigs {
        // Défini uniquement si keystore.properties existe : permet à assembleRelease de fonctionner
        // ailleurs (CI, autre machine) sans configuration de signature, tant qu'on ne publie pas
        // depuis là.
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (keystoreProperties.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        // java.time (dates de visionnage, récap annuel) n'existe nativement qu'à partir de l'API 26,
        // alors que l'app vise l'API 24.
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Room génère le schéma de la base dans ce dossier à chaque compilation : permet de suivre les
// migrations de schéma dans le temps.
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    // Pont .await() entre les Task Google Play Services (FirebaseAuth.signInWithCredential...)
    // et les coroutines, pour éviter les callbacks imbriqués dans AuthRepositoryImpl.
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.navigation.compose)

    // Persistance locale (cache offline)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Injection de dépendances
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Tâches périodiques en arrière-plan (rappels de sortie d'épisodes) : le Worker se fait injecter
    // ses dépendances par Hilt comme n'importe quel autre composant.
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Chargement des jaquettes réelles renvoyées par TMDB
    implementation(libs.coil.compose)

    // Réseau : recherche de contenus via l'API TMDB (seul usage de Retrofit/Moshi, le suivi
    // lui-même reste stocké via Room/Firestore).
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.moshi.kotlin)
    implementation(libs.okhttp.logging.interceptor)

    // Stockage chiffré de la session Jellyfin (URL serveur, token d'accès utilisateur) : token
    // réel d'un compte personnel, contrairement à la clé API TMDB (secret de build, non sensible
    // côté utilisateur).
    implementation(libs.androidx.security.crypto)

    // Supabase : authentification (connexion par jeton Google). Le moteur HTTP OkHttp est celui
    // qu'utilise déjà le reste de l'app ; sans lui, Ktor n'aurait aucun moteur sur Android.
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.ktor.client.okhttp)

    // Firebase : Firestore (source de vérité distante du suivi) + Auth (identifie l'utilisateur,
    // nécessaire aux règles de sécurité Firestore). Le BoM aligne les versions des différents
    // modules Firebase entre eux, pas besoin de préciser de version sur chacun.
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    // App Check : atteste que les appels à Firestore/Auth viennent bien de cette app installée
    // depuis le Play Store, et pas d'un script ou d'une app reconstruite à partir du binaire.
    implementation(libs.firebase.appcheck.playintegrity)
    // Le fournisseur Play Integrity ne peut rien attester sur un émulateur ou un build local : en
    // debug, App Check s'appuie sur un jeton à déclarer dans la console Firebase.
    debugImplementation(libs.firebase.appcheck.debug)

    // Connexion Google (Credential Manager)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}