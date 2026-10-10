package fr.cklla.pellicule.data.repository

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Nonce de la connexion Google. Supabase vérifie que le jeton Google contient bien le nonce de la
 * demande, ce que Firebase ne faisait pas : Google reçoit la version **hachée** (SHA-256, en
 * hexadécimal), Supabase la version **brute**, dont il recalcule lui-même le haché.
 */
internal class GoogleNonce private constructor(val raw: String) {

    val hashed: String = sha256Hex(raw)

    companion object {

        fun generate(): GoogleNonce {
            val bytes = ByteArray(NONCE_BYTES)
            SecureRandom().nextBytes(bytes)
            return GoogleNonce(bytes.toHex())
        }

        /** Pour les tests : nonce brut imposé. */
        fun of(raw: String) = GoogleNonce(raw)

        private fun sha256Hex(value: String): String =
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).toHex()

        private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

        private const val NONCE_BYTES = 32
    }
}
