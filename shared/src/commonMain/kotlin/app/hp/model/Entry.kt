package app.hp.model

/**
 * Идентификаторы в системе
 */
typealias UUID = String
typealias EntryId = String
typealias ParticipantId = String
typealias GroupId = String
typealias StorageId = String

/**
 * Участник хранилища
 */
data class Participant(
    val id: ParticipantId,
    val name: String,
    val role: ParticipantRole,
    val publicKey: ByteArray,  // X25519 public key для sealed box
    val wrappedKek: ByteArray? = null  // Обёрнутый KEK участника (зашифрован ключом хранилища)
)

/**
 * Группа участников (для управления доступом)
 */
data class Group(
    val id: GroupId,
    val name: String,
    val memberIds: Set<ParticipantId>,
    val wrappedGroupKey: ByteArray  // Ключ группы, обёрнутый для всех участников группы
)

/**
 * Поля записи пароля
 */
data class PasswordEntryData(
    val name: String,
    val username: String,
    val password: String,
    val url: String? = null,
    val notes: String? = null,
    val totpSecret: String? = null  // Секрет для TOTP
)

/**
 * Поля записи Wi-Fi
 */
data class WifiEntryData(
    val ssid: String,
    val password: String,
    val encryptionType: String = "WPA/WPA2",
    val hidden: Boolean = false
)

/**
 * Поля записи заметки
 */
data class NoteEntryData(
    val title: String,
    val content: String
)

/**
 * Базовый интерфейс для данных записи
 */
sealed class EntryData {
    abstract fun toPasswordEntryData(): PasswordEntryData?
    abstract fun toWifiEntryData(): WifiEntryData?
    abstract fun toNoteEntryData(): NoteEntryData?
}

/**
 * Запись в хранилище
 * 
 * Важные принципы:
 * - Все чувствительные данные зашифрованы своим ключом записи
 * - Ключ записи обёрнут для каждого участника/группы с доступом
 * - Метаданные (имя, структура) внутри шифроблоба, не в манифесте
 */
data class Entry(
    val id: EntryId,
    val type: EntryType,
    val status: EntryStatus = EntryStatus.ACTIVE,
    val ownerId: ParticipantId,  // Владелец записи (может редактировать ACL)
    val createdAt: Long,
    val modifiedAt: Long,
    val encryptedData: ByteArray,  // Зашифрованные данные записи (AES-256-GCM)
    val nonce: ByteArray,          // Nonce для AES-GCM
    val keyWrappers: Map<String, ByteArray>  // Map<participantId|groupId, wrappedKey> - обёртки ключа записи
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        val otherEntry = other as Entry
        return id == otherEntry.id &&
               modifiedAt == otherEntry.modifiedAt
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}
