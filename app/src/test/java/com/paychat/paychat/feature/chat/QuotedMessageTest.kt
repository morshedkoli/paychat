package com.paychat.paychat.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class QuotedMessageTest {

    @Test
    fun `encodes and decodes quoted reply correctly`() {
        val author = "Rahim"
        val snippet = "Can you send ৳500 for lunch?"
        val reply = "Sure, sent just now!"

        val encoded = QuotedMessageCodec.encode(author, snippet, reply)
        val decoded = QuotedMessageCodec.decode(encoded)

        assertNotNull(decoded.quote)
        assertEquals("Rahim", decoded.quote?.authorName)
        assertEquals("Can you send ৳500 for lunch?", decoded.quote?.textSnippet)
        assertEquals("Sure, sent just now!", decoded.body)
    }

    @Test
    fun `plain text without quote decodes with null quote`() {
        val plain = "Hello there, how are you?"
        val decoded = QuotedMessageCodec.decode(plain)

        assertNull(decoded.quote)
        assertEquals("Hello there, how are you?", decoded.body)
    }

    @Test
    fun `snippet with colons and newlines cleans up cleanly`() {
        val author = "Karim:Admin\n"
        val snippet = "Line 1\nLine 2 with: colons"
        val reply = "Got it"

        val encoded = QuotedMessageCodec.encode(author, snippet, reply)
        val decoded = QuotedMessageCodec.decode(encoded)

        assertNotNull(decoded.quote)
        assertEquals("Karim Admin", decoded.quote?.authorName)
        assertEquals("Line 1 Line 2 with: colons", decoded.quote?.textSnippet)
        assertEquals("Got it", decoded.body)
    }
}
