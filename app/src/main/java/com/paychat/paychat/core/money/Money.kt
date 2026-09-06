package com.paychat.paychat.core.money

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.absoluteValue

/**
 * An amount of Bangladeshi taka held as an integer number of poisha.
 *
 * Money never touches a floating point type anywhere in this application.
 * Construct from user input with [parse], never from a Double.
 */
@JvmInline
value class Money(val minor: Long) : Comparable<Money> {

    val isZero: Boolean get() = minor == 0L
    val isPositive: Boolean get() = minor > 0
    val isNegative: Boolean get() = minor < 0

    operator fun plus(other: Money) = Money(Math.addExact(minor, other.minor))
    operator fun minus(other: Money) = Money(Math.subtractExact(minor, other.minor))
    operator fun unaryMinus() = Money(Math.negateExact(minor))

    fun abs() = Money(minor.absoluteValue)

    override fun compareTo(other: Money) = minor.compareTo(other.minor)

    /** "1,250.50" - digits only, no symbol. */
    fun formatPlain(): String = plainFormat.format(BigDecimal.valueOf(minor, 2))

    /** "BDT 1,250.50" using the taka sign. */
    fun format(): String = SYMBOL + formatPlain()

    /** "+BDT 1,250.50" / "-BDT 1,250.50" - used in ledger rows. */
    fun formatSigned(): String = when {
        minor > 0 -> "+" + SYMBOL + abs().formatPlain()
        minor < 0 -> "-" + SYMBOL + abs().formatPlain()
        else -> SYMBOL + formatPlain()
    }

    override fun toString(): String = format()

    companion object {
        const val SYMBOL = "৳"          // Bengali taka sign
        const val CURRENCY_CODE = "BDT"
        val ZERO = Money(0)

        private val plainFormat: DecimalFormat
            get() = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))

        fun ofTaka(taka: Long) = Money(Math.multiplyExact(taka, 100L))

        /**
         * Parses free user input such as "1200", "1,200.5", " 1200.05 ".
         * Returns null when the text is not a well formed non-negative amount.
         * More than two decimal places is rejected rather than silently rounded,
         * so the user is never surprised by a changed amount.
         */
        fun parse(input: String): Money? {
            val cleaned = input.trim().replace(",", "").replace("৳", "")
            if (cleaned.isEmpty()) return null
            if (!cleaned.matches(Regex("""\d+(\.\d{0,2})?"""))) return null
            return try {
                val minor = BigDecimal(cleaned)
                    .setScale(2, RoundingMode.UNNECESSARY)
                    .movePointRight(2)
                    .longValueExact()
                Money(minor)
            } catch (e: ArithmeticException) {
                null
            }
        }
    }
}

fun Iterable<Money>.sum(): Money = fold(Money.ZERO) { acc, m -> acc + m }
