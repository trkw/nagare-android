package dev.trkw.nagare.core

object HtmlText {
    private val entities = listOf("&nbsp;" to " ", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&#39;" to "'", "&amp;" to "&")

    /** Plain text of an HTML fragment, for speech and previews. */
    fun plain(html: String): String {
        var s = html
            .replace(Regex("<(script|style)[^>]*>[\\s\\S]*?</\\1>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<br\\s*/?>|</(p|div|li|h[1-6]|blockquote)>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), " ")
        for ((k, v) in entities) s = s.replace(k, v)
        return s.replace(Regex("[ \\t]+"), " ").replace(Regex("\\s*\\n\\s*"), "\n").trim()
    }
}
