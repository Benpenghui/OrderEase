package com.example.orderease

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var orderAdapter: OrderAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawer_layout)
        recyclerView = findViewById(R.id.orders_recycler_view)

        // OnBackPressed handle for Drawer
        val onBackPressedCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                drawerLayout.closeDrawer(GravityCompat.END)
            }
        }
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                onBackPressedCallback.isEnabled = true
            }

            override fun onDrawerClosed(drawerView: View) {
                onBackPressedCallback.isEnabled = false
            }
        })

        // Setup RecyclerView
        val orders = listOf(
            Order( "老板娘", "早上 10 点", "$3.00"),
            Order("814134562",  "下午 12 点", "$3.00"),
            Order("林先生", "下午 2 点", "$19.20")
        )

        orderAdapter = OrderAdapter(orders)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = orderAdapter

        // Menu icon click
        findViewById<ImageView>(R.id.menu_icon).setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.END)
        }

        // Navigation menu
        val navView = findViewById<NavigationView>(R.id.nav_view)
        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_calendar -> {
                    Toast.makeText(this, "日历", Toast.LENGTH_SHORT).show()
                }
                R.id.nav_products -> {
                    Toast.makeText(this, "产品管理", Toast.LENGTH_SHORT).show()
                }
                R.id.nav_analytics -> {
                    Toast.makeText(this, "分析", Toast.LENGTH_SHORT).show()
                }
            }
            drawerLayout.closeDrawer(GravityCompat.END)
            true
        }

        // Bottom buttons
        findViewById<Button>(R.id.add_order_btn).setOnClickListener {
            Toast.makeText(this, "加单", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, AddOrderActivity::class.java)
            startActivity(intent)
        }

        findViewById<Button>(R.id.delete_btn).setOnClickListener {
            Toast.makeText(this, "删除", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.history_btn).setOnClickListener {
            Toast.makeText(this, "历史", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, HistoryActivity::class.java)
            startActivity(intent)
        }
    }
}

// Data class for Order
data class Order(
    val customer: String,
    val time: String,
    val price: String?
)

// RecyclerView Adapter
class OrderAdapter(private val orders: List<Order>) :
    RecyclerView.Adapter<OrderAdapter.OrderViewHolder>() {

    class OrderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val customerName: TextView = view.findViewById(R.id.customer_name)
        val orderTime: TextView = view.findViewById(R.id.order_time)
        val orderPrice: TextView = view.findViewById(R.id.order_price)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_order, parent, false)
        return OrderViewHolder(view)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        val order = orders[position]

        holder.customerName.text = order.customer

        holder.orderTime.text = order.time

        if (order.price != null) {
            holder.orderPrice.text = order.price
            holder.orderPrice.visibility = View.VISIBLE
        } else {
            holder.orderPrice.visibility = View.GONE
        }
    }

    override fun getItemCount() = orders.size
}
