package com.example.hp.crypto

interface CryptoProvider {
    fun deriveKey(password: String, salt: ByteArray): ByteArray
    fun encrypt(data: ByteArray, key: ByteArray): ByteArray
    fun decrypt(encryptedData: ByteArray, key: ByteArray): ByteArray
    fun generateRandomBytes(size: Int): ByteArray
}
