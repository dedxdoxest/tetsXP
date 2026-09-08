package app.hp.sync

import app.hp.model.StorageId

/**
 * Интерфейс облачного синка
 * Абстракция над различными облачными провайдерами
 */
interface CloudSync {
    
    /**
     * Скачать файл из облака
     * 
     * @param cloudPath Путь к файлу
     * @return ByteArray с содержимым файла или null если файл не найден
     */
    suspend fun read(cloudPath: String): ByteArray?
    
    /**
     * Загрузить файл в облако
     * 
     * @param cloudPath Путь к файлу
     * @param data Содержимое файла
     * @return true если успешно
     */
    suspend fun upload(cloudPath: String, data: ByteArray): Boolean
    
    /**
     * Получить список версий файла (для разрешения конфликтов)
     * 
     * @param cloudPath Путь к файлу
     * @return Список ревизий с метаданными
     */
    suspend fun listRevisions(cloudPath: String): List<FileRevision>
    
    /**
     * Удалить файл из облака
     * 
     * @param cloudPath Путь к файлу
     * @return true если успешно
     */
    suspend fun delete(cloudPath: String): Boolean
}

/**
 * Ревизия файла в облаке
 */
data class FileRevision(
    val id: String,
    val modifiedTime: Long,
    val size: Long,
    val etag: String? = null  // ETag для проверки изменений
)

/**
 * Результат синхронизации
 */
sealed class SyncResult {
    object Success : SyncResult()
    data class Conflict(val localVersion: Long, val remoteVersion: Long) : SyncResult()
    data class Error(val message: String) : SyncResult()
}

/**
 * Менеджер синхронизации
 * Обрабатывает merge конфликты на уровне записей
 */
interface SyncManager {
    
    /**
     * Синхронизировать хранилище с облаком
     * 
     * @param storageId ID хранилища
     * @return Результат синхронизации
     */
    suspend fun sync(storageId: StorageId): SyncResult
    
    /**
     * Разрешить конфликт синхронизации
     * Вызывается когда обнаружены расхождения между локальной и удалённой версией
     * 
     * @param storageId ID хранилища
     * @param localData Локальные данные
     * @param remoteData Удалённые данные
     * @return Объединённые данные или выбор одной из версий
     */
    suspend fun resolveConflict(
        storageId: StorageId,
        localData: ByteArray,
        remoteData: ByteArray
    ): ByteArray
}
