package com.example.hp.data

import kotlinx.serialization.Serializable

@Serializable
data class Entry(
    val id: String,
    val name: String,
    val login: String,
    val password: String,
    val url: String? = null,
    val notes: String? = null,
    val totpSecret: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false
)

@Serializable
data class StorageManifest(
    val version: Int,
    val salt: String, // Hex строка
    val entries: List<Entry>
)

data class Storage(
    val id: String = "default",
    val name: String = "Личное",
    val entries: List<Entry> = emptyList()
)
