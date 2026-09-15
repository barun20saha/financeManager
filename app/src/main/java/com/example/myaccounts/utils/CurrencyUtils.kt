package com.example.myaccounts.utils

import java.text.NumberFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToLong

object CurrencyUtils {

    fun formatInr(amount: Double): String {
        val absAmount = abs(amount)
        val inrFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
        inrFormat.maximumFractionDigits = if (absAmount % 1.0 == 0.0) 0 else 2
        inrFormat.minimumFractionDigits = 0
        val formatted = inrFormat.format(absAmount)
        // Ensure Rupee symbol
        return if (formatted.startsWith("₹")) formatted else "₹" + formatted.replace("[^0-9,.]".toRegex(), "")
    }

    fun roundPaise(amount: Double): Double {
        return (amount * 100.0).roundToLong() / 100.0
    }
}
