package com.example.es

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.example.es.R

class CardOptionAdapter(
    private val context: Context,
    private var options: List<CardOption>,
    private val onSelect: (CardOption) -> Unit
) : RecyclerView.Adapter<CardOptionAdapter.CardOptionViewHolder>() {

    private var selectedPosition: Int = RecyclerView.NO_ID.toInt()

    inner class CardOptionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cvThumb: CardView = view.findViewById(R.id.cvCardThumb)
        val ivThumb: ImageView = view.findViewById(R.id.ivCardThumb)
        val layoutOther: LinearLayout = view.findViewById(R.id.layoutOtherPlaceholder)
        val ivCheck: ImageView = view.findViewById(R.id.ivSelectedCheck)
        val tvName: TextView = view.findViewById(R.id.tvCardOptionName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardOptionViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_card_option, parent, false)
        return CardOptionViewHolder(view)
    }

    override fun onBindViewHolder(holder: CardOptionViewHolder, position: Int) {
        val option = options[position]
        val isSelected = position == selectedPosition

        holder.tvName.text = option.displayName

        if (option.isCustom) {
            // "Other Card" — show dashed placeholder, hide image
            holder.ivThumb.visibility = View.GONE
            holder.layoutOther.visibility = View.VISIBLE
        } else {
            holder.ivThumb.visibility = View.VISIBLE
            holder.layoutOther.visibility = View.GONE

            // Resolve drawable by name
            val resId = context.resources.getIdentifier(
                option.drawableName, "drawable", context.packageName
            )
            if (resId != 0) {
                holder.ivThumb.setImageResource(resId)
            } else {
                holder.ivThumb.setImageResource(R.drawable.defaultdebitcard)
            }
        }

        // Selection state
        if (isSelected) {
            holder.ivCheck.visibility = View.VISIBLE
            holder.cvThumb.cardElevation = 8f
            holder.itemView.scaleX = 1.04f
            holder.itemView.scaleY = 1.04f
        } else {
            holder.ivCheck.visibility = View.GONE
            holder.cvThumb.cardElevation = 4f
            holder.itemView.scaleX = 1f
            holder.itemView.scaleY = 1f
        }

        holder.itemView.setOnClickListener {
            val prev = selectedPosition
            selectedPosition = holder.adapterPosition
            notifyItemChanged(prev)
            notifyItemChanged(selectedPosition)
            onSelect(option)
        }
    }

    override fun getItemCount() = options.size

    fun updateOptions(newOptions: List<CardOption>) {
        options = newOptions
        selectedPosition = RecyclerView.NO_ID.toInt()
        notifyDataSetChanged()
    }

    fun getSelectedOption(): CardOption? =
        if (selectedPosition >= 0 && selectedPosition < options.size) options[selectedPosition]
        else null

    fun clearSelection() {
        val prev = selectedPosition
        selectedPosition = RecyclerView.NO_ID.toInt()
        if (prev >= 0) notifyItemChanged(prev)
    }
}
