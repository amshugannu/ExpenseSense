package com.amshu.expensesense

import android.content.Context
import android.content.SharedPreferences

object BalancePrivacyManager {
    private const val PREFS_NAME = "PrivacySettingsPrefs"
    private const val KEY_PRIVACY_MODE = "balance_privacy_mode"
    private const val KEY_BALANCE_PIN = "balance_security_pin"

    // Modes:
    // 0 -> Always Visible
    // 1 -> Show on Click (5s)
    // 2 -> Show on Click + PIN (20s)
    const val MODE_ALWAYS_VISIBLE = 0
    const val MODE_SHOW_ON_CLICK = 1
    const val MODE_SHOW_ON_CLICK_PIN = 2

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getPrivacyMode(context: Context): Int {
        return getPrefs(context).getInt(KEY_PRIVACY_MODE, MODE_ALWAYS_VISIBLE)
    }

    fun setPrivacyMode(context: Context, mode: Int) {
        getPrefs(context).edit().putInt(KEY_PRIVACY_MODE, mode).apply()
    }

    fun getPIN(context: Context): String? {
        return getPrefs(context).getString(KEY_BALANCE_PIN, null)
    }

    fun setPIN(context: Context, pin: String) {
        getPrefs(context).edit().putString(KEY_BALANCE_PIN, pin).apply()
    }

    fun isPinSet(context: Context): Boolean {
        return !getPIN(context).isNullOrEmpty()
    }

    fun maskBalance(balance: String): String {
        // Expected format: "Bal: ₹1,234.56" or "Avl: ₹50,000.00"
        val prefix = if (balance.contains(":")) balance.substringBefore(":") + ":" else ""
        return "$prefix ₹ *****"
    }
}
