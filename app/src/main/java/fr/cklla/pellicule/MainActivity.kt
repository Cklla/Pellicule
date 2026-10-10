package fr.cklla.pellicule

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import fr.cklla.pellicule.domain.model.AuthState
import fr.cklla.pellicule.notification.NotificationDestination
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
import fr.cklla.pellicule.ui.navigation.toRoute
import fr.cklla.pellicule.ui.permission.RequestNotificationPermissionOnce
import fr.cklla.pellicule.ui.recherche.RechercheScreen
import fr.cklla.pellicule.ui.stats.RecapMediaScreen
import fr.cklla.pellicule.ui.stats.RecapScreen
import fr.cklla.pellicule.ui.stats.RecapStoryScreen
import fr.cklla.pellicule.ui.stats.StatsScreen
import fr.cklla.pellicule.ui.sync.AppSyncViewModel
import fr.cklla.pellicule.ui.theme.BackgroundDark
import fr.cklla.pellicule.ui.theme.PelliculeTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Écran à ouvrir si l'activité a été lancée, ou ramenée au premier plan (voir onNewIntent),
    // depuis une notification ; `null` pour un lancement normal. `mutableStateOf` pour que
    // PelliculeApp y réagisse par recomposition.
    private var pendingDestination by mutableStateOf<NotificationDestination?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // L'app n'a qu'un thème sombre définitif : les barres système doivent toujours afficher
        // des icônes claires (visibles sur notre fond quasi-noir), qu'importe le thème
        // clair/sombre réglé sur l'appareil.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // Relu seulement au premier lancement : après une recréation (rotation), l'intent d'origine
        // est toujours là et rouvrirait l'écran déjà consommé.
        if (savedInstanceState == null) pendingDestination = NotificationDestination.fromIntent(intent)
        setContent {
            PelliculeTheme {
                PelliculeApp(
                    pendingDestination = pendingDestination,
                    onPendingDestinationConsumed = { pendingDestination = null },
                )
            }
        }
    }

    // `launchMode="singleTask"` (voir le manifeste) : si l'app tourne déjà, taper une notification
    // ramène cette même instance au premier plan via onNewIntent plutôt que d'en recréer une.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDestination = NotificationDestination.fromIntent(intent)
    }
}

// Navigation Compose : les 3 onglets sont des destinations de premier niveau (une seule instance
// de chacune, état conservé via saveState/restoreState).
//
// Connexion Google obligatoire au lancement : tant que personne n'est connecté, on affiche
// `LoginScreen` à la place du `NavHost` — pas une destination de plus dans le graphe de
// navigation, un vrai "portail" en dehors de la pile. Le temps que la session enregistrée se
// charge, rien n'est affiché : ni écran de connexion (l'utilisateur est peut-être déjà connecté),
// ni application. Dès que `AuthRepository.authState` devient `SignedIn` (connexion réussie), la
// recomposition bascule automatiquement sur le NavHost normal,
// qui démarre toujours sur la Bibliothèque. Jellyfin reste indépendant de ce compte Google : sa
// connexion propre se fait séparément depuis l'onglet Compte.
@Composable
fun PelliculeApp(
    authGateViewModel: AuthGateViewModel = hiltViewModel(),
    pendingDestination: NotificationDestination? = null,
    onPendingDestinationConsumed: () -> Unit = {},
) {
    val authState by authGateViewModel.authState.collectAsStateWithLifecycle()
    when (authState) {
        AuthState.Initializing -> {
            Box(Modifier.fillMaxSize().background(BackgroundDark))
            return
        }
        AuthState.SignedOut -> {
            LoginScreen()
            return
        }
        is AuthState.SignedIn -> Unit
    }

    // Une seule fois après la connexion, jamais à un moment arbitraire : le contrôle quotidien du
    // récap ne notifie pas sans cette permission, mais rien d'autre n'en dépend.
    RequestNotificationPermissionOnce()

    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val selectedTab = AppTab.entries.find { it.route == currentRoute }

    // Notification tapée : ouvre l'écran visé, puis se déclare consommée pour ne pas re-naviguer à
    // chaque recomposition. Placé après le portail de connexion : une destination reçue avant la
    // connexion est ouverte dès qu'elle aboutit.
    LaunchedEffect(pendingDestination) {
        pendingDestination?.let { destination ->
            destination.toRoute()?.let { route -> navController.navigate(route) }
            onPendingDestinationConsumed()
        }
    }

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
                    onStatsClick = { navController.navigate(PelliculeDestinations.STATISTIQUES) },
                    onRecapClick = { year -> navController.navigate(PelliculeDestinations.recapRoute(year)) },
                )
            }
            composable(PelliculeDestinations.STATISTIQUES) {
                StatsScreen(onBackClick = { navController.popBackStack() })
            }
            composable(
                route = PelliculeDestinations.RECAP,
                arguments = listOf(navArgument(PelliculeDestinations.RECAP_ARG_YEAR) { type = NavType.IntType }),
            ) {
                RecapScreen(
                    onBackClick = { navController.popBackStack() },
                    onMediaListClick = { year, type ->
                        navController.navigate(PelliculeDestinations.recapMediaRoute(year, type))
                    },
                    onStoryClick = { year -> navController.navigate(PelliculeDestinations.recapStoryRoute(year)) },
                )
            }
            composable(
                route = PelliculeDestinations.RECAP_STORY,
                arguments = listOf(navArgument(PelliculeDestinations.RECAP_ARG_YEAR) { type = NavType.IntType }),
            ) {
                RecapStoryScreen(
                    onCloseClick = { navController.popBackStack() },
                    onMediaClick = { mediaId -> navController.navigate(PelliculeDestinations.detailRoute(mediaId)) },
                )
            }
            composable(
                route = PelliculeDestinations.RECAP_MEDIA,
                arguments = listOf(
                    navArgument(PelliculeDestinations.RECAP_ARG_YEAR) { type = NavType.IntType },
                    navArgument(PelliculeDestinations.RECAP_ARG_TYPE) { type = NavType.StringType },
                ),
            ) {
                RecapMediaScreen(
                    onBackClick = { navController.popBackStack() },
                    onMediaClick = { mediaId -> navController.navigate(PelliculeDestinations.detailRoute(mediaId)) },
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
