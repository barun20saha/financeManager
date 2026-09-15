package com.example.myaccounts.utils

import java.text.SimpleDateFormat
import java.util.*

data class HeatmapDay(
    val isoDate: String,
    val count: Int,
    val dayOfWeek: Int, // 0 = Sunday, 6 = Saturday
    val month: Int,     // 0 = Jan, 11 = Dec
    val dayOfMonth: Int,
    val isFuture: Boolean
)

data class MonthLabel(
    val weekIndex: Int,
    val label: String
)

data class HeatmapResult(
    val year: Int,
    val weeks: List<List<HeatmapDay?>>,
    val monthLabels: List<MonthLabel>,
    val totalActivities: Int,
    val activeDays: Int,
    val maxStreak: Int
)

object HeatmapUtils {

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val monthNames = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    fun buildHeatmap(year: Int, dayCountMap: Map<String, Int>): HeatmapResult {
        val todayIso = DateUtils.todayIso()
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, Calendar.JANUARY)
        cal.set(Calendar.DAY_OF_MONTH, 1)

        val totalDays = DateUtils.daysInYear(year)
        val days = mutableListOf<HeatmapDay>()

        var totalActivities = 0
        var activeDays = 0
        var maxStreak = 0
        var currentStreak = 0

        for (i in 0 until totalDays) {
            val iso = isoFormat.format(cal.time)
            val count = dayCountMap[iso] ?: 0
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0 = Sun, 6 = Sat
            val month = cal.get(Calendar.MONTH)
            val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
            val isFuture = iso > todayIso

            if (!isFuture) {
                if (count > 0) {
                    totalActivities += count
                    activeDays += 1
                    currentStreak += 1
                    if (currentStreak > maxStreak) {
                        maxStreak = currentStreak
                    }
                } else {
                    currentStreak = 0
                }
            }

            days.add(
                HeatmapDay(
                    isoDate = iso,
                    count = count,
                    dayOfWeek = dayOfWeek,
                    month = month,
                    dayOfMonth = dayOfMonth,
                    isFuture = isFuture
                )
            )

            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        // Build weeks: each week has 7 slots (0 = Sun, 6 = Sat)
        val weeks = mutableListOf<MutableList<HeatmapDay?>>()
        var currentWeek = MutableList<HeatmapDay?>(7) { null }

        // First day of Jan might not be Sunday, pad empty slots
        val firstDay = days.first()
        for (pad in 0 until firstDay.dayOfWeek) {
            currentWeek[pad] = null
        }

        val monthLabels = mutableListOf<MonthLabel>()
        var lastMonthLabeled = -1

        days.forEach { day ->
            currentWeek[day.dayOfWeek] = day

            if (day.dayOfMonth <= 7 && day.month != lastMonthLabeled) {
                monthLabels.add(MonthLabel(weeks.size, monthNames[day.month]))
                lastMonthLabeled = day.month
            }

            if (day.dayOfWeek == 6) {
                weeks.add(currentWeek)
                currentWeek = MutableList(7) { null }
            }
        }

        if (currentWeek.any { it != null }) {
            weeks.add(currentWeek)
        }

        return HeatmapResult(
            year = year,
            weeks = weeks,
            monthLabels = monthLabels,
            totalActivities = totalActivities,
            activeDays = activeDays,
            maxStreak = maxStreak
        )
    }
}
