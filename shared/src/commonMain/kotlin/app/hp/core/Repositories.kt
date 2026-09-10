package app.hp.core

import app.hp.crypto.CryptoProvider
import app.hp.model.*
import kotlinx.coroutines.flow.Flow

/**
 * Репозиторий хранилищ
 * Управляет несколькими хранилищами (личное, семья, работа)
 */
interface StorageRepository {
    
    /**
     * Список всех хранилищ
     */
    val storages: Flow<List<Storage>>
    
    /**
     * Получить хранилище по ID
     */
    suspend fun getStorage(id: StorageId): Storage?
    
    /**
     * Создать новое личное хранилище
     * 
     * @param name Имя хранилища
     * @param token Токен владельца (случайный 256 бит)
     * @return StorageId нового хранилища и recovery-код
     */
    suspend fun createPersonalStorage(name: String, token: CharArray): CreateStorageResult
    
    /**
     * Добавить существующее хранилище по токену
     * 
     * @param token Токен доступа от владельца
     * @param cloudPath Путь к файлу в облаке
     * @return StorageId добавленного хранилища
     */
    suspend fun addExistingStorage(token: CharArray, cloudPath: String): Result<StorageId>
    
    /**
     * Удалить хранилище (локально, файл остаётся в облаке)
     */
    suspend fun removeStorage(id: StorageId): Result<Unit>
    
    /**
     * Синхронизировать хранилище с облаком
     */
    suspend fun syncStorage(id: StorageId): Result<Unit>
}

/**
 * Результат создания хранилища
 */
data class CreateStorageResult(
    val storageId: StorageId,
    val recoveryCode: String  // Показывается один раз!
)

/**
 * Менеджер записей
 * CRUD операции для записей в хранилище
 */
interface EntryManager {
    
    /**
     * Получить все доступные записи из хранилища
     * Учитывает ACL - возвращает только те записи, которые пользователь может расшифровать
     */
    suspend fun getEntries(storageId: StorageId): List<Entry>
    
    /**
     * Создать новую запись
     * 
     * @param storageId Хранилище
     * @param type Тип записи
     * @param data Данные записи
     * @param accessLevel Уровень доступа (для общих хранилищ)
     * @param targetGroups/Participants Целевые группы/участники (для общих хранилищ)
     * @return EntryId созданной записи
     */
    suspend fun createEntry(
        storageId: StorageId,
        type: EntryType,
        data: EntryData,
        accessLevel: AccessLevel = AccessLevel.VIEW,
        targetGroupIds: Set<GroupId> = emptySet(),
        targetParticipantIds: Set<ParticipantId> = emptySet()
    ): Result<EntryId>
    
    /**
     * Обновить запись
     * Может редактировать только владелец записи
     */
    suspend fun updateEntry(
        storageId: StorageId,
        entryId: EntryId,
        data: EntryData
    ): Result<Unit>
    
    /**
     * Удалить запись
     */
    suspend fun deleteEntry(storageId: StorageId, entryId: EntryId): Result<Unit>
    
    /**
     * Расшифровать данные записи
     * Возвращает расшифрованные поля или null если нет доступа
     */
    suspend fun decryptEntryData(entry: Entry): EntryData?
    
    /**
     * Изменить доступ к записи (ACL)
     * Только владелец записи может менять ACL
     */
    suspend fun updateEntryAccess(
        storageId: StorageId,
        entryId: EntryId,
        newAccessList: Map<String, AccessLevel>  // participantId|groupId -> accessLevel
    ): Result<Unit>
}

/**
 * Менеджер участников и групп
 * Доступно только админам хранилища
 */
interface ParticipantManager {
    
    /**
     * Получить список участников хранилища
     */
    suspend fun getParticipants(storageId: StorageId): List<Participant>
    
    /**
     * Создать нового участника
     * Генерирует токен, возвращает его для передачи участнику
     */
    suspend fun createParticipant(
        storageId: StorageId,
        name: String,
        role: ParticipantRole = ParticipantRole.MEMBER
    ): Result<ParticipantToken>
    
    /**
     * Отозвать участника
     * Запускает ротацию ключей для записей, где участник имел доступ
     */
    suspend fun revokeParticipant(
        storageId: StorageId,
        participantId: ParticipantId
    ): Result<Unit>
    
    /**
     * Изменить роль участника
     */
    suspend fun updateParticipantRole(
        storageId: StorageId,
        participantId: ParticipantId,
        newRole: ParticipantRole
    ): Result<Unit>
    
    /**
     * Получить список групп
     */
    suspend fun getGroups(storageId: StorageId): List<Group>
    
    /**
     * Создать группу
     */
    suspend fun createGroup(
        storageId: StorageId,
        name: String,
        memberIds: Set<ParticipantId>
    ): Result<GroupId>
    
    /**
     * Обновить состав группы
     */
    suspend fun updateGroupMembers(
        storageId: StorageId,
        groupId: GroupId,
        memberIds: Set<ParticipantId>
    ): Result<Unit>
    
    /**
     * Удалить группу
     */
    suspend fun deleteGroup(storageId: StorageId, groupId: GroupId): Result<Unit>
}

/**
 * Токен участника для передачи
 */
data class ParticipantToken(
    val token: String,      // Случайный токен (256 бит, hex)
    val qrData: String,     // Данные для QR-кода
    val recoveryCode: String // Recovery-код (показывается один раз)
)

/**
 * Сервис аутентификации
 * Управление сессиями, биометрия, разблокировка хранилищ
 */
interface AuthService {
    
    /**
     * Проверить доступность биометрии
     */
    fun isBiometricAvailable(): Boolean
    
    /**
     * Запросить биометрическую аутентификацию
     */
    suspend fun authenticateBiometric(): Result<Unit>
    
    /**
     * Разблокировать хранилище токеном
     */
    suspend fun unlockStorage(storageId: StorageId, token: CharArray): Result<Unit>
    
    /**
     * Заблокировать хранилище (очистить ключи из памяти)
     */
    fun lockStorage(storageId: StorageId)
    
    /**
     * Проверить, разблокировано ли хранилище
     */
    fun isStorageUnlocked(storageId: StorageId): Boolean
}
