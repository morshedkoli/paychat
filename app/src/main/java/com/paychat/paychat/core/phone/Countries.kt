package com.paychat.paychat.core.phone

import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.util.Locale

/**
 * One dialable country, as offered by the phone number picker.
 *
 * @param region ISO 3166-1 alpha-2 code, the form [PhoneNumbers] parses against
 * @param dialCode country calling code with no leading plus, because the field
 *   draws the plus itself
 */
data class Country(
    val region: String,
    val dialCode: String,
    val name: String,
    val flag: String,
)

/**
 * Every country a number can be entered for.
 *
 * Built from libphonenumber's own list of supported regions rather than a
 * hardcoded table, so it cannot drift from what the parser will accept, and
 * so a new calling code arrives with a library upgrade instead of a bug
 * report.
 */
object Countries {

    val all: List<Country> by lazy {
        val util = PhoneNumberUtil.getInstance()
        util.supportedRegions
            .map { region ->
                Country(
                    region = region,
                    dialCode = util.getCountryCodeForRegion(region).toString(),
                    name = displayName(region),
                    flag = flagOf(region),
                )
            }
            .sortedBy { it.name }
    }

    private val byRegion: Map<String, Country> by lazy { all.associateBy { it.region } }

    fun byRegion(region: String): Country? = byRegion[region.uppercase(Locale.ROOT)]

    /**
     * The countries a search box entry could mean.
     *
     * Matches a name, a dial code, or a region code, because someone looking
     * for their own country types whichever of the three they think of first.
     */
    fun matching(query: String): List<Country> {
        val needle = query.trim().removePrefix("+").lowercase(Locale.ROOT)
        if (needle.isEmpty()) return all
        return all.filter {
            it.name.lowercase(Locale.ROOT).contains(needle) ||
                it.dialCode.startsWith(needle) ||
                it.region.lowercase(Locale.ROOT) == needle
        }
    }

    /** The country the picker starts on, matching [PhoneNumbers.DEFAULT_REGION]. */
    val default: Country
        get() = byRegion(PhoneNumbers.DEFAULT_REGION) ?: all.first()

    private fun displayName(region: String): String =
        Locale.Builder().setRegion(region).build()
            .getDisplayCountry(Locale.ENGLISH)
            .ifBlank { region }

    /**
     * The flag emoji for a region, which is just its two letters shifted into
     * the regional indicator block. Cheaper and more complete than shipping
     * 200 drawables.
     */
    private fun flagOf(region: String): String {
        if (region.length != 2 || !region.all { it in 'A'..'Z' }) return ""
        val offset = 0x1F1E6 - 'A'.code
        return String(Character.toChars(region[0].code + offset)) +
            String(Character.toChars(region[1].code + offset))
    }
}
