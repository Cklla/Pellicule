package fr.cklla.pellicule.domain.model

/**
 * Statut de visionnage d'un contenu. Seulement 3 valeurs : contrairement à Cartouche (backlog
 * jeux), pas d'équivalent "abandonné" prévu au cahier des charges (voir CLAUDE.md).
 */
enum class WatchStatus {
    A_VOIR,
    EN_COURS,
    VU,
}
