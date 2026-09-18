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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import fr.cklla.pellicule.ui.AppTab
import fr.cklla.pellicule.ui.bibliotheque.BibliothequeScreen
import fr.cklla.pellicule.ui.components.BottomNavBar
import fr.cklla.pellicule.ui.navigation.PelliculeDestinations
import fr.cklla.pellicule.ui.navigation.route
import fr.cklla.pellicule.ui.recherche.RechercheScreen
import fr.cklla.pellicule.ui.stats.StatsScreen
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
// de chacune, état conservé via saveState/restoreState). Pas de gate de connexion pour l'instant
// (Firebase Auth pas encore câblé côté Pellicule, voir WORK.md) : l'app démarre directement sur
// la Bibliothèque, conformément à la contrainte CLAUDE.md d'utilisabilité sans compte/serveur.
@Composable
fun PelliculeApp() {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val selectedTab = AppTab.entries.find { it.route == currentRoute }

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
            composable(PelliculeDestinations.BIBLIOTHEQUE) { BibliothequeScreen() }
            composable(PelliculeDestinations.RECHERCHE) { RechercheScreen() }
            composable(PelliculeDestinations.STATS) { StatsScreen() }
        }
    }
}
