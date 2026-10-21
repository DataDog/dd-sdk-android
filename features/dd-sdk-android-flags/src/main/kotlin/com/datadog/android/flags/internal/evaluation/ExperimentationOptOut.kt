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
import java.util.Locale

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
        const val INVALID_CONTEXT_MESSAGE = "dd_experimentation_opt_out_at must be an RFC3339 timestamp string."
        private val TIMESTAMP =
            Regex("^([0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2})(?:\\.([0-9]+))?(Z|[+-][0-9]{2}:[0-9]{2})$")

        fun isValidTimestamp(value: String?): Boolean = parseTimestamp(value) != null

        fun timestampsMatch(expected: String?, actual: String?): Boolean {
            val timestamp = parseTimestamp(expected) ?: return false
            return timestamp == parseTimestamp(actual)
        }

        private fun parseTimestamp(value: String?): Timestamp? {
            val match = value?.let { TIMESTAMP.matchEntire(it.uppercase(Locale.US)) } ?: return null
            val zone = match.groupValues[TIMEZONE_GROUP].replace("Z", "+0000").replace(":", "")
            val normalized = match.groupValues[1] + zone
            return try {
                val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).apply { isLenient = false }
                val position = ParsePosition(0)
                val date = format.parse(normalized, position)
                if (date != null && position.index == normalized.length) {
                    Timestamp(date.time, match.groupValues[2].take(NANOSECOND_DIGITS).trimEnd('0'))
                } else {
                    null
                }
            } catch (_: IllegalArgumentException) {
                null
            } catch (_: NullPointerException) {
                null
            }
        }

        private const val NANOSECOND_DIGITS = 9
        private const val TIMEZONE_GROUP = 3
    }
}

internal val ExperimentationOptOutMode.wireValue: String
    get() = when (this) {
        ExperimentationOptOutMode.DISABLED -> "disabled"
        ExperimentationOptOutMode.IMMEDIATE -> "immediate"
        ExperimentationOptOutMode.FUTURE_EXPERIMENTS_ONLY -> "future_experiments_only"
    }
