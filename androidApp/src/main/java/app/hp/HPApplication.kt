package app.hp

import android.app.Application
import app.hp.crypto.AndroidCryptoProvider
import app.hp.crypto.CryptoProvider

/**
 * Application класс для HP Password Manager
 */
class HPApplication : Application() {
    
    // Глобальный крипто-провайдер
    lateinit var cryptoProvider: CryptoProvider
        private set
    
    override fun onCreate() {
        super.onCreate()
        
        // Инициализация крипто-провайдера
        cryptoProvider = AndroidCryptoProvider()
        
        // В будущем: инициализация других синглтонов (репозитории, менеджеры)
    }
}
