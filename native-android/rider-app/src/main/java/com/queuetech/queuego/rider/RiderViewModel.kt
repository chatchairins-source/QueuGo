package com.queuetech.queuego.rider

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.queuetech.queuego.core.model.QueueGoUser
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

class RiderViewModel(
    private val repository: RiderRepository,
    private val locationProvider: RiderLocationProvider,
) : ViewModel() {
    private val _state = MutableStateFlow(RiderDashboardState())
    val state: StateFlow<RiderDashboardState> = _state.asStateFlow()

    private val refreshMutex = Mutex()
    private var boundUser: QueueGoUser? = null
    private var pollJob: Job? = null

    fun bind(user: QueueGoUser) {
        if (boundUser?.id == user.id) return
        boundUser = user
        pollJob?.cancel()
        _state.value = RiderDashboardState(loading = true)
        viewModelScope.launch {
            refreshSnapshot(showLoading = true)
        }
    }

    fun unbind() {
        boundUser = null
        pollJob?.cancel()
        pollJob = null
        _state.value = RiderDashboardState()
    }

    fun setOnline(enabled: Boolean) {
        val user = boundUser ?: return
        if (_state.value.actionBusy) return
        if (!enabled && _state.value.activeJob != null) {
            setError("มีงานที่กำลังจัดส่งอยู่ กรุณาจบงานก่อนปิดรับงาน")
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(actionBusy = true, error = null, message = null)
            try {
                val coordinate = if (enabled) {
                    withTimeout(10_000L) { locationProvider.current() }
                } else {
                    null
                }
                val profile = repository.setOnline(user.id, enabled, coordinate)
                _state.value = _state.value.copy(
                    profile = profile,
                    offer = if (enabled) _state.value.offer else null,
                    actionBusy = false,
                    message = if (enabled) "เปิดรับงานแล้ว" else "ปิดรับงานแล้ว",
                )
                if (enabled) {
                    refreshSnapshot(showLoading = false)
                    startPolling()
                } else {
                    stopPolling()
                }
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    actionBusy = false,
                    error = error.message ?: "เปลี่ยนสถานะรับงานไม่สำเร็จ",
                )
            }
        }
    }

    fun acceptOffer() {
        val user = boundUser ?: return
        val offer = _state.value.offer ?: return
        if (_state.value.actionBusy) return
        if (offer.expiresAtMillis <= System.currentTimeMillis()) {
            offerExpired()
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(actionBusy = true, error = null, message = null)
            try {
                repository.acceptOffer(user.id, offer)
                _state.value = _state.value.copy(
                    offer = null,
                    actionBusy = false,
                    message = "รับงานสำเร็จ",
                )
                refreshSnapshot(showLoading = false)
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    actionBusy = false,
                    error = error.message ?: "รับงานไม่สำเร็จ",
                )
                refreshSnapshot(showLoading = false)
            }
        }
    }

    fun declineOffer() {
        val offer = _state.value.offer ?: return
        if (_state.value.actionBusy) return

        viewModelScope.launch {
            _state.value = _state.value.copy(actionBusy = true, error = null, message = null)
            try {
                repository.declineOffer(offer.orderId)
                _state.value = _state.value.copy(
                    offer = null,
                    actionBusy = false,
                    message = "ส่งงานให้ไรเดอร์คนถัดไปแล้ว",
                )
                refreshSnapshot(showLoading = false)
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    actionBusy = false,
                    error = error.message ?: "ปฏิเสธงานไม่สำเร็จ",
                )
            }
        }
    }

    fun offerExpired() {
        if (_state.value.offer == null) return
        _state.value = _state.value.copy(offer = null)
        viewModelScope.launch { refreshSnapshot(showLoading = false) }
    }

    fun refreshNow() {
        viewModelScope.launch { refreshSnapshot(showLoading = false) }
    }

    fun setError(message: String) {
        _state.value = _state.value.copy(error = message, message = null)
    }

    private suspend fun refreshSnapshot(showLoading: Boolean) {
        val user = boundUser ?: return
        refreshMutex.withLock {
            if (boundUser?.id != user.id) return
            if (showLoading) _state.value = _state.value.copy(loading = true, error = null)
            try {
                val profile = repository.loadProfile(user.id)
                val activeJob = if (profile.status == "active") {
                    repository.loadActiveJob(profile.id)
                } else {
                    null
                }
                val offer = if (
                    profile.status == "active" &&
                    profile.online &&
                    activeJob == null
                ) {
                    repository.loadOffer()
                } else {
                    null
                }

                _state.value = _state.value.copy(
                    loading = false,
                    profile = profile,
                    activeJob = activeJob,
                    offer = offer,
                    actionBusy = false,
                    error = null,
                )
                if (profile.online) startPolling() else stopPolling()
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    actionBusy = false,
                    error = error.message ?: "โหลดสถานะไรเดอร์ไม่สำเร็จ",
                )
            }
        }
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive && boundUser != null) {
                delay(POLL_MS)
                refreshSnapshot(showLoading = false)
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    companion object {
        private const val POLL_MS = 3_000L

        fun factory(
            repository: RiderRepository,
            locationProvider: RiderLocationProvider,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                RiderViewModel(repository, locationProvider) as T
        }
    }
}
