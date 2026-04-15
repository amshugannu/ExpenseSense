package com.amshu.expensesense

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CategoryFilterAdapter(
    private val categories: List<String>,
    private val onCategorySelected: (String) -> Unit
) : RecyclerView.Adapter<CategoryFilterAdapter.FilterViewHolder>() {

    private var selectedPosition = 0

    class FilterViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvFilterName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilterViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_filter_chip, parent, false)
        return FilterViewHolder(view)
    }

    override fun onBindViewHolder(holder: FilterViewHolder, position: Int) {
        val category = categories[position]
        holder.tvName.text = category
        
        val isSelected = position == selectedPosition
        holder.itemView.isSelected = isSelected
        
        if (isSelected) {
            holder.tvName.setTextColor(Color.parseColor("#529E97")) // Teal
        } else {
            holder.tvName.setTextColor(Color.parseColor("#999999")) // Gray
        }

        holder.itemView.setOnClickListener {
            val oldPos = selectedPosition
            selectedPosition = holder.adapterPosition
            notifyItemChanged(oldPos)
            notifyItemChanged(selectedPosition)
            onCategorySelected(category)
        }
    }

    override fun getItemCount() = categories.size
}
