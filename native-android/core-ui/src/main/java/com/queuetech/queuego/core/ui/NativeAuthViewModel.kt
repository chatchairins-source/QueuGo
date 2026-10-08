package com.queuetech.queuego.core.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.queuetech.queuego.core.auth.AuthRepository
import com.queuetech.queuego.core.model.AppRole
import com.queuetech.queuego.core.model.QueueGoUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface NativeAuthState {
    data object Loading : NativeAuthState
    data class SignedOut(val error: String? = null) : NativeAuthState
    data class SignedIn(val user: QueueGoUser) : NativeAuthState
}

class NativeAuthViewModel(
    private val expectedRole: AppRole,
    private val repository: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<NativeAuthState>(NativeAuthState.Loading)
    val state: StateFlow<NativeAuthState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = repository.restore(expectedRole)?.let(NativeAuthState::SignedIn)
                ?: NativeAuthState.SignedOut()
        }
    }

    fun login(identifier: String, password: String) {
        if (_state.value == NativeAuthState.Loading) return
        _state.value = NativeAuthState.Loading
        viewModelScope.launch {
            _state.value = runCatching { repository.login(identifier, password, expectedRole) }.fold(
                onSuccess = NativeAuthState::SignedIn,
                onFailure = { NativeAuthState.SignedOut(it.message ?: "เข้าสู่ระบบไม่สำเร็จ") },
            )
        }
    }

    fun logout() {
        _state.value = NativeAuthState.Loading
        viewModelScope.launch {
            repository.logout()
            _state.value = NativeAuthState.SignedOut()
        }
    }

    companion object {
        fun factory(expectedRole: AppRole, repository: AuthRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    NativeAuthViewModel(expectedRole, repository) as T
            }
    }
}
