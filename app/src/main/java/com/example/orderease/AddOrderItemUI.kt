package com.example.orderease

import com.example.orderease.data.local.entities.Product

data class AddOrderItemUI(
    var selectedProduct: Product?,
    var quantity: Int
)
