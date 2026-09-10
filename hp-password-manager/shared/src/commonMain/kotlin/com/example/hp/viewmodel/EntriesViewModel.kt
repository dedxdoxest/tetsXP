package com.example.hp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hp.data.Entry
import com.example.hp.data.repository.StorageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EntriesViewModel(
    private val storageRepository: StorageRepository,
    private val currentToken: StateFlow<String>
) : ViewModel() {

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun loadEntries(entries: List<Entry>) {
        _entries.value = entries
    }

    fun addEntry(entry: Entry) {
        viewModelScope.launch {
            _isLoading.value = true
            val token = currentToken.value
            if (token.isEmpty()) {
                _isLoading.value = false
                return@launch
            }

            val currentList = _entries.value.toMutableList()
            currentList.add(entry)
            _entries.value = currentList

            val result = storageRepository.saveEntries(currentList, token)
            result.onFailure {
                // Откат при ошибке
                _entries.value = currentList.filter { it.id != entry.id }
            }
            _isLoading.value = false
        }
    }

    fun updateEntry(entry: Entry) {
        viewModelScope.launch {
            _isLoading.value = true
            val token = currentToken.value
            if (token.isEmpty()) {
                _isLoading.value = false
                return@launch
            }

            val currentList = _entries.value.map { if (it.id == entry.id) entry else it }
            _entries.value = currentList

            val result = storageRepository.saveEntries(currentList, token)
            result.onFailure {
                // Откат
                loadEntries(_entries.value) 
            }
            _isLoading.value = false
        }
    }

    fun deleteEntry(entryId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val token = currentToken.value
            if (token.isEmpty()) {
                _isLoading.value = false
                return@launch
            }

            val currentList = _entries.value.filter { it.id != entryId }
            _entries.value = currentList

            val result = storageRepository.saveEntries(currentList, token)
            result.onFailure {
                loadEntries(_entries.value)
            }
            _isLoading.value = false
        }
    }

    fun getFilteredEntries(): List<Entry> {
        val query = _searchQuery.value.lowercase()
        return if (query.isEmpty()) {
            _entries.value
        } else {
            _entries.value.filter {
                it.name.lowercase().contains(query) ||
                it.login.lowercase().contains(query) ||
                (it.url?.lowercase()?.contains(query) == true)
            }
        }
    }
}
