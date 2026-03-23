package com.example.es

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class WalletFragment : Fragment() {

    private lateinit var btnTabCards: TextView
    private lateinit var btnTabAccounts: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_wallet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnTabCards = view.findViewById(R.id.btnTabCards)
        btnTabAccounts = view.findViewById(R.id.btnTabAccounts)

        btnTabCards.setOnClickListener { switchTab(true) }
        btnTabAccounts.setOnClickListener { switchTab(false) }

        // Start with Accounts by default as per user request (showing the UI) 
        // Or Cards first? User said "implement navigation as shown in image", usually Cards is first.
        // But the user focused on Accounts UI. Let's go with Accounts as active by default to show my work.
        // Default to Cards tab for debugging card visibility
        switchTab(true)
    }

    private fun switchTab(isCards: Boolean) {
        // Update Buttons UI
        btnTabCards.background = ContextCompat.getDrawable(
            requireContext(),
            if (isCards) R.drawable.toggle_selector_active else android.R.color.transparent
        )
        btnTabCards.setTextColor(if (isCards) 0xFF555555.toInt() else 0xFF999999.toInt())

        btnTabAccounts.background = ContextCompat.getDrawable(
            requireContext(),
            if (!isCards) R.drawable.toggle_selector_active else android.R.color.transparent
        )
        btnTabAccounts.setTextColor(if (!isCards) 0xFF555555.toInt() else 0xFF999999.toInt())

        // Swap Fragment
        val fragment = if (isCards) WalletCardsFragment() else WalletAccountsFragment()
        childFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.walletSubFragmentContainer, fragment)
            .commit()
    }
}
