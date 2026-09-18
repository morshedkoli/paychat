package com.paychat.paychat.core.ledger

import com.paychat.paychat.ui.components.Timestamps
import java.util.Calendar
import java.util.concurrent.TimeUnit

enum class DueStatus {
    OVERDUE,
    DUE_TODAY,
    DUE_TOMORROW,
    UPCOMING,
}

data class DueDateInfo(
    val status: DueStatus,
    val label: String,
    val isAlert: Boolean,
)

object DueDateHelper {
    fun evaluate(dueDateMillis: Long, now: Long = System.currentTimeMillis()): DueDateInfo {
        val calDue = Calendar.getInstance().apply {
            timeInMillis = dueDateMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val calNow = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val diffMillis = calDue.timeInMillis - calNow.timeInMillis
        val diffDays = TimeUnit.MILLISECONDS.toDays(diffMillis)

        return when {
            diffDays < 0 -> {
                val overdueDays = -diffDays
                val dayStr = if (overdueDays == 1L) "1 day" else "$overdueDays days"
                DueDateInfo(DueStatus.OVERDUE, "Overdue by $dayStr", isAlert = true)
            }
            diffDays == 0L -> DueDateInfo(DueStatus.DUE_TODAY, "Due today", isAlert = true)
            diffDays == 1L -> DueDateInfo(DueStatus.DUE_TOMORROW, "Due tomorrow", isAlert = false)
            else -> DueDateInfo(
                DueStatus.UPCOMING,
                "Due ${Timestamps.daySeparator(dueDateMillis, now)}",
                isAlert = false,
            )
        }
    }
}
