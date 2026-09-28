package com.example.synced.feature.content.data.mapper

// Pure-Kotlin cleanup of Dev.to payloads (HTML comments, markdown bodies) —
// identical behavior on device and in plain JVM unit tests (no android.text).

private val scriptOrStyle =
    Regex("(?is)<(script|style)[^>]*>.*?</(script|style)>")
private val blockEnd =
    Regex("(?i)</(p|div|li|h[1-6]|blockquote|pre|tr|table|section|article|figcaption)>")
private val lineBreak = Regex("(?i)<br\\s*/?>")
private val listItemStart = Regex("(?i)<li[^>]*>")
private val anyTag = Regex("<[^>]*>")
private val numericEntity = Regex("&#(\\d+);")
private val hexEntity = Regex("&#[xX]([0-9a-fA-F]+);")

private val namedEntities = mapOf(
    "lt" to "<",
    "gt" to ">",
    "quot" to "\"",
    "apos" to "'",
    "nbsp" to " ",
    "hellip" to "\u2026",
    "mdash" to "\u2014",
    "ndash" to "\u2013",
    "lsquo" to "\u2018",
    "rsquo" to "\u2019",
    "ldquo" to "\u201c",
    "rdquo" to "\u201d",
    "copy" to "\u00a9",
    "trade" to "\u2122",
)

/** Dev.to comment HTML → readable plain text with paragraph breaks. */
internal fun htmlToPlain(html: String): String {
    val text = html
        .replace(scriptOrStyle, "")
        .replace(blockEnd, "\n")
        .replace(lineBreak, "\n")
        .replace(listItemStart, "")
        .replace(anyTag, "")
    return decodeEntities(text)
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}

/** Dev.to `body_markdown` → readable plain text (headings, links, emphasis). */
internal fun markdownToPlain(markdown: String): String {
    val text = markdown
        .replace(Regex("(?m)^```.*$"), "") // fence markers; inner content stays
        .replace(Regex("!\\[[^\\]]*]\\([^)]*\\)"), "") // images
        .replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1") // links → label
        .replace(Regex("(?m)^#{1,6}\\s*"), "") // heading hashes
        .replace(Regex("(?m)^>\\s?"), "") // quote markers
        .replace("**", "")
        .replace("__", "")
        .replace(Regex("`([^`]+)`"), "$1")
        .replace(Regex("(?m)^[ \\t]*[-*+] "), "- ") // bullets
        .replace(Regex("\\*([^*\\n]+)\\*"), "$1")
    return decodeEntities(text)
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}

private fun decodeEntities(text: String): String {
    var out = text
    out = numericEntity.replace(out) { m ->
        m.groupValues[1].toIntOrNull()?.codePointToString() ?: m.value
    }
    out = hexEntity.replace(out) { m ->
        m.groupValues[1].toIntOrNull(16)?.codePointToString() ?: m.value
    }
    for ((name, value) in namedEntities) {
        out = out.replace("&$name;", value)
    }
    // &amp; last so "&amp;lt;" decodes to the text "&lt;" instead of "<".
    return out.replace("&amp;", "&")
}

private fun Int.codePointToString(): String =
    if (this in 1..0x10FFFF) String(Character.toChars(this)) else ""
