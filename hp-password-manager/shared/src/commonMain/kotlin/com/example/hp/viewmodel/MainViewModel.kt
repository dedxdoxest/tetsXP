package com.example.hp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hp.data.Entry
import com.example.hp.data.Storage
import com.example.hp.data.repository.StorageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AuthState {
    object Locked : AuthState()
    object Unlocked : AuthState()
    data class Error(val message: String) : AuthState()
}

class MainViewModel(
    private val storageRepository: StorageRepository,
    private val currentToken: MutableStateFlow<String>
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Locked)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _storage = MutableStateFlow<Storage?>(null)
    val storage: StateFlow<Storage?> = _storage.asStateFlow()

    init {
        checkStorageExists()
    }

    fun checkStorageExists() {
        viewModelScope.launch {
            if (!storageRepository.hasStorage()) {
                _authState.value = AuthState.Locked
            }
        }
    }

    fun createStorage(token: String) {
        viewModelScope.launch {
            val result = storageRepository.createStorage(token)
            result.onSuccess { storage ->
                _storage.value = storage
                currentToken.value = token
                _authState.value = AuthState.Unlocked
            }.onFailure { error ->
                _authState.value = AuthState.Error(error.message ?: "Ошибка создания")
            }
        }
    }

    fun unlockStorage(token: String) {
        viewModelScope.launch {
            val result = storageRepository.unlockStorage(token)
            result.onSuccess { storage ->
                _storage.value = storage
                currentToken.value = token
                _authState.value = AuthState.Unlocked
            }.onFailure { error ->
                _authState.value = AuthState.Error(error.message ?: "Неверный токен")
            }
        }
    }

    fun lockStorage() {
        currentToken.value = ""
        _storage.value = null
        _authState.value = AuthState.Locked
    }
}
