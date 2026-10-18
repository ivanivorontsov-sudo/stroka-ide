package org.ivanivorontsov.stroka.ui

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("secure", Context.MODE_PRIVATE)
    private val alias = "stroka_secret_key"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun put(name: String, value: String?) {
        if (value.isNullOrEmpty()) {
            sp.edit().remove(name).apply()
            return
        }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val enc = c.doFinal(value.toByteArray(Charsets.UTF_8))
        val blob = c.iv + enc
        sp.edit().putString(name, Base64.encodeToString(blob, Base64.NO_WRAP)).putInt(name + "_iv", c.iv.size).apply()
    }

    fun get(name: String): String? {
        val s = sp.getString(name, null) ?: return null
        return try {
            val blob = Base64.decode(s, Base64.NO_WRAP)
            val ivLen = sp.getInt(name + "_iv", 12)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob, 0, ivLen))
            String(c.doFinal(blob, ivLen, blob.size - ivLen), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    fun has(name: String): Boolean = sp.contains(name)
}
