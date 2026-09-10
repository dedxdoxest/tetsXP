package com.example.hp.crypto

import android.util.Base64
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

actual class AndroidCryptoProvider : CryptoProvider {
    
    private val random = SecureRandom()
    
    override fun deriveKey(password: String, salt: ByteArray, size: Int): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withIterations(3)
            .withMemoryAsKB(65536)
            .withParallelism(4)
            .withSalt(salt)
            .build()
        
        val generator = Argon2BytesGenerator()
        generator.init(params)
        
        val keyBytes = ByteArray(size)
        generator.generateBytes(password.toCharArray(), keyBytes)
        return keyBytes
    }
    
    override fun encrypt(key: ByteArray, data: ByteArray): Pair<ByteArray, ByteArray> {
        val nonce = ByteArray(12)
        random.nextBytes(nonce)
        
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(128, nonce)
        
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
        val cipherText = cipher.doFinal(data)
        
        return Pair(cipherText, nonce)
    }
    
    override fun decrypt(key: ByteArray, cipherText: ByteArray, nonce: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(128, nonce)
        
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
        val decrypted = cipher.doFinal(cipherText)
        return String(decrypted)
    }
    
    override fun randomBytes(size: Int): ByteArray {
        val bytes = ByteArray(size)
        random.nextBytes(bytes)
        return bytes
    }
    
    override fun encryptToken(token: String, salt: ByteArray): String {
        // Для v1 просто хэшируем токен с солью и возвращаем Base64
        // В реальной реализации можно шифровать токеном от Keystore
        val hash = deriveKey(token, salt, 32)
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }
}
