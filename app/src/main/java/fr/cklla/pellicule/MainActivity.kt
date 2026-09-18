package fr.cklla.pellicule

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import fr.cklla.pellicule.ui.AppTab
import androidx.navigation.NavType
import androidx.navigation.navArgument
import fr.cklla.pellicule.ui.bibliotheque.BibliothequeScreen
import fr.cklla.pellicule.ui.components.BottomNavBar
import fr.cklla.pellicule.ui.compte.CompteScreen
import fr.cklla.pellicule.ui.detail.DetailScreen
import fr.cklla.pellicule.ui.jellyfin.JellyfinSettingsScreen
import fr.cklla.pellicule.ui.login.AuthGateViewModel
import fr.cklla.pellicule.ui.login.LoginScreen
import fr.cklla.pellicule.ui.navigation.PelliculeDestinations
import fr.cklla.pellicule.ui.navigation.route
import fr.cklla.pellicule.ui.recherche.RechercheScreen
import fr.cklla.pellicule.ui.sync.AppSyncViewModel
import fr.cklla.pellicule.ui.theme.BackgroundDark
import fr.cklla.pellicule.ui.theme.PelliculeTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // L'app n'a qu'un thème sombre définitif : les barres système doivent toujours afficher
        // des icônes claires (visibles sur notre fond quasi-noir), qu'importe le thème
        // clair/sombre réglé sur l'appareil.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            PelliculeTheme {
                PelliculeApp()
            }
        }
    }
}

// Navigation Compose : les 3 onglets sont des destinations de premier niveau (une seule instance
// de chacune, état conservé via saveState/restoreState).
//
// Connexion Google obligatoire au lancement : tant que personne n'est connecté, on affiche
// `LoginScreen` à la place du `NavHost` — pas une destination de plus dans le graphe de
// navigation, un vrai "portail" en dehors de la pile. Dès que `AuthRepository.currentUser` devient
// non-null (connexion réussie), la recomposition bascule automatiquement sur le NavHost normal,
// qui démarre toujours sur la Bibliothèque. Jellyfin reste indépendant de ce compte Google : sa
// connexion propre se fait séparément depuis l'onglet Compte.
@Composable
fun PelliculeApp(authGateViewModel: AuthGateViewModel = hiltViewModel()) {
    val currentUser by authGateViewModel.currentUser.collectAsStateWithLifecycle()
    if (currentUser == null) {
        LoginScreen()
        return
    }

    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val selectedTab = AppTab.entries.find { it.route == currentRoute }

    // Synchro Jellyfin (statut vu des épisodes) à chaque ouverture/reprise de l'app, pas
    // seulement pour la série consultée (voir `AppSyncViewModel`) — no-op si aucun serveur n'est
    // connecté.
    val syncViewModel: AppSyncViewModel = hiltViewModel()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) syncViewModel.onAppResumed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        containerColor = BackgroundDark,
        bottomBar = {
            if (selectedTab != null) {
                BottomNavBar(
                    selectedTab = selectedTab,
                    onTabSelected = { tab ->
                        navController.navigate(tab.route) {
                            popUpTo(PelliculeDestinations.BIBLIOTHEQUE) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = PelliculeDestinations.BIBLIOTHEQUE,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(PelliculeDestinations.BIBLIOTHEQUE) {
                BibliothequeScreen(
                    onMediaClick = { mediaId ->
                        navController.navigate(PelliculeDestinations.detailRoute(mediaId))
                    },
                )
            }
            composable(PelliculeDestinations.RECHERCHE) {
                RechercheScreen(
                    onResultClick = { result, trackedMediaId ->
                        val route = trackedMediaId?.let { PelliculeDestinations.detailRoute(it) }
                            ?: PelliculeDestinations.detailApercuRoute(result)
                        navController.navigate(route)
                    },
                )
            }
            composable(PelliculeDestinations.COMPTE) {
                CompteScreen(
                    onJellyfinSettingsClick = { navController.navigate(PelliculeDestinations.JELLYFIN_SETTINGS) },
                )
            }
            composable(PelliculeDestinations.JELLYFIN_SETTINGS) {
                JellyfinSettingsScreen(onBackClick = { navController.popBackStack() })
            }
            composable(
                route = PelliculeDestinations.DETAIL,
                arguments = listOf(navArgument(PelliculeDestinations.DETAIL_ARG_MEDIA_ID) { type = NavType.StringType }),
            ) {
                DetailScreen(onBackClick = { navController.popBackStack() })
            }
            composable(
                route = PelliculeDestinations.DETAIL_APERCU,
                arguments = listOf(
                    navArgument(PelliculeDestinations.DETAIL_APERCU_ARG_TMDB_ID) {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                    navArgument(PelliculeDestinations.DETAIL_APERCU_ARG_TITLE) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument(PelliculeDestinations.DETAIL_APERCU_ARG_TYPE) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument(PelliculeDestinations.DETAIL_APERCU_ARG_YEAR) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument(PelliculeDestinations.DETAIL_APERCU_ARG_POSTER_URL) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) {
                DetailScreen(onBackClick = { navController.popBackStack() })
            }
        }
    }
}
