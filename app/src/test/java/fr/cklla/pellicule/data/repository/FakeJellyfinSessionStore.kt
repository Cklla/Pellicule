package fr.cklla.pellicule.data.repository

import fr.cklla.pellicule.data.local.JellyfinSessionStore
import fr.cklla.pellicule.domain.model.JellyfinSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Faux stockage de session Jellyfin en mémoire, utilisé pour tester [JellyfinRepositoryImpl] sans `EncryptedSharedPreferences` réel (indisponible en test unitaire JVM pur). */
class FakeJellyfinSessionStore(initial: JellyfinSession? = null) : JellyfinSessionStore {

    private val _session = MutableStateFlow(initial)
    override val session: StateFlow<JellyfinSession?> = _session.asStateFlow()
    override val deviceId: String = "test-device"

    override fun save(session: JellyfinSession) {
        _session.value = session
    }

    override fun clear() {
        _session.value = null
    }
}
