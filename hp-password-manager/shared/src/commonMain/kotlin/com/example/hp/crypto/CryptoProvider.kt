package com.example.hp.crypto

interface CryptoProvider {
    fun deriveKey(password: String, salt: ByteArray, size: Int = 32): ByteArray
    fun encrypt(key: ByteArray, data: ByteArray): Pair<ByteArray, ByteArray> // returns (ciphertext, nonce)
    fun decrypt(key: ByteArray, cipherText: ByteArray, nonce: ByteArray): String
    fun randomBytes(size: Int): ByteArray
    fun encryptToken(token: String, salt: ByteArray): String
}
