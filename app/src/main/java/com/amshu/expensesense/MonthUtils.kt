package com.amshu.expensesense

import java.text.SimpleDateFormat
import java.util.*

object MonthUtils {

    /**
     * Returns the current month in YYYY-MM format.
     * Example: 2026-04
     */
    fun getCurrentMonthKey(): String {
        return SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
    }

    /**
     * Returns the previous month in YYYY-MM format.
     */
    fun getPreviousMonthKey(): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, -1)
        return SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(cal.time)
    }

    /**
     * Formats a YYYY-MM key into a user-friendly display string.
     * Example: "2026-04" -> "April 2026"
     */
    fun formatMonthDisplay(key: String): String {
        return try {
            val date = SimpleDateFormat("yyyy-MM", Locale.getDefault()).parse(key)
            SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(date!!)
        } catch (e: Exception) {
            key
        }
    }
}
