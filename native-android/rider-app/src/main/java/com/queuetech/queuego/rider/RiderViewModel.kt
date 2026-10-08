package com.queuetech.queuego.rider

import android.net.Uri
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
    private val photoStore: RiderEvidencePhotoStore,
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
                val coordinate = if (enabled) currentCoordinate() else null
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

    fun arrivedAtShop() = markArrival("shop")

    fun arrivedAtCustomer() = markArrival("customer")

    fun openPickupProof() {
        val job = _state.value.activeJob ?: return
        if (job.status != "ready") {
            setError("ร้านยังไม่กดสินค้าพร้อมส่ง")
            return
        }
        viewModelScope.launch { openProof(RiderProofMode.PICKUP, job) }
    }

    fun openMarketPickupProof(pickup: RiderMarketPickup) {
        val job = _state.value.activeJob ?: return
        if (job.marketOrderId.isNullOrBlank()) {
            setError("งานนี้ไม่ใช่ออเดอร์ตลาดหลายร้าน")
            return
        }
        if (pickup.done) return
        _state.value = _state.value.copy(
            proof = RiderProofState(
                mode = RiderProofMode.PICKUP,
                orderId = job.orderId,
                orderNumber = job.orderNumber,
                loading = false,
                marketPickupId = pickup.pickupId,
                marketPickupLabel = "จุดรับ " + pickup.sequence + " · " + pickup.shopName,
                marketPickupAmount = pickup.shopAmount,
            ),
            error = null,
            message = null,
        )
    }

    fun resumeMarketDelivery() {
        val job = _state.value.activeJob ?: return
        val marketId = job.marketOrderId ?: return
        if (_state.value.actionBusy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(actionBusy = true, error = null, message = null)
            try {
                repository.startMarketDelivery(marketId)
                refreshSnapshot(showLoading = false)
                _state.value.activeJob?.let(::requestCustomerNavigation)
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    actionBusy = false,
                    error = error.message ?: "เริ่มนำส่งลูกค้าไม่สำเร็จ",
                )
            }
        }
    }

    fun openDeliveryProof() {
        val job = _state.value.activeJob ?: return
        if (job.status != "in_progress") {
            setError("ออเดอร์ยังไม่อยู่ในขั้นตอนส่งลูกค้า")
            return
        }
        viewModelScope.launch { openProof(RiderProofMode.DELIVERY, job) }
    }

    fun proofPhotoCaptured(uri: String) {
        val proof = _state.value.proof ?: return
        _state.value = _state.value.copy(
            proof = proof.copy(photoUri = uri, error = null),
            error = null,
        )
    }

    fun closeProof() {
        val proof = _state.value.proof ?: return
        if (proof.submitting) return
        _state.value = _state.value.copy(proof = null)
    }

    fun submitProof() {
        val proof = _state.value.proof ?: return
        val job = _state.value.activeJob ?: return
        val photoUri = proof.photoUri
        if (photoUri.isNullOrBlank()) {
            _state.value = _state.value.copy(
                proof = proof.copy(error = "กรุณาถ่ายรูปหลักฐานก่อน"),
            )
            return
        }
        if (proof.submitting) return

        viewModelScope.launch {
            _state.value = _state.value.copy(
                proof = proof.copy(submitting = true, error = null),
                error = null,
                message = null,
            )
            try {
                val coordinate = currentCoordinate()
                val photoPath = photoStore.upload(Uri.parse(photoUri))

                when (proof.mode) {
                    RiderProofMode.PICKUP -> {
                        val pickupId = proof.marketPickupId
                        if (!pickupId.isNullOrBlank()) {
                            val pickup = _state.value.marketPickups.firstOrNull {
                                it.pickupId == pickupId
                            } ?: throw IllegalStateException("ไม่พบจุดรับสินค้าตลาด")
                            val allPickedUp = repository.marketPickupWithPhoto(
                                pickup = pickup,
                                photoPath = photoPath,
                                coordinate = coordinate,
                            )
                            _state.value = _state.value.copy(
                                proof = null,
                                actionBusy = false,
                                message = if (allPickedUp) {
                                    "รับสินค้าครบทุกจุดแล้ว · กำลังไปส่งลูกค้า"
                                } else {
                                    "รับสินค้าจุดนี้แล้ว · ไปจุดรับถัดไป"
                                },
                            )
                            refreshSnapshot(showLoading = false)

                            if (allPickedUp) {
                                val marketId = job.marketOrderId
                                    ?: throw IllegalStateException("ไม่พบ Market Order")
                                repository.startMarketDelivery(marketId)
                                refreshSnapshot(showLoading = false)
                                _state.value.activeJob?.let(::requestCustomerNavigation)
                            } else {
                                _state.value.marketPickups
                                    .firstOrNull { !it.done }
                                    ?.let(::requestPickupNavigation)
                            }
                        } else {
                            repository.pickupWithPhoto(
                                orderId = job.orderId,
                                photoPath = photoPath,
                                coordinate = coordinate,
                            )
                            _state.value = _state.value.copy(
                                proof = null,
                                actionBusy = false,
                                message = "รับสินค้าแล้ว · กำลังไปส่งลูกค้า",
                            )
                            refreshSnapshot(showLoading = false)
                            _state.value.activeJob?.let(::requestCustomerNavigation)
                        }
                    }

                    RiderProofMode.DELIVERY -> {
                        repository.completeWithPhoto(
                            job = job,
                            photoPath = photoPath,
                            coordinate = coordinate,
                        )
                        _state.value = _state.value.copy(
                            proof = null,
                            actionBusy = false,
                            message = "จัดส่งสำเร็จ",
                        )
                        refreshSnapshot(showLoading = false)
                    }
                }
            } catch (error: Exception) {
                val current = _state.value.proof ?: proof
                _state.value = _state.value.copy(
                    proof = current.copy(
                        submitting = false,
                        error = error.message ?: "บันทึกหลักฐานไม่สำเร็จ",
                    ),
                )
            }
        }
    }

    fun navigationHandled() {
        _state.value = _state.value.copy(navigationRequest = null)
    }

    fun refreshNow() {
        viewModelScope.launch { refreshSnapshot(showLoading = false) }
    }

    fun setError(message: String) {
        _state.value = _state.value.copy(error = message, message = null)
    }

    private fun markArrival(target: String) {
        val job = _state.value.activeJob ?: return
        if (_state.value.actionBusy) return

        viewModelScope.launch {
            _state.value = _state.value.copy(actionBusy = true, error = null, message = null)
            try {
                val coordinate = currentCoordinate()
                repository.markArrival(job.orderId, target, coordinate)
                _state.value = _state.value.copy(actionBusy = false)
                refreshSnapshot(showLoading = false)

                val current = _state.value.activeJob ?: job
                if (target == "shop") {
                    if (current.status == "ready") {
                        openProof(RiderProofMode.PICKUP, current)
                    } else {
                        _state.value = _state.value.copy(
                            message = "ถึงร้านแล้ว · แจ้งร้านแล้ว · รอร้านกดพร้อมส่ง",
                        )
                    }
                } else {
                    openProof(RiderProofMode.DELIVERY, current)
                }
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    actionBusy = false,
                    error = error.message ?: "บันทึกการมาถึงไม่สำเร็จ",
                )
            }
        }
    }

    private suspend fun openProof(mode: RiderProofMode, job: RiderActiveJob) {
        val currentProof = RiderProofState(
            mode = mode,
            orderId = job.orderId,
            orderNumber = job.orderNumber,
            loading = true,
        )
        _state.value = _state.value.copy(proof = currentProof, error = null, message = null)
        try {
            val items = repository.loadOrderItems(job.orderId)
            val stillOpen = _state.value.proof
            if (stillOpen?.orderId == job.orderId && stillOpen.mode == mode) {
                _state.value = _state.value.copy(
                    proof = stillOpen.copy(items = items, loading = false, error = null),
                )
            }
        } catch (error: Exception) {
            val stillOpen = _state.value.proof
            if (stillOpen?.orderId == job.orderId && stillOpen.mode == mode) {
                _state.value = _state.value.copy(
                    proof = stillOpen.copy(
                        loading = false,
                        error = error.message ?: "โหลดรายการสินค้าไม่สำเร็จ",
                    ),
                )
            }
        }
    }

    private fun requestPickupNavigation(pickup: RiderMarketPickup) {
        val lat = pickup.latitude ?: return
        val lng = pickup.longitude ?: return
        _state.value = _state.value.copy(
            navigationRequest = RiderNavigationRequest(
                latitude = lat,
                longitude = lng,
                label = pickup.shopName,
                id = System.nanoTime(),
            ),
        )
    }

    private fun requestCustomerNavigation(job: RiderActiveJob) {
        if (job.isPickupPhase) return
        val lat = job.deliveryLatitude ?: return
        val lng = job.deliveryLongitude ?: return
        _state.value = _state.value.copy(
            navigationRequest = RiderNavigationRequest(
                latitude = lat,
                longitude = lng,
                label = job.deliveryAddress.ifBlank { job.customerName },
                id = System.nanoTime(),
            ),
        )
    }

    private suspend fun currentCoordinate(): RiderCoordinate =
        withTimeout(10_000L) { locationProvider.current() }

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
                val marketPickups = activeJob?.marketOrderId
                    ?.let { marketId ->
                        runCatching { repository.loadMarketPickups(marketId) }
                            .getOrElse { emptyList() }
                    }
                    ?: emptyList()
                val currentProof = _state.value.proof
                val proof = if (
                    activeJob != null &&
                    currentProof?.orderId == activeJob.orderId
                ) {
                    currentProof
                } else {
                    null
                }

                _state.value = _state.value.copy(
                    loading = false,
                    profile = profile,
                    activeJob = activeJob,
                    offer = offer,
                    proof = proof,
                    marketPickups = marketPickups,
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
            photoStore: RiderEvidencePhotoStore,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                RiderViewModel(repository, locationProvider, photoStore) as T
        }
    }
}
