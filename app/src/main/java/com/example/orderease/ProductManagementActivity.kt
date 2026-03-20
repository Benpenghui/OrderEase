package com.example.orderease

import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.orderease.data.local.AppDatabase
import com.example.orderease.data.local.entities.Product
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.*

class ProductManagementActivity : BaseActivity() {

    private lateinit var productSpinner: Spinner
    private lateinit var nameInput: EditText
    private lateinit var priceInput: EditText
    private lateinit var productImage: ImageView
    private lateinit var changeImageBtn: Button
    private lateinit var updateBtn: Button
    private lateinit var backBtn: Button

    private var currentProducts: List<Product> = emptyList()
    private var selectedProduct: Product? = null
    private var selectedImageUri: Uri? = null

    private val driveScriptUrl = "https://script.google.com/macros/s/AKfycbxyBAXCQqSrW4I1qONjuDuiyYqgbxGu9nor5Vt66Wd1fis_E6p3uBg1l-MJv7BmL7W1/exec"
    private val driveFolderId = "1aU68sW3D8vQhA3seqHyRrSkp0sxDhoCU"

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            Glide.with(this).load(it).into(productImage)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product_management)

        productSpinner = findViewById(R.id.product_spinner)
        nameInput = findViewById(R.id.product_name_input)
        priceInput = findViewById(R.id.product_price_input)
        productImage = findViewById(R.id.product_image)
        changeImageBtn = findViewById(R.id.change_image_btn)
        updateBtn = findViewById(R.id.update_btn)
        backBtn = findViewById(R.id.back_btn)

        val db = AppDatabase.getDatabase(this)

        lifecycleScope.launch {
            val shop = db.shopDao().getShop()
            if (shop != null) {
                db.productDao().getProductsByShop(shop.shopId).collectLatest { products ->
                    currentProducts = products
                    
                    val previouslySelectedId = selectedProduct?.productId
                    setupSpinner(products, previouslySelectedId)
                }
            }
        }

        productSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == 0) {
                    // "Add New" selected
                    selectedProduct = null
                    clearFields()
                    updateBtn.text = getString(R.string.add_btn_text)
                } else if (position > 0 && position <= currentProducts.size) {
                    selectedProduct = currentProducts[position - 1]
                    populateFields(selectedProduct!!)
                    updateBtn.text = getString(R.string.update_btn_text)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        changeImageBtn.setOnClickListener { pickImageLauncher.launch("image/*") }
        updateBtn.setOnClickListener { handleSaveOrUpdate() }
        backBtn.setOnClickListener { finish() }
    }

    private fun setupSpinner(products: List<Product>, previouslySelectedId: Int?) {
        val addNewText = getString(R.string.add_new_product)
        val productNames = mutableListOf(addNewText)
        productNames.addAll(products.map { it.name })
        
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, productNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        productSpinner.adapter = adapter

        if (previouslySelectedId != null) {
            val index = products.indexOfFirst { it.productId == previouslySelectedId }
            if (index != -1) productSpinner.setSelection(index + 1)
        } else {
            productSpinner.setSelection(0)
        }
    }

    private fun populateFields(product: Product) {
        nameInput.setText(product.name)
        priceInput.setText(String.format(Locale.getDefault(), "%.2f", product.cost / 100.0))
        
        Glide.with(this)
            .load(product.imagePath)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_menu_gallery)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(true)
            .into(productImage)
    }

    private fun clearFields() {
        nameInput.setText("")
        priceInput.setText("")
        productImage.setImageResource(android.R.drawable.ic_menu_gallery)
        selectedImageUri = null
    }

    private fun handleSaveOrUpdate() {
        val newName = nameInput.text.toString().trim()
        val priceStr = priceInput.text.toString().trim()

        if (newName.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_empty_name), Toast.LENGTH_SHORT).show()
            return
        }
        val newPriceCents = (priceStr.toDoubleOrNull()?.times(100))?.toInt() ?: 0

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(this@ProductManagementActivity)
            val shopId = db.shopDao().getShop()?.shopId ?: 1
            
            var finalImagePath = selectedProduct?.imagePath
            var uploadSuccessful = true

            if (selectedImageUri != null) {
                val existingFileId = extractFileId(selectedProduct?.imagePath)
                val driveUrl = uploadToGoogleDrive(selectedImageUri!!, shopId, newName, existingFileId)
                
                if (!driveUrl.isNullOrEmpty()) {
                    finalImagePath = driveUrl
                } else {
                    val localPath = saveImageLocally(selectedImageUri!!, shopId, newName)
                    if (localPath != null) {
                        finalImagePath = localPath
                    } else {
                        uploadSuccessful = false
                    }
                }
            }

            if (selectedProduct == null) {
                // Add New
                val newProduct = Product(
                    name = newName,
                    cost = newPriceCents,
                    shopId = shopId,
                    imagePath = finalImagePath
                )
                db.productDao().insertProduct(newProduct)
            } else {
                // Update Existing
                val updatedProduct = selectedProduct!!.copy(
                    name = newName,
                    cost = newPriceCents,
                    imagePath = finalImagePath
                )
                db.productDao().updateProduct(updatedProduct)

                // Update prices for future orders (collection date >= today)
                val calendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val todayStart = calendar.timeInMillis

                db.orderItemDao().updateFutureOrderItemsPrice(
                    productId = selectedProduct!!.productId,
                    newPrice = newPriceCents,
                    minDate = todayStart
                )
            }

            val syncManager = FirebaseSyncManager(this@ProductManagementActivity)
            syncManager.syncLocalToFirebase()

            withContext(Dispatchers.Main) {
                val successMsg = if (selectedProduct == null) R.string.product_added else R.string.order_updated
                val message = if (uploadSuccessful) getString(successMsg) else "Action complete, but image upload failed."
                Toast.makeText(this@ProductManagementActivity, message, Toast.LENGTH_SHORT).show()
                
                if (selectedProduct == null) clearFields()
            }
        }
    }

    private fun extractFileId(url: String?): String? {
        if (url.isNullOrEmpty() || !url.contains("drive.google.com")) return null
        val regex = Regex("(?:id=|/d/)([a-zA-Z0-9_-]{25,})")
        return regex.find(url)?.groupValues?.get(1)
    }

    private suspend fun uploadToGoogleDrive(uri: Uri, shopId: Int, productName: String, existingFileId: String?): String? = withContext(Dispatchers.IO) {
        try {
            val bytes = contentResolver.openInputStream(uri)?.readBytes() ?: return@withContext null
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            
            val json = JSONObject().apply {
                put("base64", base64)
                put("mimeType", contentResolver.getType(uri) ?: "image/jpeg")
                put("fileName", "${shopId}_${productName.replace(" ", "_")}.jpg")
                put("folderId", driveFolderId)
                if (!existingFileId.isNullOrEmpty()) put("fileId", existingFileId)
            }

            val client = OkHttpClient.Builder().build()
            val requestBody = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder().url(driveScriptUrl).post(requestBody).build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@withContext null
                val jsonResponse = JSONObject(body)
                if (jsonResponse.has("error")) return@withContext null
                return@withContext jsonResponse.optString("url", null)
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun saveImageLocally(uri: Uri, shopId: Int, productName: String): String? = withContext(Dispatchers.IO) {
        try {
            val fileName = "${shopId}_${productName.replace(" ", "_")}.jpg"
            val file = File(filesDir, fileName)
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            }
            file.absolutePath
        } catch (e: Exception) { null }
    }
}
