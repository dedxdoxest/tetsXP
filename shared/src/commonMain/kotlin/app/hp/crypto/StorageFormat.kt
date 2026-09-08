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
    val kdfParams: KdfParams,
    val encryptionAlgorithm: String = "AES-256-GCM",
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class KdfParams(
    val algorithm: String = "Argon2id",
    val memoryKb: Int = 65536,      // 64 MB
    val iterations: Int = 3,
    val parallelism: Int = 4,
    val saltHex: String             // 16 байт
)

@Serializable
data class Manifest(
    val storageKeyWrapped: List<WrappedKey>,  // Ключ хранилища, обёрнутый для каждого участника
    val entries: List<EntryMeta>,             // Метаданные записей (но не содержимое!)
    val groups: List<GroupMeta>               // Группы участников
)

@Serializable
data class WrappedKey(
    val participantId: String,
    val wrappedKeyHex: String,    // KEK участника шифрует ключ хранилища
    val nonceHex: String
)

@Serializable
data class EntryMeta(
    val id: String,
    val ownerParticipantId: String,
    val keyWrappers: List<KeyWrapper>,  // Кто может расшифровать эту запись
    val isDeleted: Boolean = false,     // Tombstone
    val updatedAt: Long
)

@Serializable
data class KeyWrapper(
    val targetId: String,         // ID участника или группы
    val targetType: String,       // "participant" или "group"
    val wrappedKeyHex: String,    // Ключ записи, обёрнутый KEK цели
    val nonceHex: String
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
    val login: String?,
    val password: String?,
    val url: String?,
    val notes: String?,
    val totpSecret: String?,
    val customFields: Map<String, String>? = null
)

/**
 * Зашифрованный блоб записи
 */
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
}
