package com.tgbt.misc

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*


val moscowZoneId: ZoneId = ZoneId.of("Europe/Moscow")

fun Instant.simpleFormatTime(): String =
    DateTimeFormatter.ofPattern("HH:mm, EE").withLocale(Locale("ru"))
        .format(this.atZone(moscowZoneId))

fun LocalTime.simpleFormatTime(): String =
    DateTimeFormatter.ofPattern("HH:mm").withLocale(Locale("ru"))
        .format(this)


fun zonedNow(): ZonedDateTime = Instant.now().atZone(moscowZoneId)

fun Long.asZonedTime(): ZonedDateTime = Instant.ofEpochSecond(this).atZone(moscowZoneId)

fun LocalTime?.inLocalRange(start: LocalTime, end: LocalTime): Boolean {
    if (this == null) {
        return false
    }
    val range = if (end >= start)
        start..end else end..start
    return if (end >= start) this in range else this !in range
}