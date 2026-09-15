package com.example.myaccounts.utils

import java.text.SimpleDateFormat
import java.util.*

object DateUtils {

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)

    fun todayIso(): String {
        return isoFormat.format(Date())
    }

    fun formatDisplayDate(isoDate: String): String {
        return try {
            val date = isoFormat.parse(isoDate)
            if (date != null) displayFormat.format(date) else isoDate
        } catch (e: Exception) {
            isoDate
        }
    }

    fun isLeapYear(year: Int): Boolean {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
    }

    fun daysInYear(year: Int): Int {
        return if (isLeapYear(year)) 366 else 365
    }

    fun daysInMonth(year: Int, monthZeroIndexed: Int): Int {
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, monthZeroIndexed)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }
}
