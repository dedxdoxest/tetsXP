package app.hp.crypto

import kotlinx.serialization.*
import kotlinx.serialization.json.Json

/**
 * Структура файла БД (версия 1)
 * 
 * [Header] - публичный, JSON
 * [ManifestBlob] - зашифрован AES-GCM ключом хранилища
 * [EntryBlobs] - массив зашифрованных записей (каждая своим ключом)
 */

@Serializable
data class Header(
    val magic: String = "HPDB",
    val version: Int = 1,
    val storageId: String,
    val memoryKb: Int = 65536,
    val iterations: Int = 3,
    val parallelism: Int = 4,
    val saltHex: String,
    val encryptionAlgorithm: String = "AES-256-GCM",
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class Manifest(
    val storageKeyWrapped: List<WrappedKey>,
    val entries: List<EntryMeta>,
    val groups: List<GroupMeta>
)

@Serializable
data class WrappedKey(
    val participantId: String,
    val wrappedKeyHex: String,
    val nonceHex: String = ""
)

@Serializable
data class EntryMeta(
    val id: String,
    val ownerParticipantId: String,
    val keyWrappers: List<KeyWrapper>,
    val isDeleted: Boolean = false,
    val updatedAt: Long
)

@Serializable
data class KeyWrapper(
    val targetId: String,
    val targetType: String,
    val wrappedKeyHex: String,
    val nonceHex: String = ""
)

@Serializable
data class GroupMeta(
    val id: String,
    val name: String,
    val memberIds: List<String>
)

/**
 * Содержимое записи (шифруется отдельно своим ключом)
 */
@Serializable
data class EntryContent(
    val name: String,
    val login: String? = null,
    val password: String? = null,
    val url: String? = null,
    val notes: String? = null,
    val totpSecret: String? = null,
    val customFields: Map<String, String>? = null
)

/**
 * Зашифрованный блоб
 */
data class EncryptedData(
    val ciphertext: ByteArray,
    val nonce: ByteArray,
    val tag: ByteArray
)

@Serializable
data class EncryptedEntry(
    val id: String,
    val ciphertextHex: String,
    val nonceHex: String,
    val authTagHex: String
)

object StorageSerializer {
    val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    fun headerToJson(header: Header): String = json.encodeToString(header)
    fun headerFromJson(jsonStr: String): Header = json.decodeFromString(jsonStr)
    
    fun manifestToJson(manifest: Manifest): String = json.encodeToString(manifest)
    fun manifestFromJson(jsonStr: String): Manifest = json.decodeFromString(jsonStr)
    
    fun entryContentToJson(content: EntryContent): String = json.encodeToString(content)
    fun entryContentFromJson(jsonStr: String): EntryContent = json.decodeFromString(jsonStr)
    
    fun encryptedEntryToJson(entry: EncryptedEntry): String = json.encodeToString(entry)
    fun encryptedEntryFromJson(jsonStr: String): EncryptedEntry = json.decodeFromString(jsonStr)
}
