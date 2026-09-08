package app.hp.core.impl

import app.hp.core.*
import app.hp.crypto.CryptoProvider
import app.hp.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import app.hp.sync.CloudSync

/**
 * Реализация StorageRepository
 */
class StorageRepositoryImpl(
    private val cryptoProvider: CryptoProvider,
    private val cloudSync: CloudSync
) : StorageRepository {
    
    private val _storages = MutableStateFlow<List<Storage>>(emptyList())
    override val storages: Flow<List<Storage>> = _storages.asStateFlow()
    
    private val mutex = Mutex()
    private val storageMap = mutableMapOf<StorageId, Storage>()
    
    // Активные сессии (разблокированные хранилища)
    private val unlockedStorages = mutableMapOf<StorageId, UnlockedStorage>()
    
    data class UnlockedStorage(
        val storage: Storage,
        val storageKey: ByteArray,  // Расшифрованный ключ хранилища
        val participantKek: ByteArray  // KEK текущего участника
    )
    
    override suspend fun getStorage(id: StorageId): Storage? {
        return mutex.withLock {
            storageMap[id]
        }
    }
    
    override suspend fun createPersonalStorage(name: String, token: CharArray): CreateStorageResult {
        return mutex.withLock {
            // Генерация ID хранилища
            val storageId = cryptoProvider.randomBytes(16).toHex()
            
            // Генерация соли для KDF
            val salt = cryptoProvider.randomBytes(32)
            
            // Параметры KDF
            val kdfParams = KdfParams(
                memoryKb = 65536,
                iterations = 3,
                parallelism = 4,
                salt = salt
            )
            
            // Вывод мастер-ключа владельца из токена
            val ownerKek = cryptoProvider.argon2id(token, salt, kdfParams.memoryKb, kdfParams.iterations, kdfParams.parallelism, 32)
            
            // Генерация ключа хранилища
            val storageKey = cryptoProvider.randomBytes(32)
            
            // Обёртка ключа хранилища для владельца
            val wrappedStorageKey = cryptoProvider.wrapKey(storageKey, ownerKek)
            
            // Создание участника-владельца
            val ownerId = cryptoProvider.randomBytes(16).toHex()
            val (ownerPubKey, ownerPrivKey) = cryptoProvider.x25519KeyPair()
            
            val owner = Participant(
                id = ownerId,
                name = "Владелец",
                role = ParticipantRole.OWNER,
                publicKey = ownerPubKey,
                wrappedKek = null  // Владелец знает свой токен
            )
            
            // Создание хранилища
            val storage = Storage(
                id = storageId,
                name = name,
                version = 1,
                kdfParams = kdfParams,
                encryptionParams = EncryptionParams(),
                participants = mapOf(ownerId to owner),
                groups = emptyMap(),
                entries = emptyMap(),
                wrappedStorageKey = mapOf(ownerId to wrappedStorageKey),
                lastSyncTime = null,
                cloudPath = null
            )
            
            // Сохранение в память
            storageMap[storageId] = storage
            unlockedStorages[storageId] = UnlockedStorage(storage, storageKey, ownerKek)
            _storages.value = storageMap.values.toList()
            
            // Генерация recovery-кода
            val recoveryCode = generateRecoveryCode(storageId, ownerId, token)
            
            CreateStorageResult(storageId, recoveryCode)
        }
    }
    
    override suspend fun addExistingStorage(token: CharArray, cloudPath: String): Result<StorageId> {
        return mutex.withLock {
            // TODO: Загрузить хранилище из облака по cloudPath
            // TODO: Расшифровать манифест используя токен
            // TODO: Добавить в список хранилищ
            Result.failure(NotImplementedError("Добавление существующего хранилища ещё не реализовано"))
        }
    }
    
    override suspend fun removeStorage(id: StorageId): Result<Unit> {
        return mutex.withLock {
            if (storageMap.remove(id) != null) {
                unlockedStorages.remove(id)
                _storages.value = storageMap.values.toList()
                Result.success(Unit)
            } else {
                Result.failure(Exception("Хранилище не найдено"))
            }
        }
    }
    
    override suspend fun syncStorage(id: StorageId): Result<Unit> {
        val unlocked = unlockedStorages[id] ?: return Result.failure(Exception("Хранилище заблокировано"))
        
        return mutex.withLock {
            // Сериализация хранилища в файл
            val fileData = serializeStorage(unlocked.storage, unlocked.storageKey)
            
            // Загрузка в облако
            val cloudPath = unlocked.storage.cloudPath ?: return@withLock Result.failure(Exception("Cloud path not set"))
            
            if (cloudSync.upload(cloudPath, fileData)) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to upload to cloud"))
            }
        }
    }
    
    private fun generateRecoveryCode(storageId: StorageId, ownerId: String, token: CharArray): String {
        // Формат: XXXX-XXXX-XXXX-XXXX (16 символов)
        // В реальности нужно хранить больше информации для восстановления
        return token.concatToString().take(16).chunked(4).joinToString("-")
    }
    
    private fun serializeStorage(storage: Storage, storageKey: ByteArray): ByteArray {
        // TODO: Сериализация в бинарный формат
        // [Заголовок] + [Манифест] + [Блобы записей]
        return byteArrayOf()
    }
}
