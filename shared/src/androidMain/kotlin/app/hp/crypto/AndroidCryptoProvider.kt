package app.hp.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Android-реализация CryptoProvider
 * 
 * Использует:
 * - Android Keystore для хранения мастер-ключей
 * - Bouncy Castle (встроенный в Android) для Argon2id, AES-GCM, XChaCha20-Poly1305
 */
class AndroidCryptoProvider : CryptoProvider {
    
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }
    
    private val secureRandom = SecureRandom()
    
    override fun randomBytes(size: Int): ByteArray {
        return ByteArray(size).also { secureRandom.nextBytes(it) }
    }
    
    override fun argon2id(
        password: CharArray,
        salt: ByteArray,
        memoryKb: Int,
        iterations: Int,
        parallelism: Int,
        keyLength: Int
    ): ByteArray {
        // TODO: Реализовать через Bouncy Castle или libsodium
        // Для начала используем заглушку на PBKDF2
        // В продакшене заменить на Argon2id
        return pbkdf2Fallback(password, salt, iterations, keyLength)
    }
    
    private fun pbkdf2Fallback(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
        keyLength: Int
    ): ByteArray {
        // Заглушка: в реальности использовать Argon2id из BC
        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = javax.crypto.spec.PBEKeySpec(
            password,
            salt,
            iterations.toLong(),
            keyLength * 8
        )
        return factory.generateSecret(spec).encoded
    }
    
    override fun aesGcmEncrypt(
        key: ByteArray,
        plaintext: ByteArray,
        associatedData: ByteArray?
    ): EncryptedData {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        
        // Генерация случайного nonce (12 байт для GCM)
        val nonce = randomBytes(12)
        val gcmSpec = GCMParameterSpec(128, nonce)
        
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        associatedData?.let { cipher.updateAAD(it) }
        
        val ciphertext = cipher.doFinal(plaintext)
        
        return EncryptedData(
            ciphertext = ciphertext,
            nonce = nonce
        )
    }
    
    override fun aesGcmDecrypt(
        key: ByteArray,
        ciphertext: ByteArray,
        nonce: ByteArray,
        associatedData: ByteArray?
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(128, nonce)
        
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        associatedData?.let { cipher.updateAAD(it) }
        
        return cipher.doFinal(ciphertext)
    }
    
    override fun x25519KeyPair(): Pair<ByteArray, ByteArray> {
        // TODO: Реализовать через Bouncy Castle (X25519)
        // Заглушка: генерация случайных байт (НЕ БЕЗОПАСНО для продакшена)
        val publicKey = randomBytes(32)
        val privateKey = randomBytes(32)
        return Pair(publicKey, privateKey)
    }
    
    override fun sealedBoxEncrypt(
        plaintext: ByteArray,
        recipientPublicKey: ByteArray
    ): ByteArray {
        // TODO: Реализовать XChaCha20-Poly1305 Sealed Box через libsodium/BC
        // Заглушка
        return plaintext
    }
    
    override fun sealedBoxDecrypt(
        ciphertext: ByteArray,
        recipientPrivateKey: ByteArray
    ): ByteArray {
        // TODO: Реализовать XChaCha20-Poly1305 Sealed Box через libsodium/BC
        // Заглушка
        return ciphertext
    }
    
    override fun wrapKey(keyToWrap: ByteArray, wrappingKey: ByteArray): ByteArray {
        // Обёртка ключа через AES-GCM
        val encrypted = aesGcmEncrypt(wrappingKey, keyToWrap, null)
        return ByteArrayUtils.concat(encrypted.nonce, encrypted.ciphertext)
    }
    
    override fun unwrapKey(wrappedKey: ByteArray, unwrappingKey: ByteArray): ByteArray {
        // Распаковка: первые 12 байт - nonce, остальное - ciphertext
        require(wrappedKey.size > 12) { "Wrapped key too short" }
        val nonce = wrappedKey.sliceArray(0..11)
        val ciphertext = wrappedKey.sliceArray(12 until wrappedKey.size)
        return aesGcmDecrypt(unwrappingKey, ciphertext, nonce, null)
    }
    
    override fun fingerprint(publicKey: ByteArray): String {
        // Простой SHA-256 хеш для фингерпринта
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val hash = md.digest(publicKey)
        // Берём первые 6 байт и форматируем как XXXX-XXXX-XXXX
        return hash.slice(0..5).joinToString("-") { "%02X".format(it) }
    }
    
    /**
     * Генерация и хранение мастер-ключа в Android Keystore
     */
    fun generateMasterKey(alias: String): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        
        val parameterSpec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)  // Требуется биометрия/PIN
            .build()
        
        keyGenerator.init(parameterSpec)
        return keyGenerator.generateKey()
    }
    
    fun getMasterKey(alias: String): SecretKey? {
        return keyStore.getKey(alias, null) as? SecretKey
    }
    
    fun deleteMasterKey(alias: String) {
        keyStore.deleteEntry(alias)
    }
}
