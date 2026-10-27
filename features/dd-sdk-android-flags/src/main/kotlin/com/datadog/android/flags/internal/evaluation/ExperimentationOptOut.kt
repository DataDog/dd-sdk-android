/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.evaluation

import com.datadog.android.flags.ExperimentationOptOutMode
import com.datadog.android.flags.model.ErrorCode
import com.datadog.android.flags.model.EvaluationContext
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** Keeps the requested context separate from the last accepted assignments. */
internal class ExperimentationOptOut(val mode: ExperimentationOptOutMode = ExperimentationOptOutMode.DISABLED) {
    val enabled: Boolean = mode != ExperimentationOptOutMode.DISABLED
    val stateLock = Any()
    private var currentRequest: Request? = null

    fun prepare(context: EvaluationContext): Request = synchronized(stateLock) {
        val snapshot = context.copy(attributes = context.attributes.toMap())
        val supplied = enabled && snapshot.attributes.containsKey(ATTRIBUTE)
        val invalid = supplied && !isValidTimestamp(snapshot.attributes[ATTRIBUTE])
        Request(snapshot, supplied && !invalid, invalid, mode).also { currentRequest = it }
    }

    fun ifCurrent(request: Request, action: () -> Unit): Boolean = synchronized(stateLock) {
        if (enabled && currentRequest !== request) {
            false
        } else {
            action()
            true
        }
    }

    fun resolutionError(installedContext: EvaluationContext?): ErrorCode? =
        resolutionContext(installedContext).errorCode

    fun resolutionContext(installedContext: EvaluationContext?): ResolutionContext {
        if (!enabled) return ResolutionContext(installedContext ?: EvaluationContext.EMPTY, null)
        return synchronized(stateLock) {
            val request = currentRequest
            val errorCode = when {
                request?.invalid == true -> ErrorCode.INVALID_CONTEXT
                request == null || request.context != installedContext -> ErrorCode.PROVIDER_NOT_READY
                else -> null
            }
            ResolutionContext(request?.context ?: EvaluationContext.EMPTY, errorCode)
        }
    }

    data class ResolutionContext(val context: EvaluationContext, val errorCode: ErrorCode?)

    class Request(
        val context: EvaluationContext,
        val optedOut: Boolean,
        val invalid: Boolean,
        val mode: ExperimentationOptOutMode
    )

    private data class Timestamp(val wholeSecondMillis: Long, val fraction: String)

    companion object {
        const val ATTRIBUTE = "dd_experimentation_opt_out_at"
        const val INVALID_CONTEXT_MESSAGE =
            "Set dd_experimentation_opt_out_at to YYYY-MM-DDTHH:mm:ss[.fraction] with Z or +/-HH:mm, or omit the attribute."
        private val TIMESTAMP =
            Regex("^([0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2})(?:\\.([0-9]+))?(Z|[+-][0-9]{2}:[0-9]{2})$")

        fun isValidTimestamp(value: String?): Boolean = parseTimestamp(value) != null

        fun timestampsMatch(expected: String?, actual: String?): Boolean {
            val timestamp = parseTimestamp(expected) ?: return false
            return timestamp == parseTimestamp(actual)
        }

        @Suppress("ComplexCondition") // Admission requires a complete parse and both UTC bounds.
        private fun parseTimestamp(value: String?): Timestamp? {
            val match = value?.let { TIMESTAMP.matchEntire(it.uppercase(Locale.US)) } ?: return null
            val offsetMinutes = parseOffsetMinutes(match.groupValues[TIMEZONE_GROUP])
            val dateTime = match.groupValues[1]

            // Calendar numbers years within an era: astronomical year zero is 1 BC.
            @Suppress("UnsafeThirdPartyFunctionCall") // Fixed non-empty replacement on a non-null string.
            val normalized = if (dateTime.startsWith("0000")) {
                "BC ${dateTime.replaceFirst("0000", "0001")}"
            } else {
                "AD $dateTime"
            }
            return if (offsetMinutes == null) {
                null
            } else {
                try {
                    @Suppress("UnsafeThirdPartyFunctionCall") // Non-null arguments; exceptions caught below.
                    val format = SimpleDateFormat("G yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                        calendar = GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.US).apply {
                            // Use the Gregorian rules before 1582 too, without a historical cutover.
                            gregorianChange = Date(Long.MIN_VALUE)
                        }
                        isLenient = false
                    }
                    val position = ParsePosition(0)
                    val date = format.parse(normalized, position)

                    // Apply the numeric offset explicitly; Calendar limits its zone-offset field more narrowly.
                    @Suppress("UnsafeThirdPartyFunctionCall") // Non-throwing subtraction of bounded values.
                    val utcMillis = date?.time?.minus(TimeUnit.MINUTES.toMillis(offsetMinutes.toLong()))
                    if (utcMillis != null && position.index == normalized.length &&
                        utcMillis >= MIN_TIMESTAMP_MILLIS && utcMillis < MAX_TIMESTAMP_MILLIS_EXCLUSIVE
                    ) {
                        Timestamp(utcMillis, match.groupValues[2].take(NANOSECOND_DIGITS).trimEnd('0'))
                    } else {
                        null
                    }
                } catch (_: IllegalArgumentException) {
                    null
                } catch (_: NullPointerException) {
                    null
                }
            }
        }

        private fun parseOffsetMinutes(zone: String): Int? {
            if (zone == "Z") return 0
            @Suppress("UnsafeThirdPartyFunctionCall") // TIMESTAMP guarantees a sign and two-digit fields.
            val fields = zone.drop(1).split(':').map { it.toInt() }
            val (hours, minutes) = fields
            return if (hours > MAX_OFFSET_HOURS || minutes >= MINUTES_PER_HOUR) {
                null
            } else {
                (hours * MINUTES_PER_HOUR + minutes) * if (zone[0] == '-') -1 else 1
            }
        }

        private const val NANOSECOND_DIGITS = 9
        private const val TIMEZONE_GROUP = 3
        private const val MAX_OFFSET_HOURS = 23
        private const val MINUTES_PER_HOUR = 60
        private const val MIN_TIMESTAMP_MILLIS = -62167219200000L // 0000-01-01T00:00:00Z
        private const val MAX_TIMESTAMP_MILLIS_EXCLUSIVE = 253402300800000L // 10000-01-01T00:00:00Z
    }
}

internal val ExperimentationOptOutMode.wireValue: String
    get() = when (this) {
        ExperimentationOptOutMode.DISABLED -> "disabled"
        ExperimentationOptOutMode.IMMEDIATE -> "immediate"
        ExperimentationOptOutMode.FUTURE_EXPERIMENTS_ONLY -> "future_experiments_only"
    }
