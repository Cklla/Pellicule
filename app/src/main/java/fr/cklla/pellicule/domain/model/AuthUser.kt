package fr.cklla.pellicule.domain.model

/**
 * Utilisateur connecté, tel que manipulé par l'UI et les ViewModels.
 *
 * Volontairement minimal : seul [uid] est réellement utilisé (identifie l'utilisateur pour
 * Firestore et ses règles de sécurité). [displayName] ne sert qu'à un affichage de courtoisie,
 * jamais stocké nulle part.
 */
data class AuthUser(
    val uid: String,
    val displayName: String?,
)
