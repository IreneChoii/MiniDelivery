package com.example.minidelivery.ui.main

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.minidelivery.R
import com.example.minidelivery.data.Order
import com.example.minidelivery.data.OrderItem
import com.example.minidelivery.data.OrderStatus
import com.example.minidelivery.mqtt.MqttManager
import com.example.minidelivery.order.OrderAdapter
import com.example.minidelivery.ui.delivery.DeliveryActivity
import com.example.minidelivery.ui.done.DoneActivity
import com.example.minidelivery.ui.detail.DetailActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.chip.ChipGroup
import com.google.android.material.tabs.TabLayout



class MainActivity : AppCompatActivity() {
    private lateinit var mqttManager: MqttManager // MqttManager 선언
    private lateinit var tabLayout: TabLayout // 탭 레이아웃 선언
    private lateinit var chipGroup: ChipGroup // 칩 그룹 선언
    private lateinit var bottomNavigation: BottomNavigationView // 하단 네비게이션 선언
    private lateinit var orderRecyclerView: RecyclerView // RecyclerView 선언
    private lateinit var orderAdapter: OrderAdapter // Adapter 선언

    private val viewModel: MainViewModel by lazy {
        MainViewModelFactory(mqttManager).create(MainViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mqttManager = MqttManager(this)
        mqttManager.connect()

        initViews()
        setupUI()

        // MainActivity에서 MQTT LiveData 관찰
        observeMqttOrders()

        viewModel.orders.observe(this) { orders ->
            orderAdapter.updateOrders(orders)
        }

        mqttManager = MqttManager(this)
        mqttManager.connect()
    }
    // 초기 데이터 로드 함수
    fun observeMqttOrders() {
        mqttManager.orderLiveData.observeForever { orderData ->
            val newOrder = orderData?.let {
                Order(
                    id = System.currentTimeMillis().toString(),
                    order_time = orderData.order_date,
                    order_name = it.store_id + " | "+ it.order_name,
                    address = it.user_id + " | " +orderData.address,
                    paymentStatus = "결제완료",
                    price = orderData.price,
                    status = OrderStatus.READY, // 기본 상태 설정
                    storeRequest = "", // 가게 요청사항 (기본값)
                    deliveryRequest = "", // 배달 요청사항 (기본값)
                    items = listOf(OrderItem(orderData.order_name, 1, "${orderData.price}원")) // 간단하게 아이템 설정
                )
            }

            viewModel.addOrder(newOrder)
        }
    }

    private fun initViews() {
        tabLayout = findViewById(R.id.tabLayout)
        chipGroup = findViewById(R.id.chipGroup)
        bottomNavigation = findViewById(R.id.bottomNavigation)
        orderRecyclerView = findViewById(R.id.orderRecyclerView)
    }

    private fun setupUI() {
        setupRecyclerView()
        setupTabLayout()
        setupChipGroup()
        setupBottomNavigation()
    }

    private fun setupRecyclerView() {
        orderAdapter = OrderAdapter(
            this::onOrderItemClick,
            this::onStatusButtonClick
        )
        orderRecyclerView.adapter = orderAdapter
        orderRecyclerView.layoutManager = LinearLayoutManager(this)
        orderRecyclerView.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
    }

    private fun setupTabLayout() {
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                viewModel.onTabSelected(tab?.position ?: 0)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupChipGroup() {
        chipGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.chipLatest -> viewModel.setSortOrder(SortOrder.LATEST)
                R.id.chipOldest -> viewModel.setSortOrder(SortOrder.OLDEST)
            }
        }
    }

    private fun setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> true
                R.id.nav_delivery -> {
                    startActivity(Intent(this, DeliveryActivity::class.java))
                    true
                }
                R.id.nav_done -> {
                    startActivity(Intent(this, DoneActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun onOrderItemClick(order: Order) {
        val intent = Intent(this, DetailActivity::class.java).apply {
            putExtra("orderId", order.id)
            putExtra("orderStatus", order.status.name)
        }
        startActivityForResult(intent, ORDER_DETAILS_REQUEST_CODE)
    }

    private fun onStatusButtonClick(order: Order) {
        viewModel.updateOrderStatus(order)
    }

    private fun observeViewModel() {
        viewModel.orders.observe(this) { orders ->
            orderAdapter.updateOrders(orders)
        }

        viewModel.navigateToDelivery.observe(this) { shouldNavigate ->
            if (shouldNavigate) {
                startActivity(Intent(this, DeliveryActivity::class.java))
                viewModel.onDeliveryNavigated()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mqttManager.disconnect() // MQTT 연결 해제
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ORDER_DETAILS_REQUEST_CODE && resultCode == RESULT_OK) {
            viewModel.loadOrders()
        }
    }

    companion object {
        const val ORDER_DETAILS_REQUEST_CODE = 1001
    }
}
