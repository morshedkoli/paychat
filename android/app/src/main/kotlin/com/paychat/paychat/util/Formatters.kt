package com.paychat.paychat.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Formatters {
    private val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.US).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 2
    }

    private val compactDateFormatter = SimpleDateFormat("dd MMM, yyyy", Locale.getDefault())
    private val timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())

    fun currency(value: Double): String = currencyFormatter.format(value)

    fun compactDate(date: Date): String = compactDateFormatter.format(date)

    fun time(date: Date): String = timeFormatter.format(date)

    fun relativeGreeting(): String {
        return when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 0..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    fun initials(value: String): String {
        val parts = value.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return when {
            parts.isEmpty() -> "P"
            parts.size == 1 -> parts.first().first().uppercase()
            else -> "${parts.first().first()}${parts.last().first()}".uppercase()
        }
    }
}
