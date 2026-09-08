package app.hp.sync

import kotlinx.serialization.json.*
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Яндекс.Диск реализация CloudSync
 * 
 * Использует REST API Яндекса:
 * https://yandex.ru/dev/disk/rest/
 * 
 * OAuth токен пользователя должен быть получен заранее
 */
class YandexDiskSync(
    private val oauthToken: String
) : CloudSync {
    
    private val json = Json { ignoreUnknownKeys = true }
    private val baseUrl = "https://cloud-api.yandex.net/v1/disk"
    
    override suspend fun read(cloudPath: String): ByteArray? {
        // 1. Получить ссылку на скачивание
        val downloadUrl = getDownloadLink(cloudPath) ?: return null
        
        // 2. Скачать файл по ссылке
        return downloadFile(downloadUrl)
    }
    
    override suspend fun upload(cloudPath: String, data: ByteArray): Boolean {
        // 1. Получить ссылку на загрузку
        val uploadUrl = getUploadLink(cloudPath) ?: return false
        
        // 2. Загрузить файл
        return uploadFile(uploadUrl, data)
    }
    
    override suspend fun listRevisions(cloudPath: String): List<FileRevision> {
        // Яндекс.Диск хранит историю версий
        // GET /v1/disk/resources/history?path=<path>
        val url = "$baseUrl/resources/history?path=${urlEncode(cloudPath)}"
        
        return makeRequest(url) { response ->
            val jsonObject = json.parseToJsonElement(response).jsonObject
            val items = jsonObject["items"]?.jsonArray ?: return@makeRequest emptyList()
            
            items.map { item ->
                val obj = item.jsonObject
                FileRevision(
                    id = obj["id"]?.jsonPrimitive?.content ?: "",
                    modifiedTime = obj["modified"]?.jsonPrimitive?.let { 
                        kotlin.runCatching { 
                            java.time.Instant.parse(it.content).toEpochMilli() 
                        }.getOrDefault(0L)
                    } ?: 0L,
                    size = obj["size"]?.jsonPrimitive?.longOrNull() ?: 0L
                )
            }
        } ?: emptyList()
    }
    
    override suspend fun delete(cloudPath: String): Boolean {
        val url = "$baseUrl/resources?path=${urlEncode(cloudPath)}&permanently=false"
        
        return makeSimpleRequest(url, "DELETE")
    }
    
    // --- Внутренние методы ---
    
    private suspend fun getDownloadLink(cloudPath: String): String? {
        val url = "$baseUrl/resources/download?path=${urlEncode(cloudPath)}"
        
        return makeRequest(url) { response ->
            json.parseToJsonElement(response).jsonObject["href"]?.jsonPrimitive?.content
        }
    }
    
    private suspend fun getUploadLink(cloudPath: String): String? {
        val url = "$baseUrl/resources/upload?path=${urlEncode(cloudPath)}&overwrite=true"
        
        return makeRequest(url) { response ->
            json.parseToJsonElement(response).jsonObject["href"]?.jsonPrimitive?.content
        }
    }
    
    private suspend fun downloadFile(url: String): ByteArray? {
        return withTimeout(30000) {
            val connection = HttpsURLConnection(URL(url)).apply {
                requestMethod = "GET"
                connectTimeout = 30000
                readTimeout = 30000
            }
            
            try {
                connection.connect()
                if (connection.responseCode == 200) {
                    connection.inputStream.readBytes()
                } else {
                    null
                }
            } finally {
                connection.disconnect()
            }
        }
    }
    
    private suspend fun uploadFile(url: String, data: ByteArray): Boolean {
        return withTimeout(30000) {
            val connection = HttpsURLConnection(URL(url)).apply {
                requestMethod = "PUT"
                connectTimeout = 30000
                readTimeout = 30000
                doOutput = true
                setRequestProperty("Content-Type", "application/octet-stream")
                setRequestProperty("Content-Length", data.size.toString())
            }
            
            try {
                connection.outputStream.write(data)
                connection.connect()
                connection.responseCode in 200..299
            } finally {
                connection.disconnect()
            }
        }
    }
    
    private suspend inline fun <T> makeRequest(
        url: String,
        crossinline parse: (String) -> T
    ): T? {
        return withTimeout(10000) {
            val connection = HttpsURLConnection(URL(url)).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Authorization", "OAuth $oauthToken")
            }
            
            try {
                connection.connect()
                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    parse(response)
                } else {
                    null
                }
            } finally {
                connection.disconnect()
            }
        }
    }
    
    private suspend fun makeSimpleRequest(url: String, method: String): Boolean {
        return withTimeout(10000) {
            val connection = HttpsURLConnection(URL(url)).apply {
                requestMethod = method
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Authorization", "OAuth $oauthToken")
            }
            
            try {
                connection.connect()
                connection.responseCode in 200..299
            } finally {
                connection.disconnect()
            }
        }
    }
    
    private fun urlEncode(s: String): String = 
        java.net.URLEncoder.encode(s, "UTF-8")
    
    private inline fun <T> withTimeout(timeoutMs: Long, block: () -> T): T {
        // Простая реализация таймаута для Kotlin
        // В реальности использовать kotlinx.coroutines.withTimeout
        return block()
    }
}

/**
 * Фабрика для создания CloudSync провайдеров
 */
object CloudSyncFactory {
    
    enum class Provider {
        YANDEX_DISK,
        GOOGLE_DRIVE,
        ONEDRIVE
    }
    
    fun create(provider: Provider, credentials: String): CloudSync {
        return when (provider) {
            Provider.YANDEX_DISK -> YandexDiskSync(credentials)
            Provider.GOOGLE_DRIVE -> TODO("Google Drive будет добавлен позже")
            Provider.ONEDRIVE -> TODO("OneDrive будет добавлен позже")
        }
    }
}
