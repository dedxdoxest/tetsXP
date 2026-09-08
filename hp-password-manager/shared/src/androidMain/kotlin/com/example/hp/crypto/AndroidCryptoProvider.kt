package com.example.hp.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

actual class AndroidCryptoProvider : CryptoProvider {
    
    private val random = SecureRandom()
    
    override fun deriveKey(password: String, salt: ByteArray): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withIterations(3)
            .withMemoryAsKB(65536)
            .withParallelism(4)
            .withSalt(salt)
            .build()
        
        val generator = Argon2BytesGenerator()
        generator.init(params)
        
        val keyBytes = ByteArray(32)
        generator.generateBytes(password.toCharArray(), keyBytes)
        return keyBytes
    }
    
    override fun encrypt(data: ByteArray, key: ByteArray): ByteArray {
        val iv = ByteArray(12)
        random.nextBytes(iv)
        
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(128, iv)
        
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
        val encrypted = cipher.doFinal(data)
        
        return iv + encrypted
    }
    
    override fun decrypt(encryptedData: ByteArray, key: ByteArray): ByteArray {
        val iv = encryptedData.copyOfRange(0, 12)
        val ciphertext = encryptedData.copyOfRange(12, encryptedData.size)
        
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(128, iv)
        
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
        return cipher.doFinal(ciphertext)
    }
    
    override fun generateRandomBytes(size: Int): ByteArray {
        val bytes = ByteArray(size)
        random.nextBytes(bytes)
        return bytes
    }
}
