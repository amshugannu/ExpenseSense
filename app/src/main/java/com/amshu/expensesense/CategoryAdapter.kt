package com.amshu.expensesense

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CategoryAdapter(
    private val categories: List<String>,
    private val onCategoryClick: (String) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    class CategoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivIcon: ImageView = view.findViewById(R.id.ivCategoryIcon)
        val tvName: TextView = view.findViewById(R.id.tvCategoryName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = categories[position]
        holder.tvName.text = category
        
        // Map category to icon
        val iconResId = getCategoryIcon(category)
        holder.ivIcon.setImageResource(iconResId)
        
        holder.itemView.setOnClickListener {
            onCategoryClick(category)
        }
    }

    override fun getItemCount() = categories.size

    companion object {
        fun getCategoryIcon(category: String): Int {
            return when (category.lowercase()) {
                "games" -> R.drawable.cat_games
                "movies" -> R.drawable.cat_movie
                "sports" -> R.drawable.cat_sports
                "food", "dinner" -> R.drawable.cat_dinner
                "groceries" -> R.drawable.cat_groceries
                "drinks" -> R.drawable.cat_drinks
                "household supplies" -> R.drawable.cat_household_supplies
                "maintainance" -> R.drawable.cat_maintenance // Note: matched with 'maintenance' drawable and 'maintainance' string
                "rent" -> R.drawable.cat_rent
                "medical" -> R.drawable.cat_medical
                "taxes" -> R.drawable.cat_taxes
                "insurances" -> R.drawable.cat_insurance
                "gifts" -> R.drawable.cat_gifts
                "travel" -> R.drawable.cat_travel
                "fuel" -> R.drawable.cat_fuel
                "internet" -> R.drawable.cat_internet
                "gas" -> R.drawable.cat_gas
                "bills" -> R.drawable.cat_bills
                "shopping" -> R.drawable.cat_shopping
                "subscriptions" -> R.drawable.cat_subscriptions
                "other" -> R.drawable.cat_other
                else -> R.drawable.cat_other
            }
        }
    }
}
