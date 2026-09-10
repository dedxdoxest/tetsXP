package app.hp.data

import app.hp.crypto.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Интерфейс репозитория хранилища
 */
interface StorageRepository {
    suspend fun createStorage(token: CharArray): String
    suspend fun openStorage(storageId: String, token: CharArray): Boolean
    suspend fun closeStorage(storageId: String)
    suspend fun getEntries(storageId: String): List<Entry>
    suspend fun saveEntry(storageId: String, entry: Entry)
    suspend fun deleteEntry(storageId: String, entryId: String)
    suspend fun syncWithCloud(storageId: String): SyncResult
}

sealed class SyncResult {
    object Success : SyncResult()
    data class Conflict(val message: String) : SyncResult()
    data class Error(val message: String) : SyncResult()
}

/**
 * Модель записи
 */
data class Entry(
    val id: String,
    val name: String,
    val login: String?,
    val password: String?,
    val url: String?,
    val notes: String?,
    val totpSecret: String?,
    val ownerId: String,
    val updatedAt: Long
)
