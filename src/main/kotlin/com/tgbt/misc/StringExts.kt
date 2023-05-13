package com.tgbt.misc

import com.tgbt.settings.Setting
import java.io.InputStreamReader
import java.net.URI
import java.net.URISyntaxException

/**
 * Returns a string trimmed to the specified [length] with optional [tail] string added at the end of the trimmed string.
 * The [tail] length must be less than the target [length] as the result is required to be no longer than this value.
 */
fun String.trimToLength(length: Int, tail: String = ""): String {
    require(length > tail.length) { "Tail '$tail' occupies the full length of $length chars of the new string" }
    return if (this.length <= length) this else this.take(length - tail.length) + tail
}

fun String.teaserString(length: Int = 20): String = trimToLength(length, "…").replace('\n', ' ')

/**
 * Simple Markdown special chars escaping
 */
fun String.escapeMarkdown() = replace("*", "\\*")
    .replace("_", "\\_")
    .replace("`", "\\`")

fun String.isImageUrl() = try {
    val uri = URI(this)
    (uri.host != null && (uri.path.endsWith(".jpg", ignoreCase = true)
            || uri.path.endsWith(".jpeg", ignoreCase = true)
            || uri.path.endsWith(".png", ignoreCase = true))
            && (uri.scheme == "https" || uri.scheme == "http"))
} catch (e: URISyntaxException) {
    false
}

fun loadResourceAsString(resourceBaseName: String): String = Setting::class.java.classLoader
    .getResourceAsStream(resourceBaseName)
    .let { it ?: throw IllegalStateException("Null resource stream for $resourceBaseName") }
    .use { InputStreamReader(it).use(InputStreamReader::readText) }