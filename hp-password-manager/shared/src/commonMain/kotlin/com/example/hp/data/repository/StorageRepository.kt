package com.example.hp.data.repository

import com.example.hp.data.Entry
import com.example.hp.data.Storage

interface StorageRepository {
    suspend fun createStorage(token: String): Result<Storage>
    suspend fun unlockStorage(token: String): Result<Storage>
    suspend fun saveEntries(entries: List<Entry>, token: String): Result<Unit>
    suspend fun hasStorage(): Boolean
    suspend fun clearStorage()
}
