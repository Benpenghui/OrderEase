package com.example.orderease

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.orderease.data.local.entities.Product

class AddOrderAdapter(
    private val items: List<AddOrderItemUI>,
    private val getProducts: () -> List<Product>,
    private val onDataChanged: () -> Unit,
    private val onRemoveItem: (Int) -> Unit
) : RecyclerView.Adapter<AddOrderAdapter.ItemViewHolder>() {

    private var onPlusClickedInternal: () -> Unit = {}

    class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val itemSpinner: Spinner = view.findViewById(R.id.itemSpinner)
        val itemQuantity: EditText = view.findViewById(R.id.itemQuantity)
        val itemTotalPrice: TextView = view.findViewById(R.id.itemTotalPrice)
        val addItemButton: ImageButton = view.findViewById(R.id.addItemButton)
        val removeItemButton: ImageButton = view.findViewById(R.id.removeItemButton)
        val itemImage: ImageView = view.findViewById(R.id.itemImage)
    }

    fun setOnPlusClicked(callback: () -> Unit) {
        this.onPlusClickedInternal = callback
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_order, parent, false)
        return ItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val item = items[position]
        val products = getProducts()
        val productNames = products.map { it.name }

        val spinnerAdapter = ArrayAdapter(holder.itemView.context, android.R.layout.simple_spinner_item, productNames)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        holder.itemSpinner.adapter = spinnerAdapter

        val currentIndex = products.indexOf(item.selectedProduct)
        if (currentIndex >= 0) {
            holder.itemSpinner.setSelection(currentIndex, false)
        }

        // Load image for initial selection
        updateItemImage(holder.itemImage, item.selectedProduct)

        holder.itemSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                val newProduct = products[pos]
                if (item.selectedProduct != newProduct) {
                    item.selectedProduct = newProduct
                    updateRowTotal(holder, item)
                    updateItemImage(holder.itemImage, newProduct)
                    onDataChanged()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        holder.itemQuantity.tag?.let { holder.itemQuantity.removeTextChangedListener(it as TextWatcher) }
        
        val textWatcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val qty = s.toString().toIntOrNull() ?: 0
                if (item.quantity != qty) {
                    item.quantity = qty
                    updateRowTotal(holder, item)
                    onDataChanged()
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }
        holder.itemQuantity.setText(item.quantity.toString())
        holder.itemQuantity.addTextChangedListener(textWatcher)
        holder.itemQuantity.tag = textWatcher

        updateRowTotal(holder, item)

        holder.addItemButton.setOnClickListener {
            onPlusClickedInternal()
        }

        if (holder.bindingAdapterPosition == 0) {
            holder.removeItemButton.visibility = View.GONE
        } else {
            holder.removeItemButton.visibility = View.VISIBLE
            holder.removeItemButton.setOnClickListener {
                onRemoveItem(holder.bindingAdapterPosition)
            }
        }
    }

    private fun updateRowTotal(holder: ItemViewHolder, item: AddOrderItemUI) {
        val totalCents = (item.selectedProduct?.cost ?: 0) * item.quantity
        holder.itemTotalPrice.text = String.format("总价: $ %.2f", totalCents / 100.0)
    }

    private fun updateItemImage(imageView: ImageView, product: Product?) {
        Glide.with(imageView.context)
            .load(product?.imagePath)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_menu_gallery)
            .into(imageView)
    }

    override fun getItemCount() = items.size
}
