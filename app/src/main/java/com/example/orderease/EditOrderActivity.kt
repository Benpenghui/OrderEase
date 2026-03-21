package com.example.orderease

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.orderease.data.local.AppDatabase
import com.example.orderease.data.local.entities.Customer
import com.example.orderease.data.local.entities.OrderItem as OrderItemEntity
import com.example.orderease.data.local.entities.Product
import com.example.orderease.utils.SessionManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class EditOrderActivity : AppCompatActivity() {

    private lateinit var customerNameInput: EditText
    private lateinit var phoneNumberInput: EditText
    private lateinit var collectionDateInput: EditText
    private lateinit var totalPriceText: TextView
    private lateinit var recyclerViewItems: RecyclerView
    private lateinit var sessionManager: SessionManager

    private val itemsInOrder = mutableListOf<AddOrderItemUI>()
    private lateinit var adapter: AddOrderAdapter
    private var availableProducts = listOf<Product>()
    private var currentOrderId: Int = -1
    private var selectedCollectionDate = Calendar.getInstance()
    private val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_order)

        sessionManager = SessionManager(this)
        customerNameInput = findViewById(R.id.customer_name_input)
        phoneNumberInput = findViewById(R.id.phone_number_input)
        collectionDateInput = findViewById(R.id.collection_date_input)
        totalPriceText = findViewById(R.id.total_price_text)
        recyclerViewItems = findViewById(R.id.recyclerViewItems)

        currentOrderId = intent.getIntExtra("ORDER_ID", -1)

        // Setup Date Picker
        collectionDateInput.setOnClickListener { showDatePicker() }

        adapter = AddOrderAdapter(itemsInOrder, { availableProducts }, {
            updateOverallTotal()
        }, { position ->
            removeItem(position)
        })
        
        adapter.setOnPlusClicked {
            addNewItem()
        }

        recyclerViewItems.layoutManager = LinearLayoutManager(this)
        recyclerViewItems.adapter = adapter

        if (currentOrderId != -1) {
            loadOrderData()
        } else {
            Toast.makeText(this, getString(R.string.invalid_order_id), Toast.LENGTH_SHORT).show()
            finish()
        }

        findViewById<ImageView>(R.id.menu_button).setOnClickListener { finish() }
        findViewById<Button>(R.id.back_to_main_button).setOnClickListener { finish() }
        findViewById<Button>(R.id.confirm_changes_button).setOnClickListener {
            updateOrder()
        }
    }

    private fun loadOrderData() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            
            // Get current shop ID
            val username = sessionManager.getUsername()
            val shop = if (username != null) db.shopDao().getShopByUsername(username) else db.shopDao().getShop()
            val currentShopId = shop?.shopId ?: 1

            // Load products - ONLY show active ones for selection
            availableProducts = db.productDao().getProductsByShop(currentShopId).first()

            val orderWithDetails = db.orderDao().getOrdersWithDetailsInRange(0, Long.MAX_VALUE).first()
                .find { it.order.orderId == currentOrderId }

            orderWithDetails?.let { details ->
                customerNameInput.setText(details.customer.name)
                phoneNumberInput.setText(details.customer.phone)
                
                selectedCollectionDate.timeInMillis = details.order.collectionDate
                collectionDateInput.setText(dateFormatter.format(selectedCollectionDate.time))

                itemsInOrder.clear()
                details.items.forEach { itemWithProduct ->
                    // Even if product is soft-deleted, we still show it in the order summary
                    itemsInOrder.add(AddOrderItemUI(itemWithProduct.product, itemWithProduct.orderItem.quantity))
                }
                adapter.notifyDataSetChanged()
                updateOverallTotal()
            }
        }
    }

    private fun showDatePicker() {
        DatePickerDialog(
            this,
            { _, year, month, day ->
                selectedCollectionDate.set(year, month, day)
                collectionDateInput.setText(dateFormatter.format(selectedCollectionDate.time))
            },
            selectedCollectionDate.get(Calendar.YEAR),
            selectedCollectionDate.get(Calendar.MONTH),
            selectedCollectionDate.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun addNewItem() {
        val defaultProduct = if (availableProducts.isNotEmpty()) availableProducts[0] else null
        itemsInOrder.add(AddOrderItemUI(defaultProduct, 1))
        adapter.notifyItemInserted(itemsInOrder.size - 1)
        updateOverallTotal()
    }

    private fun removeItem(position: Int) {
        if (itemsInOrder.size > 1 && position != RecyclerView.NO_POSITION) {
            itemsInOrder.removeAt(position)
            adapter.notifyItemRemoved(position)
            adapter.notifyItemRangeChanged(position, itemsInOrder.size)
            updateOverallTotal()
        }
    }

    private fun updateOverallTotal() {
        val totalCents = itemsInOrder.sumOf { it.selectedProduct?.cost?.times(it.quantity) ?: 0 }
        totalPriceText.text = getString(R.string.total_price_label, totalCents / 100.0)
    }

    private fun updateOrder() {
        val customerName = customerNameInput.text.toString()
        val phoneNumber = phoneNumberInput.text.toString()

        if (customerName.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_empty_customer_name), Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            
            val customerId = db.customerDao().insertCustomer(
                Customer(name = customerName, phone = phoneNumber, notes = "")
            ).toInt()

            val originalOrder = db.orderDao().getOrderById(currentOrderId)
            originalOrder?.let {
                val updatedOrder = it.copy(
                    customerId = customerId,
                    collectionDate = selectedCollectionDate.timeInMillis
                )
                db.orderDao().updateOrder(updatedOrder)
            }

            val oldItems = db.orderItemDao().getItemsForOrder(currentOrderId).first()
            oldItems.forEach { db.orderItemDao().deleteOrderItem(it) }

            itemsInOrder.forEach { uiItem ->
                uiItem.selectedProduct?.let { product ->
                    db.orderItemDao().insertOrderItem(
                        OrderItemEntity(
                            quantity = uiItem.quantity,
                            totalPrice = product.cost * uiItem.quantity,
                            orderId = currentOrderId,
                            productId = product.productId
                        )
                    )
                }
            }

            Toast.makeText(this@EditOrderActivity, getString(R.string.order_updated), Toast.LENGTH_SHORT).show()
            
            // Sync to Firebase if online
            val syncManager = FirebaseSyncManager(applicationContext)
            if (syncManager.isOnline()) {
                syncManager.syncLocalToFirebase()
            }

            finish()
        }
    }
}
