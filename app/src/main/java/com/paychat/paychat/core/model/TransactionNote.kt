package com.paychat.paychat.core.model

/**
 * Common spending and settlement categories.
 */
enum class TxnCategory(val displayName: String) {
    GENERAL("General"),
    FOOD("Food & Dining"),
    RENT("Rent"),
    GROCERY("Groceries"),
    UTILITIES("Utilities"),
    TRANSPORT("Transport"),
    SHOPPING("Shopping"),
    MEDICAL("Medical"),
    SETTLEMENT("Settlement");

    companion object {
        fun fromDisplayName(name: String?): TxnCategory? {
            if (name == null) return null
            return entries.find { it.displayName.equals(name.trim(), ignoreCase = true) }
        }
    }
}

/**
 * Parsed structure of a transaction's note field.
 *
 * Backwards and forwards compatible with plain string notes stored in Firestore
 * and SQLite, allowing category and MFS transaction ID tracking without
 * changing database schemas or breaking Firestore security rule shapes.
 */
data class TransactionNote(
    val category: TxnCategory = TxnCategory.GENERAL,
    val text: String = "",
    val trxId: String? = null,
) {
    val hasDetails: Boolean get() = text.isNotBlank() || category != TxnCategory.GENERAL || trxId != null

    companion object {
        private val CATEGORY_REGEX = Regex("""^\[([A-Za-z &]+)\]\s*""")
        private val TRX_ID_REGEX = Regex("""(?i)(?:\(?\bTrxID:\s*([A-Za-z0-9_-]+)\)?|\btrx:\s*([A-Za-z0-9_-]+))""")

        fun format(category: TxnCategory, noteText: String?, trxId: String?): String? {
            val cleanText = noteText?.trim().orEmpty()
            val cleanTrxId = trxId?.trim()?.takeIf { it.isNotEmpty() }

            val prefix = if (category != TxnCategory.GENERAL) "[${category.displayName}] " else ""
            val suffix = if (cleanTrxId != null) " (TrxID: $cleanTrxId)" else ""

            val combined = (prefix + cleanText + suffix).trim()
            return combined.ifEmpty { null }
        }

        fun parse(raw: String?): TransactionNote {
            if (raw.isNullOrBlank()) return TransactionNote()

            var remaining = raw.trim()
            var category = TxnCategory.GENERAL

            val categoryMatch = CATEGORY_REGEX.find(remaining)
            if (categoryMatch != null) {
                val matchedCat = TxnCategory.fromDisplayName(categoryMatch.groupValues[1])
                if (matchedCat != null) {
                    category = matchedCat
                    remaining = remaining.substring(categoryMatch.range.last + 1).trim()
                }
            }

            var trxId: String? = null
            val trxMatch = TRX_ID_REGEX.find(remaining)
            if (trxMatch != null) {
                trxId = trxMatch.groupValues[1].ifEmpty { trxMatch.groupValues[2] }
                remaining = (remaining.substring(0, trxMatch.range.first) +
                    remaining.substring(trxMatch.range.last + 1)).trim()
            }

            // Remove empty wrapping parentheses if left over
            if (remaining.startsWith("(") && remaining.endsWith(")")) {
                remaining = remaining.substring(1, remaining.length - 1).trim()
            }

            return TransactionNote(
                category = category,
                text = remaining,
                trxId = trxId,
            )
        }
    }
}
