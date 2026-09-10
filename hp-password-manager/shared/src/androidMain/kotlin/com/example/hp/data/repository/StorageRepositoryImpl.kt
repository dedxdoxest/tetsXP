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

class StorageRepositoryImpl(
    private val context: Context,
    private val cryptoProvider: CryptoProvider
) : StorageRepository {

    private val fileName = "hp_vault.dat"
    private val prefsName = "hp_prefs"
    private val tokenKey = "vault_token" 

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
            val saltHex = salt.joinToString("") { "%02x".format(it) }
            
            val manifest = StorageManifest(
                version = 1,
                salt = saltHex,
                entries = emptyList()
            )

            saveToFile(manifest, token)

            val encryptedToken = cryptoProvider.encryptToken(token, salt)
            encryptedPrefs.edit().putString(tokenKey, encryptedToken).apply()

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
            
            val saltHex = data.copyOfRange(0, 32).decodeToString()
            val salt = saltHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val nonce = data.copyOfRange(32, 44)
            val cipherText = data.copyOfRange(44, data.size)

            val masterKey = cryptoProvider.deriveKey(token, salt, size = 32)
            val manifestJson = cryptoProvider.decrypt(masterKey, cipherText, nonce)
            val manifest = json.decodeFromString<StorageManifest>(manifestJson)

            val encryptedToken = cryptoProvider.encryptToken(token, salt)
            encryptedPrefs.edit().putString(tokenKey, encryptedToken).apply()

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
            val saltHex = data.copyOfRange(0, 32).decodeToString()
            val salt = saltHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

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
        val saltBytes = manifest.salt.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val masterKey = cryptoProvider.deriveKey(token, saltBytes, size = 32)
        val jsonStr = json.encodeToString(StorageManifest.serializer(), manifest)
        
        val (cipherText, nonce) = cryptoProvider.encrypt(masterKey, jsonStr.toByteArray())

        val output = ByteArrayOutputStream()
        output.write(manifest.salt.encodeToByteArray())
        output.write(nonce) 
        output.write(cipherText)

        File(context.filesDir, fileName).writeBytes(output.toByteArray())
    }
}
