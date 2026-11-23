/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:OptIn(ExperimentalFlagsApi::class)

package com.datadog.android.flags.internal.net

import androidx.annotation.WorkerThread
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.flags.ExperimentalFlagsApi
import com.datadog.android.flags.ResponseSigningFailureReason
import com.datadog.android.flags.internal.model.PrecomputedAssignmentsRequest.Diagnostics
import com.datadog.android.flags.internal.signing.VerifiedResponse
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.tools.annotation.NoOpImplementation

@NoOpImplementation
internal interface PrecomputedAssignmentsReader {
    @WorkerThread
    fun readPrecomputedFlags(
        context: EvaluationContext,
        datadogContext: DatadogContext,
        diagnostics: Diagnostics
    ): PrecomputedAssignmentsResult?

    @WorkerThread
    fun readPrecomputedFlags(
        context: EvaluationContext,
        datadogContext: DatadogContext,
        diagnostics: Diagnostics,
        assignmentToken: String
    ): PrecomputedAssignmentsResult?

    /**
     * Reads assignments for an opted-in signing client. A successful read returns [SignedAssignmentsResponse].
     * [assignmentToken] is the subject-authorization credential, or null.
     */
    @WorkerThread
    fun readSignedPrecomputedFlags(
        context: EvaluationContext,
        datadogContext: DatadogContext,
        diagnostics: Diagnostics,
        assignmentToken: String?,
        generation: Long,
        onPrepared: () -> Unit = {}
    ): PrecomputedAssignmentsResult? {
        val result = if (assignmentToken == null) {
            readPrecomputedFlags(context, datadogContext, diagnostics)
        } else {
            readPrecomputedFlags(context, datadogContext, diagnostics, assignmentToken)
        }
        return if (result is PrecomputedAssignmentsResponse) {
            SignedAssignmentsResponse(result.body, null, result.flagSelectionIgnoredReason)
        } else {
            result
        }
    }
}

internal sealed interface PrecomputedAssignmentsResult {
    data object AuthorizationRejected : PrecomputedAssignmentsResult

    /** Required signing rejected the response, or could not prepare the request. */
    data class SigningRejected(val reason: ResponseSigningFailureReason) : PrecomputedAssignmentsResult
}

/**
 * [flagSelectionIgnoredReason] is the sanitized `DD-Flag-Selection-Ignored` reason, or null when the header is absent.
 */
internal data class PrecomputedAssignmentsResponse(
    val body: String,
    val flagSelectionIgnoredReason: String?
) : PrecomputedAssignmentsResult

/**
 * A response read by an opted-in signing client. [proof] is null when the response is not verified.
 */
internal data class SignedAssignmentsResponse(
    val json: String,
    val proof: VerifiedResponse?,
    val flagSelectionIgnoredReason: String? = null,
    val isCancelled: () -> Boolean = { false }
) : PrecomputedAssignmentsResult
