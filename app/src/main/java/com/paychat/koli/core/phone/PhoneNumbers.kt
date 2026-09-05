package com.paychat.koli.core.phone

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every phone number in the system is stored in E.164 form. This is the single
 * place that converts anything a user or the address book gives us into that
 * form, so that lookups by phone always match.
 */
@Singleton
class PhoneNumbers @Inject constructor() {

    private val util: PhoneNumberUtil = PhoneNumberUtil.getInstance()

    /**
     * @param raw anything: "01712345678", "+8801712345678", "0171 234 5678"
     * @param defaultRegion ISO region used when [raw] has no country code
     * @return E.164 string, or null when the number is not valid
     */
    fun toE164(raw: String, defaultRegion: String = DEFAULT_REGION): String? = try {
        val parsed = util.parse(raw.trim(), defaultRegion)
        if (util.isValidNumber(parsed)) {
            util.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
        } else {
            null
        }
    } catch (e: NumberParseException) {
        null
    }

    /** "+8801712345678" -> "+880 1712-345678" for display. */
    fun formatForDisplay(e164: String, defaultRegion: String = DEFAULT_REGION): String = try {
        val parsed = util.parse(e164, defaultRegion)
        util.format(parsed, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL)
    } catch (e: NumberParseException) {
        e164
    }

    companion object {
        const val DEFAULT_REGION = "BD"
    }
}
