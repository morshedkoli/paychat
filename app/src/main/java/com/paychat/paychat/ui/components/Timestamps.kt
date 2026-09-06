package com.paychat.paychat.ui.components

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Chat-style timestamps: today shows a clock time, yesterday says so, anything
 * older shows a date. Kept as plain functions so they can be unit tested.
 */
object Timestamps {

    fun forThreadList(at: Long, now: Long = System.currentTimeMillis()): String = when {
        at == 0L -> ""
        isSameDay(at, now) -> time(at)
        isYesterday(at, now) -> "Yesterday"
        isSameYear(at, now) -> dayAndMonth(at)
        else -> fullDate(at)
    }

    fun forMessage(at: Long): String = time(at)

    fun daySeparator(at: Long, now: Long = System.currentTimeMillis()): String = when {
        isSameDay(at, now) -> "Today"
        isYesterday(at, now) -> "Yesterday"
        isSameYear(at, now) -> dayAndMonth(at)
        else -> fullDate(at)
    }

    fun isSameDay(a: Long, b: Long): Boolean {
        val first = calendar(a)
        val second = calendar(b)
        return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
            first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
    }

    private fun isYesterday(at: Long, now: Long): Boolean {
        val yesterday = calendar(now).apply { add(Calendar.DAY_OF_YEAR, -1) }
        return isSameDay(at, yesterday.timeInMillis)
    }

    private fun isSameYear(a: Long, b: Long) =
        calendar(a).get(Calendar.YEAR) == calendar(b).get(Calendar.YEAR)

    private fun calendar(at: Long) = Calendar.getInstance().apply { timeInMillis = at }

    private fun time(at: Long) = format("h:mm a", at)
    private fun dayAndMonth(at: Long) = format("d MMM", at)
    private fun fullDate(at: Long) = format("d MMM yyyy", at)

    private fun format(pattern: String, at: Long) =
        SimpleDateFormat(pattern, Locale.getDefault()).format(Date(at))
}
