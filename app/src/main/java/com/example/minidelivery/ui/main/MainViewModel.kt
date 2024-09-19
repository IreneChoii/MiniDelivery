package com.example.minidelivery.ui.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import com.example.minidelivery.R
import com.example.minidelivery.data.Order
import com.example.minidelivery.data.OrderRepository
import com.example.minidelivery.data.OrderStatus
import com.google.android.material.tabs.TabLayout

enum class SortOrder {
    LATEST, OLDEST
}

class MainViewModel : ViewModel() {
    private val repository = OrderRepository.getInstance() // 리포지토리 인스턴스 가져오기

    private val _currentOrder = MutableLiveData<Order?>() // 현재 주문 LiveData
    val currentOrder: LiveData<Order?> = _currentOrder

    val orderCardVisibility = currentOrder.map { it != null } // 주문 카드 표시 여부

    private val _acceptButtonState = MutableLiveData<AcceptButtonState>() // 접수 버튼 상태 LiveData
    val acceptButtonState: LiveData<AcceptButtonState> = _acceptButtonState

    private val _navigateToDelivery = MutableLiveData<Boolean>() // 배달 화면 이동 LiveData
    val navigateToDelivery: LiveData<Boolean> = _navigateToDelivery

    private var currentTab = 0 // 현재 선택된 탭
    private var sortOrder = SortOrder.LATEST // 현재 정렬 순서


    init {
        loadInitialData() // 초기 데이터 로드
    }

    // 초기 데이터 로드 함수
    private fun loadInitialData() {
        repository.loadInitialData() // 리포지토리에서 초기 데이터 로드
        loadOrders() // 주문 목록 로드
    }

    // 주문 목록 로드 함수
    fun loadOrders() {
        val orders = when (currentTab) {
            0 -> repository.getProcessingOrders() // 처리 중인 주문 가져오기
            1 -> repository.getCookingOrders() // 조리 중인 주문 가져오기
            else -> emptyList()
        }

        val sortedOrders = when (sortOrder) {
            SortOrder.LATEST -> orders.sortedByDescending { it.time } // 최신순 정렬
            SortOrder.OLDEST -> orders.sortedBy { it.time } // 과거순 정렬
        }

        _currentOrder.value = sortedOrders.firstOrNull() // 첫 번째 주문을 현재 주문으로 설정
        updateAcceptButtonState() // 버튼 상태 업데이트
    }

    // 접수 버튼 클릭 처리 함수
    fun onAcceptButtonClick() {
        _currentOrder.value?.let { order ->
            when (order.status) {
                OrderStatus.READY -> updateOrderStatus(OrderStatus.COOKING)
                OrderStatus.COOKING -> updateOrderStatus(OrderStatus.COOKED)
                OrderStatus.COOKED -> updateOrderStatus(OrderStatus.DELIVERING)
                OrderStatus.DELIVERING -> updateOrderStatus(OrderStatus.COMPLETED)
                OrderStatus.COMPLETED -> {} // 이미 완료된 주문은 추가 처리 없음
            }
        }
    }

    // 주문 상태 업데이트 함수
    fun updateOrderStatus(newStatus: OrderStatus) {
        _currentOrder.value?.let { order ->
            order.status = newStatus // 주문 상태 업데이트
            repository.updateOrder(order) // 리포지토리에 주문 업데이트
            updateAcceptButtonState() // 버튼 상태 업데이트
            loadOrders() // 주문 목록 다시 로드

            if (newStatus == OrderStatus.DELIVERING) {
                _navigateToDelivery.value = true // 배달 화면으로 이동 트리거
            }
        }
    }

    // 주문 상태 업데이트 함수 (문자열 버전)
    fun updateOrderStatus(newStatusString: String) {
        val newStatus = OrderStatus.valueOf(newStatusString)
        updateOrderStatus(newStatus)
    }

    // 접수 버튼 상태 업데이트 함수
    private fun updateAcceptButtonState() {
        _currentOrder.value?.let { order ->
            _acceptButtonState.value = when (order.status) {
                OrderStatus.READY -> AcceptButtonState("접수", R.color.processing_color)
                OrderStatus.COOKING -> AcceptButtonState("조리완료", R.color.cooking_color)
                OrderStatus.COOKED -> AcceptButtonState("배달시작", R.color.cooked_color)
                OrderStatus.DELIVERING -> AcceptButtonState("배달완료", R.color.delivering_color)
                OrderStatus.COMPLETED -> AcceptButtonState("완료", R.color.completed_color)
            }
        }
    }

    // 정렬 순서 설정 함수
    fun setSortOrder(order: SortOrder) {
        sortOrder = order // 정렬 순서 설정
        loadOrders() // 주문 목록 다시 로드
    }


    // 탭 선택 리스너
    val tabSelectedListener = object : TabLayout.OnTabSelectedListener {
        override fun onTabSelected(tab: TabLayout.Tab?) {
            currentTab = tab?.position ?: 0 // 현재 탭 업데이트
            loadOrders() // 주문 목록 다시 로드
        }

        override fun onTabUnselected(tab: TabLayout.Tab?) {}
        override fun onTabReselected(tab: TabLayout.Tab?) {}
    }

    // 배달 화면 이동 후 처리 함수
    fun onDeliveryNavigated() {
        _navigateToDelivery.value = false // 배달 화면 이동 후 플래그 리셋
    }

    data class AcceptButtonState(val text: String, val colorResId: Int)
}