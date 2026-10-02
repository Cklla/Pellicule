package fr.cklla.pellicule.ui.permission

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit

/**
 * Vrai si l'app peut afficher des notifications : permission `POST_NOTIFICATIONS` accordée
 * (Android 13+) et notifications non désactivées dans les réglages système (toutes versions).
 */
fun canPostNotifications(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled()

/**
 * Demande la permission de notification à la demande, au moment où une fonctionnalité en a besoin
 * plutôt qu'au lancement. Obtenu par [rememberNotificationPermissionRequester].
 */
class NotificationPermissionRequester internal constructor(
    private val isGranted: () -> Boolean,
    private val canAsk: Boolean,
    private val launch: (onResult: (Boolean) -> Unit) -> Unit,
) {

    /**
     * Appelle [onResult] avec `true` si l'app peut notifier, `false` sinon. Si la permission n'est
     * pas encore accordée, la demande système s'affiche d'abord ; avant Android 13 il n'y a rien à
     * demander (seul un réglage système peut rouvrir les notifications), le résultat est immédiat.
     */
    fun request(onResult: (Boolean) -> Unit) {
        when {
            isGranted() -> onResult(true)
            !canAsk -> onResult(false)
            else -> launch(onResult)
        }
    }
}

@Composable
fun rememberNotificationPermissionRequester(): NotificationPermissionRequester {
    val context = LocalContext.current
    val pending = remember { PendingResult() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pending.callback?.invoke(granted)
        pending.callback = null
    }
    return remember(context, launcher) {
        NotificationPermissionRequester(
            isGranted = { canPostNotifications(context) },
            canAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
            launch = { onResult ->
                pending.callback = onResult
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
        )
    }
}

/**
 * Demande la permission de notification une seule fois pour toute la vie de l'installation, à
 * l'arrivée dans l'app connectée. Le refus ne bloque rien : la notification du récap ne part
 * simplement pas, la carte du récap reste disponible. Une demande ultérieure reste possible, à
 * l'activation d'un rappel d'épisode.
 */
@Composable
fun RequestNotificationPermissionOnce() {
    val context = LocalContext.current
    val requester = rememberNotificationPermissionRequester()
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences(PERMISSION_PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_PERMISSION_ASKED, false)) return@LaunchedEffect
        prefs.edit { putBoolean(KEY_PERMISSION_ASKED, true) }
        requester.request { }
    }
}

private const val PERMISSION_PREFS_NAME = "notification_permission"
private const val KEY_PERMISSION_ASKED = "asked"

private class PendingResult {
    var callback: ((Boolean) -> Unit)? = null
}
