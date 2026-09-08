package com.example.hp.data

data class Entry(
    val id: String,
    val name: String,
    val login: String,
    val password: String,
    val url: String?,
    val notes: String?,
    val totpSecret: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false
)

data class Storage(
    val id: String,
    val name: String,
    val entries: List<Entry> = emptyList()
)
