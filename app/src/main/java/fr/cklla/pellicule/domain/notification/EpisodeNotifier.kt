package fr.cklla.pellicule.domain.notification

import fr.cklla.pellicule.domain.model.EpisodeKey

/** Affiche la notification « un épisode sort aujourd'hui » ; l'implémentation Android vit dans `notification`. */
fun interface EpisodeNotifier {

    /**
     * Notifie la sortie de [episode] pour le contenu [mediaId] intitulé [title]. Retourne `false`
     * si la notification n'a pas pu être affichée (permission absente), auquel cas l'épisode n'est
     * pas considéré comme notifié.
     */
    fun notify(mediaId: String, title: String, episode: EpisodeKey): Boolean
}
