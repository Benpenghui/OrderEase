package com.example.orderease

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.orderease.data.local.AppDatabase
import com.example.orderease.data.local.entities.OrderWithCustomerAndItems
import com.example.orderease.utils.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class DayOrdersActivity : BaseActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var orderAdapter: OrderAdapter
    private lateinit var titleText: TextView
    private var isDeleteMode = false
    private var isEditMode = false
    private var selectedDateMillis: Long = 0
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_day_orders)

        sessionManager = SessionManager(this)
        selectedDateMillis = intent.getLongExtra("SELECTED_DATE", System.currentTimeMillis())
        val dateSdf = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
        
        titleText = findViewById(R.id.title)
        titleText.text = getString(R.string.date_orders_title, dateSdf.format(Date(selectedDateMillis)))

        recyclerView = findViewById(R.id.orders_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)

        orderAdapter = OrderAdapter(emptyList(), { orderToDelete ->
            showDeleteConfirmationDialog(orderToDelete)
        }, { orderToDetail ->
            if (!isEditMode && !isDeleteMode) {
                showOrderDetailsDialog(orderToDetail)
            }
        }, { orderToEdit ->
            val intent = Intent(this, EditOrderActivity::class.java)
            intent.putExtra("ORDER_ID", orderToEdit.order.orderId)
            startActivity(intent)
        }, { orderToMarkPaid ->
            markOrderAsPaid(orderToMarkPaid)
        })
        recyclerView.adapter = orderAdapter

        findViewById<ImageView>(R.id.back_button).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.edit_mode_btn).setOnClickListener {
            toggleEditMode(!isEditMode)
        }

        findViewById<Button>(R.id.delete_mode_btn).setOnClickListener {
            toggleDeleteMode(!isDeleteMode)
        }

        loadDayOrders()
    }

    private fun toggleDeleteMode(enabled: Boolean) {
        isDeleteMode = enabled
        isEditMode = false
        orderAdapter.setDeleteMode(isDeleteMode)
        orderAdapter.setEditMode(isEditMode)
    }

    private fun toggleEditMode(enabled: Boolean) {
        isEditMode = enabled
        isDeleteMode = false
        orderAdapter.setEditMode(isEditMode)
        orderAdapter.setDeleteMode(isDeleteMode)
    }

    private fun loadDayOrders() {
        val cal = Calendar.getInstance()
        cal.timeInMillis = selectedDateMillis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfDay = cal.timeInMillis

        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        val endOfDay = cal.timeInMillis

        val db = AppDatabase.getDatabase(this)
        lifecycleScope.launch {
            val username = sessionManager.getUsername()
            val shop = if (username != null) db.shopDao().getShopByUsername(username) else db.shopDao().getShop()
            val shopId = shop?.shopId ?: 1

            db.orderDao().getOrdersWithDetailsInRange(shopId, startOfDay, endOfDay).collectLatest { orders ->
                orderAdapter.updateOrders(orders)
            }
        }
    }

    private fun showDeleteConfirmationDialog(orderWithDetails: OrderWithCustomerAndItems) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_order_title)
            .setMessage(getString(R.string.delete_order_confirm, orderWithDetails.order.orderId.toString()))
            .setPositiveButton(R.string.yes) { _, _ ->
                deleteOrder(orderWithDetails)
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    private fun showOrderDetailsDialog(orderWithDetails: OrderWithCustomerAndItems) {
        val sb = StringBuilder()
        sb.append(getString(R.string.order_id_label)).append("${orderWithDetails.order.orderId}\n")
        sb.append(getString(R.string.customer_label_prefix)).append("${orderWithDetails.customer.name}\n")
        sb.append(getString(R.string.phone_label)).append("${orderWithDetails.customer.phone}\n")
        val collectionSdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        sb.append(getString(R.string.collection_date_label)).append("${collectionSdf.format(Date(orderWithDetails.order.collectionDate))}\n")
        
        val status = if (orderWithDetails.order.paymentStatus) getString(R.string.payment_paid) else getString(R.string.payment_unpaid)
        sb.append(getString(R.string.payment_status_label)).append(status).append("\n\n")
        
        sb.append(getString(R.string.items_label)).append("\n")
        
        var totalCents = 0
        orderWithDetails.items.forEach { itemWithProduct ->
            val product = itemWithProduct.product
            val item = itemWithProduct.orderItem
            val linePrice = item.totalPrice
            totalCents += linePrice
            sb.append("- ${product.name} x${item.quantity} ($${String.format("%.2f", linePrice / 100.0)})\n")
        }
        sb.append("\n").append(getString(R.string.total_price_label, totalCents / 100.0))

        AlertDialog.Builder(this)
            .setTitle(R.string.order_details_title)
            .setMessage(sb.toString())
            .setPositiveButton(R.string.edit_btn) { _, _ ->
                val intent = Intent(this, EditOrderActivity::class.java)
                intent.putExtra("ORDER_ID", orderWithDetails.order.orderId)
                startActivity(intent)
            }
            .setNegativeButton(R.string.close_btn, null)
            .show()
    }

    private fun deleteOrder(orderWithDetails: OrderWithCustomerAndItems) {
        val db = AppDatabase.getDatabase(this)
        val syncManager = FirebaseSyncManager(this)
        lifecycleScope.launch(Dispatchers.IO) {
            db.orderDao().deleteOrder(orderWithDetails.order)
            syncManager.deleteOrderFromFirebase(orderWithDetails.order.orderId)
        }
    }

    private fun markOrderAsPaid(orderWithDetails: OrderWithCustomerAndItems) {
        val db = AppDatabase.getDatabase(this)
        val syncManager = FirebaseSyncManager(this)
        lifecycleScope.launch(Dispatchers.IO) {
            val updatedOrder = orderWithDetails.order.copy(paymentStatus = true)
            db.orderDao().updateOrder(updatedOrder)
            if (syncManager.isOnline()) {
                syncManager.syncLocalToFirebase()
            }
        }
    }
}
