package com.example.orderease

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.orderease.data.local.AppDatabase
import com.example.orderease.data.local.entities.Customer
import com.example.orderease.data.local.entities.Order
import com.example.orderease.data.local.entities.OrderItem as OrderItemEntity
import com.example.orderease.data.local.entities.Product
import com.example.orderease.utils.SessionManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AddOrderActivity : BaseActivity() {

    private lateinit var customerNameInput: AutoCompleteTextView
    private lateinit var phoneNumberInput: EditText
    private lateinit var collectionDateInput: EditText
    private lateinit var totalPriceText: TextView
    private lateinit var recyclerViewItems: RecyclerView
    private lateinit var sessionManager: SessionManager

    private val itemsInOrder = mutableListOf<AddOrderItemUI>()
    private lateinit var adapter: AddOrderAdapter
    private var availableProducts = listOf<Product>()
    private var allCustomers = listOf<Customer>()
    private var selectedCollectionDate = Calendar.getInstance()
    private val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_order)

        // Override BaseActivity's inset listener to remove the bottom padding that pushes buttons up
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        sessionManager = SessionManager(this)
        customerNameInput = findViewById(R.id.customer_name_input)
        phoneNumberInput = findViewById(R.id.phone_number_input)
        collectionDateInput = findViewById(R.id.collection_date_input)
        totalPriceText = findViewById(R.id.total_price_text)
        recyclerViewItems = findViewById(R.id.recyclerViewItems)

        // Setup Date Picker
        collectionDateInput.setText(dateFormatter.format(selectedCollectionDate.time))
        collectionDateInput.setOnClickListener { showDatePicker() }

        // Setup RecyclerView
        adapter = AddOrderAdapter(itemsInOrder, { availableProducts }, {
            updateOverallTotal()
        }, { position: Int ->
            removeItem(position)
        })
        
        adapter.setOnPlusClicked {
            addNewItem()
        }

        recyclerViewItems.layoutManager = LinearLayoutManager(this)
        recyclerViewItems.adapter = adapter

        // Load data
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            
            // Get current shop ID
            val username = sessionManager.getUsername()
            val shop = if (username != null) db.shopDao().getShopByUsername(username) else db.shopDao().getShop()
            val currentShopId = shop?.shopId ?: 1

            // Load products - ONLY show active ones
            availableProducts = db.productDao().getProductsByShop(currentShopId).first()
            
            // Load customers for AutoComplete
            allCustomers = db.customerDao().getAllCustomers().first()
            setupCustomerAutoComplete()

            if (itemsInOrder.isEmpty()) {
                addNewItem()
            }
            adapter.notifyDataSetChanged()
        }

        findViewById<ImageView>(R.id.menu_button).setOnClickListener { finish() }
        findViewById<Button>(R.id.back_to_main_button).setOnClickListener { finish() }
        findViewById<Button>(R.id.save_order_button).setOnClickListener { submitOrder() }
    }

    private fun setupCustomerAutoComplete() {
        // Create a list that combines name and phone for the dropdown display
        val customerDisplayList = allCustomers.map { 
            when {
                it.name.isNotEmpty() && it.phone.isNotEmpty() -> "${it.name} (${it.phone})"
                it.name.isNotEmpty() -> it.name
                it.phone.isNotEmpty() -> it.phone
                else -> "Unnamed Customer"
            }
        }
        val autoCompleteAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, customerDisplayList)
        customerNameInput.setAdapter(autoCompleteAdapter)

        customerNameInput.setOnItemClickListener { parent, _, position, _ ->
            val selectedDisplay = parent.getItemAtPosition(position) as String
            
            // Find the customer that matches exactly what was displayed in the dropdown
            val customer = allCustomers.find { 
                val display = when {
                    it.name.isNotEmpty() && it.phone.isNotEmpty() -> "${it.name} (${it.phone})"
                    it.name.isNotEmpty() -> it.name
                    it.phone.isNotEmpty() -> it.phone
                    else -> "Unnamed Customer"
                }
                display == selectedDisplay
            }
            
            customer?.let {
                // Update fields and move cursor to the end
                customerNameInput.setText(it.name)
                phoneNumberInput.setText(it.phone)
                customerNameInput.setSelection(customerNameInput.text.length)
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

    fun updateOverallTotal() {
        val totalCents = itemsInOrder.sumOf { it.selectedProduct?.cost?.times(it.quantity) ?: 0 }
        totalPriceText.text = getString(R.string.total_price_label, totalCents / 100.0)
    }

    private fun submitOrder() {
        val customerName = customerNameInput.text.toString().trim()
        val phoneNumber = phoneNumberInput.text.toString().trim()

        // Validation: At least name OR phone number must be provided
        if (customerName.isEmpty() && phoneNumber.isEmpty()) {
            Toast.makeText(this, "Please enter a customer name or phone number", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            
            // Get current shop ID
            val username = sessionManager.getUsername()
            val shop = if (username != null) db.shopDao().getShopByUsername(username) else db.shopDao().getShop()
            val currentShopId = shop?.shopId ?: 1

            // Logic to find an existing customer to prevent duplicates
            val existingCustomer = allCustomers.find { 
                if (phoneNumber.isNotEmpty()) {
                    // If phone is provided, it is the primary unique identifier
                    it.phone == phoneNumber
                } else {
                    // If no phone provided, match by name only if the existing record also has no phone
                    it.name.equals(customerName, ignoreCase = true) && it.phone.isEmpty()
                }
            }

            val customerId = if (existingCustomer != null) {
                existingCustomer.customerId
            } else {
                db.customerDao().insertCustomer(
                    Customer(name = customerName, phone = phoneNumber, notes = "")
                ).toInt()
            }

            // 2. Create Order
            val order = Order(
                paymentStatus = false,
                orderDate = System.currentTimeMillis(),
                collectionDate = selectedCollectionDate.timeInMillis,
                collectionStatus = false,
                shopId = currentShopId,
                customerId = customerId
            )
            val newOrderId = db.orderDao().insertOrderWithId(order).toInt()

            // 3. Create Order Items
            itemsInOrder.forEach { uiItem ->
                uiItem.selectedProduct?.let { product ->
                    db.orderItemDao().insertOrderItem(
                        OrderItemEntity(
                            quantity = uiItem.quantity,
                            totalPrice = product.cost * uiItem.quantity,
                            orderId = newOrderId,
                            productId = product.productId
                        )
                    )
                }
            }

            Toast.makeText(this@AddOrderActivity, getString(R.string.order_saved), Toast.LENGTH_SHORT).show()
            
            // Sync to Firebase if online
            val syncManager = FirebaseSyncManager(applicationContext)
            if (syncManager.isOnline()) {
                syncManager.syncLocalToFirebase()
            }
            
            finish()
        }
    }
}
