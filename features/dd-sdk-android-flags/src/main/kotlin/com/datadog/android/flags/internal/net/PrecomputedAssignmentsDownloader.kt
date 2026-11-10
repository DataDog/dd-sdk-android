/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.net

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.flags.internal.evaluation.AssignmentAuthorization
import com.datadog.android.flags.internal.model.PrecomputedAssignmentsRequest.Diagnostics
import com.datadog.android.flags.internal.selection.FlagSelectionIgnoredReason
import com.datadog.android.flags.model.EvaluationContext
import okhttp3.Call
import okhttp3.Request
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject

/**
 * Downloads precomputed flag assignments from Datadog Feature Flags service.
 *
 * @param callFactory Factory for creating ordinary HTTP calls
 * @param assignmentAuthorizationCallFactory Factory for credentialed calls, which must not follow redirects
 * @param internalLogger Logger for error and debug messages
 * @param requestFactory Factory for creating precomputed assignments requests
 */
internal class PrecomputedAssignmentsDownloader(
    private val callFactory: Call.Factory,
    private val assignmentAuthorizationCallFactory: Call.Factory,
    private val internalLogger: InternalLogger,
    private val requestFactory: PrecomputedAssignmentsRequestFactory
) : PrecomputedAssignmentsReader {

    @WorkerThread
    override fun readPrecomputedFlags(
        context: EvaluationContext,
        datadogContext: DatadogContext,
        diagnostics: Diagnostics
    ): PrecomputedAssignmentsResult? = readPrecomputedFlagsInternal(context, datadogContext, diagnostics, null)

    @WorkerThread
    override fun readPrecomputedFlags(
        context: EvaluationContext,
        datadogContext: DatadogContext,
        diagnostics: Diagnostics,
        assignmentToken: String
    ): PrecomputedAssignmentsResult? =
        readPrecomputedFlagsInternal(context, datadogContext, diagnostics, assignmentToken)

    private fun readPrecomputedFlagsInternal(
        context: EvaluationContext,
        datadogContext: DatadogContext,
        diagnostics: Diagnostics,
        assignmentToken: String?
    ): PrecomputedAssignmentsResult? {
        val request = requestFactory.create(context, datadogContext, diagnostics, assignmentToken) ?: return null
        return executeDownloadRequest(request, assignmentToken != null)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun executeDownloadRequest(request: Request, credentialUsed: Boolean): PrecomputedAssignmentsResult? = try {
        val selectedCallFactory = if (credentialUsed) assignmentAuthorizationCallFactory else callFactory
        val response = selectedCallFactory.newCall(request).execute()
        handleResponse(response, credentialUsed)
    } catch (e: Throwable) {
        if (credentialUsed) {
            // Transports may include request headers in exception messages, causes, or custom fields.
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                { "Unexpected error while downloading flags with assignment authorization" }
            )
        } else {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                { "Unexpected error while downloading flags" },
                e
            )
        }
        null
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // Bounded OkHttp and JSON reads are handled as ordinary request failures.
    private fun handleResponse(
        response: Response,
        credentialUsed: Boolean
    ): PrecomputedAssignmentsResult? = if (response.isSuccessful) {
        @Suppress("UnsafeThirdPartyFunctionCall") // Safe: wrapped in outer try-catch
        response.body?.use { it.string() }?.let { body ->
            PrecomputedAssignmentsResponse(
                body = body,
                flagSelectionIgnoredReason = if (requestFactory.sendsFlagSelection) {
                    response.header(HEADER_FLAG_SELECTION_IGNORED)
                        ?.takeIf(String::isNotEmpty)
                        ?.let { if (credentialUsed) REDACTED_REASON else FlagSelectionIgnoredReason.sanitize(it) }
                } else {
                    null
                }
            )
        }
    } else {
        val authorizationRejected = response.body?.use {
            if (response.code == HTTP_UNAUTHORIZED) {
                @Suppress("UnsafeThirdPartyFunctionCall") // Bounded read; malformed JSON is an ordinary failure.
                isAuthorizationRejection(response.peekBody(MAX_ERROR_BODY_BYTES).string())
            } else {
                false
            }
        } ?: false
        internalLogger.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            {
                if (authorizationRejected) {
                    "Assignment download failed: ${AssignmentAuthorization.AUTHORIZATION_FAILED}"
                } else {
                    "Failed to download flags: ${response.code}"
                }
            }
        )

        internalLogger.log(
            level = InternalLogger.Level.ERROR,
            target = InternalLogger.Target.TELEMETRY,
            messageBuilder = { "Flag assignment server returned error (${response.code})" },
            onlyOnce = true
        )

        if (authorizationRejected) PrecomputedAssignmentsResult.AuthorizationRejected else null
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // Bounded JSON parsing is contained by the JSONException fallback.
    private fun isAuthorizationRejection(body: String): Boolean = try {
        val errors = JSONObject(body).optJSONArray("errors") ?: return false
        (0 until errors.length()).any { index ->
            errors.optJSONObject(index)?.optString("code") == AssignmentAuthorization.AUTHORIZATION_FAILED
        }
    } catch (_: JSONException) {
        false
    }

    private companion object {
        const val HEADER_FLAG_SELECTION_IGNORED = "DD-Flag-Selection-Ignored"
        const val HTTP_UNAUTHORIZED = 401
        const val MAX_ERROR_BODY_BYTES = 64L * 1024L
        const val REDACTED_REASON = "redacted"
    }
}
