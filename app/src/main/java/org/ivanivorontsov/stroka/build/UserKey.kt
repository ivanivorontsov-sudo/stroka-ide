
package org.ivanivorontsov.stroka.build

import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate

class UserKey(val key: PrivateKey, val cert: X509Certificate) {
    companion object {
        const val DEFAULT_ALIAS = "stroka-user"
        const val DEFAULT_PASSWORD = "stroka-user"

        fun load(store: File, password: CharArray = DEFAULT_PASSWORD.toCharArray(), alias: String = DEFAULT_ALIAS): UserKey {
            if (!store.isFile) throw IllegalStateException("Не найден ключ подписи пользовательских приложений: ${store.absolutePath}")
            val ks = KeyStore.getInstance("PKCS12")
            FileInputStream(store).use { ks.load(it, password) }
            val name = if (ks.containsAlias(alias)) alias else ks.aliases().toList().firstOrNull()
                ?: throw IllegalStateException("В хранилище ключей нет ни одного сертификата")
            val key = ks.getKey(name, password) as? PrivateKey
                ?: throw IllegalStateException("В хранилище нет закрытого ключа «$name»")
            val cert = ks.getCertificate(name) as? X509Certificate
                ?: throw IllegalStateException("В хранилище нет сертификата «$name»")
            return UserKey(key, cert)
        }
    }
}
