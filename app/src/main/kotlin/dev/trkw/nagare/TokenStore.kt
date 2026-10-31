package dev.trkw.nagare

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Server URL and token. The token is encrypted with a key kept in the Android Keystore. */
class TokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("nagare", Context.MODE_PRIVATE)

    var server: String?
        get() = prefs.getString("server", null)
        set(value) = prefs.edit().putString("server", value).apply()

    var token: String?
        get() = prefs.getString("token", null)?.let { runCatching { decrypt(it) }.getOrNull() }
        set(value) = prefs.edit().putString("token", value?.let(::encrypt)).apply()

    /** Whether the user turned on notifications on this device. */
    var pushEnabled: Boolean
        get() = prefs.getBoolean("push_enabled", false)
        set(value) = prefs.edit().putBoolean("push_enabled", value).apply()

    /** The FCM token last registered with the server. */
    var pushToken: String?
        get() = prefs.getString("push_token", null)
        set(value) = prefs.edit().putString("push_token", value).apply()

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val data = cipher.doFinal(plain.encodeToByteArray())
        return Base64.encodeToString(cipher.iv + data, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
        return cipher.doFinal(bytes, 12, bytes.size - 12).decodeToString()
    }

    private companion object {
        const val ALIAS = "nagare-token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
