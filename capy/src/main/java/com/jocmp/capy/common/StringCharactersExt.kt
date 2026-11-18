package com.jocmp.capy.common

/**
 * Returns the string with the special XML characters (other than single-quote) ampersand-escaped.
 *
 * The four escaped characters are `<`, `>`, `&`, and `"`.
 */
val String.escapingSpecialXMLCharacters: String
    get() {
        val escaped = StringBuilder(length)

        this.forEach { char ->
            when (char) {
                '&' -> escaped.append("&amp;")
                '<' -> escaped.append("&lt;")
                '>' -> escaped.append("&gt;")
                '\"' -> escaped.append("&quot;")
                else -> escaped.append(char)
            }
        }

        return escaped.toString()
    }

val String.unescapingHTMLCharacters: String
    get() {
        return this
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }

val String.escapingSpecialHTMLCharacters: String
    get() = replace("&", "&amp;")
