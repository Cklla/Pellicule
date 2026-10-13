package fr.cklla.pellicule.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleNonceTest {

    @Test
    fun `la version hachee est le SHA-256 hexadecimal de la version brute`() {
        // Valeur de référence connue : SHA-256("abc").
        val nonce = GoogleNonce.of("abc")

        assertEquals("abc", nonce.raw)
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", nonce.hashed)
    }

    @Test
    fun `chaque nonce genere est different et le brut n'est pas le hache`() {
        val first = GoogleNonce.generate()
        val second = GoogleNonce.generate()

        assertNotEquals(first.raw, second.raw)
        assertNotEquals(first.raw, first.hashed)
        assertEquals(64, first.raw.length)
        assertEquals(64, first.hashed.length)
        assertTrue(first.raw.all { it in '0'..'9' || it in 'a'..'f' })
    }
}
