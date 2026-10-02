package fr.cklla.pellicule.ui.navigation

import fr.cklla.pellicule.notification.NotificationDestination
import fr.cklla.pellicule.notification.NotificationDestinationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationNavigationTest {

    @Test
    fun `la destination Detail ouvre la fiche du contenu`() {
        val destination = NotificationDestination(NotificationDestinationType.DETAIL, "3f2b8c1e-0d4a-4e8f-9a53-1c2d3e4f5a6b")

        assertEquals("detail/3f2b8c1e-0d4a-4e8f-9a53-1c2d3e4f5a6b", destination.toRoute())
    }

    @Test
    fun `un identifiant forge ne produit aucune route`() {
        assertNull(NotificationDestination(NotificationDestinationType.DETAIL, "abc/../compte").toRoute())
        assertNull(NotificationDestination(NotificationDestinationType.DETAIL, "").toRoute())
        assertNull(NotificationDestination(NotificationDestinationType.DETAIL, "a b").toRoute())
    }

    @Test
    fun `la destination Recap ouvre le recap de l'annee`() {
        assertEquals("recap/2026", NotificationDestination(NotificationDestinationType.RECAP, "2026").toRoute())
    }

    @Test
    fun `une annee invalide ne produit aucune route de recap`() {
        assertNull(NotificationDestination(NotificationDestinationType.RECAP, "20x6").toRoute())
        assertNull(NotificationDestination(NotificationDestinationType.RECAP, "202").toRoute())
        assertNull(NotificationDestination(NotificationDestinationType.RECAP, "2026/../compte").toRoute())
    }
}
