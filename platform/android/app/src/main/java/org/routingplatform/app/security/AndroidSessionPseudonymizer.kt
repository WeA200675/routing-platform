package org.routingplatform.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/** Converts an internal navigation-session id to a stable local pseudonym. */
class AndroidSessionPseudonymizer {
    fun pseudonymize(sessionId: String): String {
        require(sessionId.isNotBlank())
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(key())
        return mac.doFinal(sessionId.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "routing_platform_session_pseudonym_v1"
        const val ALGORITHM = "HmacSHA256"
    }
}
