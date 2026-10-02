package fr.cklla.pellicule.domain.model

/** Rappel activé pour un contenu suivi ; [lastNotified] vaut `null` tant qu'aucun épisode n'a été notifié. */
data class EpisodeReminder(val mediaId: String, val lastNotified: EpisodeKey?)
