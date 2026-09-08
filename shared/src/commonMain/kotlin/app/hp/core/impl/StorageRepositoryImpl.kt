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
    
    /**
     * Сериализация хранилища в бинарный формат файла:
     * [Заголовок (публичный)] + [Манифест (шифрованный)] + [Блобы записей]
     */
    private fun serializeStorage(storage: Storage, storageKey: ByteArray): ByteArray {
        // Заголовок (публичный)
        val header = Header(
            storageId = storage.id,
            kdfParams = KdfParams(
                saltHex = storage.kdfParams.salt.toHex()
            ),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        
        // Манифест
        val manifest = Manifest(
            storageKeyWrapped = storage.wrappedStorageKey.map { (participantId, wrappedKey) ->
                WrappedKey(
                    participantId = participantId,
                    wrappedKeyHex = wrappedKey.toHex(),
                    nonceHex = "" // TODO: сохранить nonce
                )
            },
            entries = storage.entries.map { (entryId, entry) ->
                EntryMeta(
                    id = entryId,
                    ownerParticipantId = entry.ownerId,
                    keyWrappers = entry.acl.map { (targetId, _) ->
                        KeyWrapper(
                            targetId = targetId,
                            targetType = "participant", // TODO: определить тип
                            wrappedKeyHex = entry.key.toHex(), // TODO: обернуть ключ
                            nonceHex = ""
                        )
                    },
                    isDeleted = false,
                    updatedAt = entry.modifiedAt
                )
            },
            groups = storage.groups.map { (groupId, group) ->
                GroupMeta(
                    id = groupId,
                    name = group.name,
                    memberIds = group.memberIds
                )
            }
        )
        
        // Шифрование манифеста ключом хранилища
        val manifestJson = StorageSerializer.manifestToJson(manifest)
        val encryptedManifest = cryptoProvider.encrypt(manifestJson.encodeToByteArray(), storageKey)
        
        // Блобы записей
        val entryBlobs = storage.entries.map { (entryId, entry) ->
            val content = EntryContent(
                name = entry.name,
                login = entry.login,
                password = entry.password,
                url = entry.url,
                notes = entry.notes,
                totpSecret = entry.totpSecret
            )
            val contentJson = StorageSerializer.entryContentToJson(content)
            val encrypted = cryptoProvider.encrypt(contentJson.encodeToByteArray(), entry.key)
            EncryptedEntry(
                id = entryId,
                ciphertextHex = encrypted.ciphertext.toHex(),
                nonceHex = encrypted.nonce.toHex(),
                authTagHex = encrypted.tag.toHex()
            )
        }
        
        // Сборка файла в бинарный формат
        return buildFile(header, encryptedManifest, entryBlobs)
    }
    
    private fun buildFile(header: Header, encryptedManifest: EncryptedData, entryBlobs: List<EncryptedEntry>): ByteArray {
        // Простая бинарная структура:
        // [4 байта: длина header] [header JSON]
        // [4 байта: длина manifest blob] [manifest blob]
        // [4 байта: кол-во entry blobs]
        //   для каждого: [4 байта: длина] [blob]
        
        val headerJson = StorageSerializer.headerToJson(header).encodeToByteArray()
        val manifestBlob = encryptedManifest.ciphertext
        
        val outputStream = java.io.ByteArrayOutputStream()
        val buffer = java.nio.ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        
        // Запись header
        buffer.putInt(headerJson.size)
        outputStream.write(buffer.array())
        outputStream.write(headerJson)
        
        // Запись manifest
        buffer.putInt(0).also { buffer.clear() }
        buffer.putInt(manifestBlob.size)
        outputStream.write(buffer.array())
        outputStream.write(manifestBlob)
        
        // Запись entry blobs
        buffer.putInt(0).also { buffer.clear() }
        buffer.putInt(entryBlobs.size)
        outputStream.write(buffer.array())
        
        for (blob in entryBlobs) {
            val blobBytes = StorageSerializer.json.encodeToString(blob).encodeToByteArray()
            buffer.putInt(0).also { buffer.clear() }
            buffer.putInt(blobBytes.size)
            outputStream.write(buffer.array())
            outputStream.write(blobBytes)
        }
        
        return outputStream.toByteArray()
    }
    
    private fun deriveKekFromParticipant(storage: Storage, participantId: String): ByteArray {
        // TODO: Получить KEK участника из wrappedKek или из токена
        TODO("KEK derivation")
    }
}
