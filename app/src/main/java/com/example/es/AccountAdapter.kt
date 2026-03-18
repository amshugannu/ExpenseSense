package com.example.es

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AccountAdapter(
    private val accounts: List<Account>,
    private val onEdit: (Account) -> Unit,
    private val onDelete: (Account) -> Unit
) : RecyclerView.Adapter<AccountAdapter.AccountViewHolder>() {

    class AccountViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvAccountName)
        val tvSubtitle: TextView = view.findViewById(R.id.tvAccountSubtitle)
        val tvBalance: TextView = view.findViewById(R.id.tvWalletBalance)
        val imgIcon: ImageView = view.findViewById(R.id.imgAccountIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccountViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_account_wallet, parent, false)
        return AccountViewHolder(view)
    }

    override fun onBindViewHolder(holder: AccountViewHolder, position: Int) {
        val account = accounts[position]
        holder.tvName.text = account.name
        holder.tvSubtitle.text = account.type ?: "Connected account"
        holder.tvBalance.text = "₹%.2f".format(account.balance)
        holder.tvBalance.visibility = View.VISIBLE

        // Icon Logic matching image
        val nameLower = account.name.lowercase()
        when {
            nameLower.contains("paypal") -> holder.imgIcon.setImageResource(R.drawable.ic_paypal)
            nameLower.contains("bank") -> holder.imgIcon.setImageResource(R.drawable.ic_bank)
            nameLower.contains("savings") -> holder.imgIcon.setImageResource(R.drawable.ic_savings)
            else -> holder.imgIcon.setImageResource(R.drawable.ic_bank)
        }
    }

    override fun getItemCount() = accounts.size
}
