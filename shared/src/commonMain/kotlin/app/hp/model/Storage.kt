package app.hp.model

/**
 * Хранилище (файл БД)
 * 
 * Структура файла:
 * [Заголовок]  — публичный: версия формата, параметры KDF/шифрования, id хранилища
 * [Манифест]   — зашифрован общим ключом хранилища (обёрнут для всех участников):
 *                список записей {id, тип, обёртки ключа записи по участникам/группам, tombstone}
 * [Блобы записей] — каждая запись зашифрована СВОИМ ключом (AES-256-GCM)
 */
data class Storage(
    val id: StorageId,
    val name: String,
    val version: Int = 1,  // Версия формата файла
    val kdfParams: KdfParams,  // Параметры KDF (Argon2id)
    val encryptionParams: EncryptionParams,  // Параметры шифрования
    val participants: Map<ParticipantId, Participant>,
    val groups: Map<GroupId, Group>,
    val entries: Map<EntryId, Entry>,
    val wrappedStorageKey: Map<ParticipantId, ByteArray>,  // Ключ хранилища, обёрнутый для каждого участника
    val lastSyncTime: Long? = null,
    val cloudPath: String? = null  // Путь в облаке для синка
)

/**
 * Параметры KDF (Argon2id)
 */
data class KdfParams(
    val memoryKb: Int = 65536,      // 64 MB
    val iterations: Int = 3,
    val parallelism: Int = 4,
    val salt: ByteArray             // Соль (16-32 байта)
)

/**
 * Параметры шифрования
 */
data class EncryptionParams(
    val cipher: String = "AES-256-GCM",
    val tagLengthBits: Int = 128
)

/**
 * Личное пространство участника внутри общего хранилища
 * Записи в личном пространстве видны только владельцу
 */
data class PersonalSpace(
    val ownerId: ParticipantId,
    val entryIds: Set<EntryId>
)

/**
 * Общее пространство хранилища
 * Записи доступны согласно ACL
 */
data class SharedSpace(
    val entryIds: Set<EntryId>
)
