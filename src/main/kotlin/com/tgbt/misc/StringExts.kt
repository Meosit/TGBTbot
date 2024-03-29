package com.tgbt.misc

import com.tgbt.bot.BotContext
import java.io.InputStreamReader
import java.net.URI
import java.net.URISyntaxException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * Returns a string trimmed to the specified [length] with optional [tail] string added at the end of the trimmed string.
 * The [tail] length must be less than the target [length] as the result is required to be no longer than this value.
 */
fun String.trimToLength(length: Int, tail: String = ""): String {
    require(length > tail.length) { "Tail '$tail' occupies the full length of $length chars of the new string" }
    return if (this.length <= length) this else this.take(length - tail.length) + tail
}

/**
 * Simple Markdown special chars escaping
 */
fun String.escapeMarkdown() = replace("*", "\\*")
    .replace("_", "\\_")
    .replace("`", "\\`")

/**
 * Checks that two strings has different set of letters (or in different order)
 */
infix fun String?.lettersDiffer(string: String?): Boolean =
    this?.filter(Char::isLetterOrDigit) != string?.filter(Char::isLetterOrDigit)


fun String.isImageUrl(): Boolean {
    try {
        val uri = URI(this)
        return uri.path.endsWith(".jpg", ignoreCase = true)
                || uri.path.endsWith(".jpeg", ignoreCase = true)
                || uri.path.endsWith(".png", ignoreCase = true)
    } catch (e: URISyntaxException) {
        return false
    }
}

fun Instant.simpleFormatTime(): String =
    DateTimeFormatter.ofPattern("HH:mm, EE").withLocale(Locale("ru"))
        .format(this.atZone(ZoneId.of("Europe/Moscow")))

fun loadResourceAsString(resourceBaseName: String): String = BotContext::class.java.classLoader
    .getResourceAsStream(resourceBaseName)
    .let { it ?: throw IllegalStateException("Null resource stream for $resourceBaseName") }
    .use { InputStreamReader(it).use(InputStreamReader::readText) }