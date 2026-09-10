package app.hp.crypto

/**
 * Интерфейс криптографических операций
 * 
 * Используем expect/actual для платформенно-независимого API
 * Реализация на Android: Android Keystore + libsodium (или Bouncy Castle)
 */

/**
 * Результат операции шифрования
 */
data class EncryptedData(
    val ciphertext: ByteArray,
    val nonce: ByteArray,
    val tag: ByteArray? = null  // Для AES-GCM тег включён в ciphertext
)

/**
 * Криптографический интерфейс для KMP
 */
interface CryptoProvider {
    
    /**
     * Генерация случайных байт
     */
    fun randomBytes(size: Int): ByteArray
    
    /**
     * Argon2id KDF - вывод ключа из токена/пароля
     * 
     * @param password Исходный токен/пароль
     * @param salt Соль
     * @param memoryKb Память в KB
     * @param iterations Количество итераций
     * @param parallelism Параллелизм
     * @param keyLength Длина выходного ключа
     * @return Выведенный ключ
     */
    fun argon2id(
        password: CharArray,
        salt: ByteArray,
        memoryKb: Int = 65536,
        iterations: Int = 3,
        parallelism: Int = 4,
        keyLength: Int = 32
    ): ByteArray
    
    /**
     * AES-256-GCM шифрование
     * 
     * @param key Ключ (32 байта)
     * @param plaintext Открытые данные
     * @param associatedData Дополнительные аутентифицируемые данные (AAD)
     * @return Зашифрованные данные с nonce
     */
    fun aesGcmEncrypt(
        key: ByteArray,
        plaintext: ByteArray,
        associatedData: ByteArray? = null
    ): EncryptedData
    
    /**
     * AES-256-GCM расшифровка
     * 
     * @param key Ключ (32 байта)
     * @param ciphertext Зашифрованные данные
     * @param nonce Nonce (12 байт для GCM)
     * @param associatedData Дополнительные аутентифицируемые данные (AAD)
     * @return Расшифрованные данные
     * @throws IllegalStateException если тег не совпадает (данные повреждены или ключ неверный)
     */
    fun aesGcmDecrypt(
        key: ByteArray,
        ciphertext: ByteArray,
        nonce: ByteArray,
        associatedData: ByteArray? = null
    ): ByteArray
    
    /**
     * X25519 генерация пары ключей
     * 
     * @return Pair<publicKey, privateKey> (каждый 32 байта)
     */
    fun x25519KeyPair(): Pair<ByteArray, ByteArray>
    
    /**
     * XChaCha20-Poly1305 Sealed Box - шифрование для получателя
     * Используется для отправки записей между пользователями
     * 
     * @param plaintext Данные для шифрования
     * @param recipientPublicKey Публичный ключ получателя (32 байта)
     * @return Зашифрованный блоб
     */
    fun sealedBoxEncrypt(
        plaintext: ByteArray,
        recipientPublicKey: ByteArray
    ): ByteArray
    
    /**
     * XChaCha20-Poly1305 Sealed Box - расшифровка
     * 
     * @param ciphertext Зашифрованный блоб
     * @param recipientPrivateKey Приватный ключ получателя (32 байта)
     * @return Расшифрованные данные
     * @throws IllegalStateException если расшифровка не удалась
     */
    fun sealedBoxDecrypt(
        ciphertext: ByteArray,
        recipientPrivateKey: ByteArray
    ): ByteArray
    
    /**
     * Обёртка ключа (Key Wrap) - шифрование ключа другим ключом
     * Используется для обёртки ключей записей KEK участника
     * 
     * @param keyToWrap Ключ для обёртки
     * @param wrappingKey Ключ обёртки (KEK)
     * @return Обёрнутый ключ
     */
    fun wrapKey(keyToWrap: ByteArray, wrappingKey: ByteArray): ByteArray
    
    /**
     * Распаковка ключа (Key Unwrap)
     * 
     * @param wrappedKey Обёрнутый ключ
     * @param unwrappingKey Ключ распаковки (KEK)
     * @return Распакованный ключ
     * @throws IllegalStateException если распаковка не удалась
     */
    fun unwrapKey(wrappedKey: ByteArray, unwrappingKey: ByteArray): ByteArray
    
    /**
     * Хеширование для фингерпринта ключа (сверка при обмене контактами)
     * 
     * @param publicKey Публичный ключ
     * @return Строка фингерпринта формата "4-4-4" (например, "A1B2-C3D4-E5F6")
     */
    fun fingerprint(publicKey: ByteArray): String
}

/**
 * Утилиты для работы с байтами
 */
object ByteArrayUtils {
    
    /**
     * Конвертация ByteArray в Hex строку
     */
    fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    
    /**
     * Конвертация Hex строки в ByteArray
     */
    fun String.hexToByteArray(): ByteArray {
        check(length % 2 == 0) { "Hex string must have even length" }
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
    
    /**
     * Безопасное сравнение ByteArray (constant-time)
     */
    fun secureEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].toInt() xor b[i].toInt())
        }
        return result == 0
    }
    
    /**
     * Конкатенация ByteArray
     */
    fun concat(vararg arrays: ByteArray): ByteArray {
        val totalSize = arrays.sumOf { it.size }
        val result = ByteArray(totalSize)
        var offset = 0
        for (array in arrays) {
            array.copyInto(result, offset)
            offset += array.size
        }
        return result
    }
}
