package com.example.hp.data.repository

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.hp.crypto.CryptoProvider
import com.example.hp.data.Entry
import com.example.hp.data.Storage
import com.example.hp.data.StorageManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64

class StorageRepositoryImpl(
    private val context: Context,
    private val cryptoProvider: CryptoProvider
) : StorageRepository {

    private val fileName = "hp_vault.dat"
    private val prefsName = "hp_prefs"
    private val tokenKey = "vault_token_hash" 

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val encryptedPrefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            prefsName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override suspend fun createStorage(token: String): Result<Storage> = withContext(Dispatchers.IO) {
        try {
            val salt = cryptoProvider.randomBytes(16)
            val saltHex = Base64.getEncoder().encodeToString(salt)
            
            val manifest = StorageManifest(
                version = 1,
                salt = saltHex,
                entries = emptyList()
            )

            saveToFile(manifest, token)

            // Сохраняем хэш токена для быстрой проверки
            val tokenHash = hashToken(token, salt)
            encryptedPrefs.edit().putString(tokenKey, tokenHash).apply()

            Result.success(Storage(entries = emptyList()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun unlockStorage(token: String): Result<Storage> = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, fileName)
            if (!file.exists()) return@withContext Result.failure(Exception("Хранилище не найдено"))

            val data = file.readBytes()
            if (data.size < 64) throw Exception("Файл поврежден")
            
            // Читаем соль (первые 44 байта - base64 кодированная соль длиной 16 байт)
            val saltLength = 44 // длина base64 строки для 16 байт
            val saltHex = String(data.copyOfRange(0, saltLength))
            val salt = Base64.getDecoder().decode(saltHex)
            
            val nonceLength = 12
            val nonce = data.copyOfRange(saltLength, saltLength + nonceLength)
            val cipherText = data.copyOfRange(saltLength + nonceLength, data.size)

            val masterKey = cryptoProvider.deriveKey(token, salt, size = 32)
            val manifestJson = cryptoProvider.decrypt(masterKey, cipherText, nonce)
            val manifest = json.decodeFromString<StorageManifest>(manifestJson)

            // Проверяем токен и сохраняем хэш
            val tokenHash = hashToken(token, salt)
            encryptedPrefs.edit().putString(tokenKey, tokenHash).apply()

            Result.success(Storage(entries = manifest.entries))
        } catch (e: Exception) {
            Result.failure(Exception("Неверный токен: ${e.message}"))
        }
    }

    override suspend fun saveEntries(entries: List<Entry>, token: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, fileName)
            if (!file.exists()) return@withContext Result.failure(Exception("Файл не найден"))
            
            val data = file.readBytes()
            val saltLength = 44
            val saltHex = String(data.copyOfRange(0, saltLength))
            val salt = Base64.getDecoder().decode(saltHex)

            val masterKey = cryptoProvider.deriveKey(token, salt, size = 32)
            val manifest = StorageManifest(version = 1, salt = saltHex, entries = entries)
            val jsonStr = json.encodeToString(StorageManifest.serializer(), manifest)

            val (cipherText, newNonce) = cryptoProvider.encrypt(masterKey, jsonStr.toByteArray())

            val output = ByteArrayOutputStream()
            output.write(saltHex.encodeToByteArray()) 
            output.write(newNonce)
            output.write(cipherText)

            file.writeBytes(output.toByteArray())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun hasStorage(): Boolean = withContext(Dispatchers.IO) {
        File(context.filesDir, fileName).exists()
    }

    override suspend fun clearStorage() {
        File(context.filesDir, fileName).delete()
        encryptedPrefs.edit().clear().apply()
    }

    private suspend fun saveToFile(manifest: StorageManifest, token: String) {
        val salt = Base64.getDecoder().decode(manifest.salt)
        val masterKey = cryptoProvider.deriveKey(token, salt, size = 32)
        val jsonStr = json.encodeToString(StorageManifest.serializer(), manifest)
        
        val (cipherText, nonce) = cryptoProvider.encrypt(masterKey, jsonStr.toByteArray())

        val output = ByteArrayOutputStream()
        output.write(manifest.salt.encodeToByteArray())
        output.write(nonce) 
        output.write(cipherText)

        File(context.filesDir, fileName).writeBytes(output.toByteArray())
    }
    
    private fun hashToken(token: String, salt: ByteArray): String {
        val hash = cryptoProvider.deriveKey(token, salt, 32)
        return Base64.getEncoder().encodeToString(hash)
    }
}
