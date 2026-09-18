package com.paychat.paychat.feature.chat

data class QuotedMessage(
    val authorName: String,
    val textSnippet: String,
)

data class ParsedMessage(
    val quote: QuotedMessage?,
    val body: String,
)

object QuotedMessageCodec {
    private val QUOTE_REGEX = Regex("""^\[quote:([^:]*):([^\]]*)\]\n?([\s\S]*)""")

    fun encode(authorName: String, snippet: String, body: String): String {
        val cleanAuthor = authorName.replace(":", " ").replace("\n", " ").trim()
        val cleanSnippet = snippet.replace("\n", " ").take(100).trim()
        return "[quote:$cleanAuthor:$cleanSnippet]\n$body"
    }

    fun decode(rawText: String?): ParsedMessage {
        if (rawText.isNullOrBlank()) return ParsedMessage(null, rawText.orEmpty())

        val match = QUOTE_REGEX.find(rawText)
        if (match != null) {
            val author = match.groupValues[1].trim()
            val snippet = match.groupValues[2].trim()
            val body = match.groupValues[3]
            return ParsedMessage(
                quote = QuotedMessage(authorName = author, textSnippet = snippet),
                body = body,
            )
        }

        return ParsedMessage(quote = null, body = rawText)
    }
}
