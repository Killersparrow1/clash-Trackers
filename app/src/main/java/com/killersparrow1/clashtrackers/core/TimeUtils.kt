package com.killersparrow1.clashtrackers.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Formats a Unix epoch-second timestamp using the device locale and time zone. */
fun formatTimestamp(epochSec: Long): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .format(Instant.ofEpochSecond(epochSec).atZone(ZoneId.systemDefault()))
