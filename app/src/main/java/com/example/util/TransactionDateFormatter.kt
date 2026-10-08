package com.example.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object TransactionDateFormatter {
    private val LAGOS_ZONE: ZoneId = ZoneId.of("Africa/Lagos")
    private val DATE_TIME_PATTERN: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm", Locale.ENGLISH)

    /**
     * Parses a UTC timestamp string (ISO-8601 / PostgreSQL timestamptz / timestamp) into epoch milliseconds.
     */
    fun parseUtcToEpochMillis(createdAt: String?): Long? {
        val raw = createdAt?.trim().orEmpty()
        if (raw.isEmpty() || raw.equals("null", ignoreCase = true)) return null
        return try {
            parseUtcToInstant(raw)?.toEpochMilli()
        } catch (_: Throwable) {
            null
        }
    }

    private fun parseUtcToInstant(raw: String): Instant? {
        var normalized = raw.trim()
        if (normalized.length >= 19 && normalized[10] == ' ') {
            normalized = normalized.substring(0, 10) + "T" + normalized.substring(11)
        }
        if (Regex(".*[+-]\\d{2}$").matches(normalized)) {
            normalized += ":00"
        } else if (Regex(".*[+-]\\d{4}$").matches(normalized)) {
            normalized = normalized.substring(0, normalized.length - 2) + ":" + normalized.substring(normalized.length - 2)
        }

        try {
            return OffsetDateTime.parse(normalized).toInstant()
        } catch (_: Throwable) {}

        try {
            return Instant.parse(normalized)
        } catch (_: Throwable) {}

        try {
            return LocalDateTime.parse(normalized).atZone(ZoneOffset.UTC).toInstant()
        } catch (_: Throwable) {}

        if (normalized.length >= 19) {
            try {
                val core = normalized.substring(0, 19)
                val ldt = LocalDateTime.parse(core, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.US))
                return ldt.atZone(ZoneOffset.UTC).toInstant()
            } catch (_: Throwable) {}
        }

        return null
    }

    /**
     * Converts a UTC `created_at` string (or fallback epoch millis) to Africa/Lagos time
     * and formats it like "08 Oct 2026, 03:00 pm".
     */
    fun formatUtcToLagos(createdAt: String?, fallbackEpochMillis: Long? = null): String {
        val raw = createdAt?.trim().orEmpty()
        if (raw.isNotEmpty() && !raw.equals("null", ignoreCase = true)) {
            val instant = parseUtcToInstant(raw)
            if (instant != null) {
                return formatInstantInLagos(instant)
            }
            return raw
        }
        if (fallbackEpochMillis != null && fallbackEpochMillis > 0L) {
            return formatInstantInLagos(Instant.ofEpochMilli(fallbackEpochMillis))
        }
        return ""
    }

    fun formatInstantInLagos(instant: Instant): String {
        val lagosZdt: ZonedDateTime = instant.atZone(LAGOS_ZONE)
        val dateAndTime = DATE_TIME_PATTERN.format(lagosZdt)
        val amPm = if (lagosZdt.hour < 12) "am" else "pm"
        return "$dateAndTime $amPm"
    }
}
