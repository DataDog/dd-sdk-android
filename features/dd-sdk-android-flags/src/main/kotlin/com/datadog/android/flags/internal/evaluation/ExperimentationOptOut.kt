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
    val enabled: Boolean = mode == ExperimentationOptOutMode.IMMEDIATE
    val stateLock = Any()
    private var currentRequest: Request? = null

    fun prepare(context: EvaluationContext): Request = synchronized(stateLock) {
        val snapshot = context.copy(attributes = context.attributes.toMap())
        val supplied = enabled && snapshot.attributes.containsKey(ATTRIBUTE)
        val invalid = supplied && !isValidTimestamp(snapshot.attributes[ATTRIBUTE])
        Request(snapshot, supplied && !invalid, invalid).also { currentRequest = it }
    }

    fun ifCurrent(request: Request, action: () -> Unit): Boolean = synchronized(stateLock) {
        if (enabled && currentRequest !== request) {
            false
        } else {
            action()
            true
        }
    }

    fun resolutionError(installedContext: EvaluationContext?): ErrorCode? {
        if (!enabled) return null
        return synchronized(stateLock) {
            val request = currentRequest
            when {
                request?.invalid == true -> ErrorCode.INVALID_CONTEXT
                request == null || request.context != installedContext -> ErrorCode.PROVIDER_NOT_READY
                else -> null
            }
        }
    }

    class Request(val context: EvaluationContext, val optedOut: Boolean, val invalid: Boolean)

    companion object {
        const val ATTRIBUTE = "dd_experimentation_opt_out_at"
        const val INVALID_CONTEXT_MESSAGE = "dd_experimentation_opt_out_at must be an RFC3339 timestamp string."
        private val TIMESTAMP =
            Regex("^([0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2})(?:\\.[0-9]+)?(Z|[+-][0-9]{2}:[0-9]{2})$")

        fun isValidTimestamp(value: String?): Boolean {
            val match = value?.let { TIMESTAMP.matchEntire(it.uppercase(Locale.US)) } ?: return false
            val zone = match.groupValues[2].replace("Z", "+0000").replace(":", "")
            val normalized = match.groupValues[1] + zone
            return try {
                val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).apply { isLenient = false }
                val position = ParsePosition(0)
                format.parse(normalized, position) != null && position.index == normalized.length
            } catch (_: IllegalArgumentException) {
                false
            } catch (_: NullPointerException) {
                false
            }
        }
    }
}
