package app.hp.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.crypto.digests.ShortenedDigest
import org.bouncycastle.crypto.engines.ChaCha7539Engine
import org.bouncycastle.crypto.macs.Poly1305
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import org.bouncycastle.util.encoders.Hex

/**
 * Android-реализация CryptoProvider
 * 
 * Использует:
 * - Android Keystore для хранения мастер-ключей
 * - Bouncy Castle для Argon2id, XChaCha20-Poly1305
 * - Стандартный JCE для AES-GCM
 */
class AndroidCryptoProvider : CryptoProvider {
    
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }
    
    private val secureRandom = SecureRandom()
    
    init {
        // Регистрируем Bouncy Castle провайдер
        java.security.Security.addProvider(BouncyCastleProvider())
    }
    
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
        // Используем Bouncy Castle для Argon2id
        val generator = Argon2BytesGenerator()
        
        // Argon2id: type = 2 (ID), version = 0x13 (1.3)
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(iterations)
            .withMemoryAsKB(memoryKb)
            .withParallelism(parallelism)
            .withSalt(salt)
            .build()
        
        generator.init(params)
        
        val output = ByteArray(keyLength)
        generator.generateBytes(password.concatToString().toCharArray(), output)
        
        return output
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
        // Используем Bouncy Castle для X25519
        val generator = org.bouncycastle.jcajce.provider.asymmetric.ec.KeyPairGenerator("X25519")
        val keyPair = generator.generateKeyPair()
        
        val publicKey = keyPair.public.encoded
        val privateKey = keyPair.private.encoded
        
        return Pair(publicKey, privateKey)
    }
    
    override fun sealedBoxEncrypt(
        plaintext: ByteArray,
        recipientPublicKey: ByteArray
    ): ByteArray {
        // XChaCha20-Poly1305 Sealed Box реализация через Bouncy Castle
        // Генерируем ephemeral key pair
        val ephemeralGenerator = org.bouncycastle.jcajce.provider.asymmetric.ec.KeyPairGenerator("X25519")
        val ephemeralKeyPair = ephemeralGenerator.generateKeyPair()
        
        // ECDH для получения общего секрета
        val ecdh = javax.crypto.KeyAgreement.getInstance("ECDH", "BC")
        ecdh.init(ephemeralKeyPair.private)
        
        // Создаём публичный ключ получателя из байтов
        val keyFactory = java.security.KeyFactory.getInstance("X25519", "BC")
        val pubKeySpec = java.security.spec.X509EncodedKeySpec(recipientPublicKey)
        val recipientPubKey = keyFactory.generatePublic(pubKeySpec)
        
        ecdh.doPhase(recipientPubKey, true)
        val sharedSecret = ecdh.generateSecret()
        
        // KDF для получения ключа шифрования и nonce
        val hkdf = org.bouncycastle.crypto.kdf.HKDFBytesGenerator(
            org.bouncycastle.crypto.digests.SHA256Digest()
        )
        val info = "HP-SealedBox".toByteArray()
        hkdf.init(sharedSecret, null, info)
        
        val keyAndNonce = ByteArray(40) // 32 байта ключ + 24 байта nonce для XChaCha20
        hkdf.generateBytes(keyAndNonce, 0, keyAndNonce.size)
        
        val encryptionKey = keyAndNonce.sliceArray(0..31)
        val nonce = keyAndNonce.sliceArray(32..39)
        
        // Шифрование на ChaCha20-Poly1305
        val engine = ChaCha7539Engine()
        val params = ParametersWithIV(KeyParameter(encryptionKey), nonce)
        engine.init(true, params)
        
        val ciphertext = ByteArray(plaintext.size + 16) // +16 байт для тега Poly1305
        val poly1305 = Poly1305()
        
        var offset = 0
        while (offset < plaintext.size) {
            offset += engine.processBlock(plaintext, offset, ciphertext, offset)
        }
        
        // Генерация тега
        poly1305.init(KeyParameter(encryptionKey))
        poly1305.update(ciphertext, 0, plaintext.size)
        val tag = ByteArray(16)
        poly1305.doFinal(tag, 0)
        System.arraycopy(tag, 0, ciphertext, plaintext.size, 16)
        
        // Формат: [ephemeral public key (32)] + [nonce (24)] + [ciphertext + tag]
        val ephemeralPubKey = ephemeralKeyPair.public.encoded
        return ByteArrayUtils.concat(ephemeralPubKey, nonce, ciphertext)
    }
    
    override fun sealedBoxDecrypt(
        ciphertext: ByteArray,
        recipientPrivateKey: ByteArray
    ): ByteArray {
        require(ciphertext.size > 56) { "Ciphertext too short" }
        
        // Извлекаем компоненты
        val ephemeralPubKey = ciphertext.sliceArray(0..31)
        val nonce = ciphertext.sliceArray(32..55)
        val encryptedData = ciphertext.sliceArray(56 until ciphertext.size)
        
        // ECDH для получения общего секрета
        val ecdh = javax.crypto.KeyAgreement.getInstance("ECDH", "BC")
        
        // Восстанавливаем приватный ключ получателя
        val keyFactory = java.security.KeyFactory.getInstance("X25519", "BC")
        val privKeySpec = java.security.spec.PKCS8EncodedKeySpec(recipientPrivateKey)
        val recipientPrivKey = keyFactory.generatePrivate(privKeySpec)
        
        ecdh.init(recipientPrivKey)
        
        // Создаём публичный ключ отправителя
        val senderPubKeyFactory = java.security.KeyFactory.getInstance("X25519", "BC")
        val senderPubKeySpec = java.security.spec.X509EncodedKeySpec(ephemeralPubKey)
        val senderPubKey = senderPubKeyFactory.generatePublic(senderPubKeySpec)
        
        ecdh.doPhase(senderPubKey, true)
        val sharedSecret = ecdh.generateSecret()
        
        // KDF для получения ключа расшифровки
        val hkdf = org.bouncycastle.crypto.kdf.HKDFBytesGenerator(
            org.bouncycastle.crypto.digests.SHA256Digest()
        )
        val info = "HP-SealedBox".toByteArray()
        hkdf.init(sharedSecret, null, info)
        
        val keyAndNonce = ByteArray(40)
        hkdf.generateBytes(keyAndNonce, 0, keyAndNonce.size)
        
        val decryptionKey = keyAndNonce.sliceArray(0..31)
        
        // Расшифрование ChaCha20-Poly1305
        val engine = ChaCha7539Engine()
        val params = ParametersWithIV(KeyParameter(decryptionKey), nonce)
        engine.init(false, params)
        
        val ciphertextWithoutTag = encryptedData.sliceArray(0 until encryptedData.size - 16)
        val tag = encryptedData.sliceArray(encryptedData.size - 16 until encryptedData.size)
        
        val plaintext = ByteArray(ciphertextWithoutTag.size)
        var offset = 0
        while (offset < ciphertextWithoutTag.size) {
            offset += engine.processBlock(ciphertextWithoutTag, offset, plaintext, offset)
        }
        
        // Проверка тега
        val poly1305 = Poly1305()
        poly1305.init(KeyParameter(decryptionKey))
        poly1305.update(ciphertextWithoutTag, 0, ciphertextWithoutTag.size)
        val calculatedTag = ByteArray(16)
        poly1305.doFinal(calculatedTag, 0)
        
        if (!ByteArrayUtils.secureEquals(tag, calculatedTag)) {
            throw IllegalStateException("Poly1305 tag verification failed")
        }
        
        return plaintext
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
        // SHA-256 хеш для фингерпринта
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
