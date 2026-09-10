package com.paychat.paychat.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransactionNoteTest {

    @Test
    fun parseEmptyNote() {
        val parsed = TransactionNote.parse(null)
        assertEquals(TxnCategory.GENERAL, parsed.category)
        assertEquals("", parsed.text)
        assertNull(parsed.trxId)
    }

    @Test
    fun parseSimpleNoteWithoutCategoryOrTrxId() {
        val parsed = TransactionNote.parse("Lunch at cafe")
        assertEquals(TxnCategory.GENERAL, parsed.category)
        assertEquals("Lunch at cafe", parsed.text)
        assertNull(parsed.trxId)
    }

    @Test
    fun parseNoteWithCategory() {
        val parsed = TransactionNote.parse("[Food & Dining] Dinner with team")
        assertEquals(TxnCategory.FOOD, parsed.category)
        assertEquals("Dinner with team", parsed.text)
        assertNull(parsed.trxId)
    }

    @Test
    fun parseNoteWithCategoryAndTrxId() {
        val parsed = TransactionNote.parse("[Utilities] WiFi bill (TrxID: 9J2K5L)")
        assertEquals(TxnCategory.UTILITIES, parsed.category)
        assertEquals("WiFi bill", parsed.text)
        assertEquals("9J2K5L", parsed.trxId)
    }

    @Test
    fun parseNoteWithTrxIdOnly() {
        val parsed = TransactionNote.parse("TrxID: BKASH12345")
        assertEquals(TxnCategory.GENERAL, parsed.category)
        assertEquals("", parsed.text)
        assertEquals("BKASH12345", parsed.trxId)
    }

    @Test
    fun formatNoteWithCategoryAndTrxId() {
        val formatted = TransactionNote.format(
            category = TxnCategory.RENT,
            noteText = "Monthly apartment rent",
            trxId = "BL98765",
        )
        assertEquals("[Rent] Monthly apartment rent (TrxID: BL98765)", formatted)

        val reParsed = TransactionNote.parse(formatted)
        assertEquals(TxnCategory.RENT, reParsed.category)
        assertEquals("Monthly apartment rent", reParsed.text)
        assertEquals("BL98765", reParsed.trxId)
    }

    @Test
    fun formatGeneralNoteWithoutTrxId() {
        val formatted = TransactionNote.format(
            category = TxnCategory.GENERAL,
            noteText = "Just a note",
            trxId = null,
        )
        assertEquals("Just a note", formatted)
    }
}
