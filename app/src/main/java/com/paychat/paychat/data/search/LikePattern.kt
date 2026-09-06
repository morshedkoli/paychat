package com.paychat.paychat.data.search

/**
 * Builds the LIKE pattern a search runs against.
 *
 * The wildcards LIKE understands are ordinary characters to someone
 * searching for a note that contains them, so they are escaped rather than
 * honoured. The escape character itself is escaped first, or escaping the
 * wildcards would produce new ones.
 */
object LikePattern {

    fun containing(text: String): String = "%" + escape(text) + "%"

    fun escape(text: String): String = text
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
}
