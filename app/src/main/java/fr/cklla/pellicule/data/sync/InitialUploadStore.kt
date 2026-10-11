package fr.cklla.pellicule.data.sync

/**
 * Mémorise, par compte, que le suivi présent dans Room au premier lancement a déjà été confronté au
 * serveur (voir `MediaSyncer.enqueueLocalOnFirstSync`). Sans cette mémoire, l'envoi du suivi local
 * se rejouerait à chaque démarrage et ressusciterait ce qui a été supprimé ailleurs depuis.
 */
interface InitialUploadStore {
    /** Compte pour lequel l'envoi initial a été fait, `null` s'il n'a jamais eu lieu sur cet appareil. */
    fun markedFor(): String?

    fun isDone(userId: String): Boolean = markedFor() == userId
    fun markDone(userId: String)

    /** Oublie la mémoire : appelé à la déconnexion, en même temps que le suivi local est vidé. */
    fun clear()
}
