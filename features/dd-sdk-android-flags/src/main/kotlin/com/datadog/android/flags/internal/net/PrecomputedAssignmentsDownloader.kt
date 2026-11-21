/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:OptIn(ExperimentalFlagsApi::class)

package com.datadog.android.flags.internal.net

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.flags.ExperimentalFlagsApi
import com.datadog.android.flags.ResponseSigningMode
import com.datadog.android.flags.internal.evaluation.AssignmentAuthorization
import com.datadog.android.flags.internal.model.AssignmentAuthorizationErrorResponse
import com.datadog.android.flags.internal.model.PrecomputedAssignmentsRequest.Diagnostics
import com.datadog.android.flags.internal.selection.FlagSelectionIgnoredReason
import com.datadog.android.flags.internal.signing.ResponseSigningState
import com.datadog.android.flags.internal.signing.ResponseSigningVerifier
import com.datadog.android.flags.model.EvaluationContext
import com.google.gson.JsonParseException
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * Downloads precomputed flag assignments from Datadog Feature Flags service.
 *
 * @param callFactory Factory for creating HTTP calls
 * @param internalLogger Logger for error and debug messages
 * @param requestFactory Factory for creating precomputed assignments requests
 * @param signing optional signing verification state; null for clients that did not opt in
 */
internal class PrecomputedAssignmentsDownloader(
    private val callFactory: Call.Factory,
    private val internalLogger: InternalLogger,
    private val requestFactory: PrecomputedAssignmentsRequestFactory,
    private val signing: ResponseSigningState? = null
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

    @WorkerThread
    // Transport failures and proof failures have different fallback rules.
    @Suppress("ReturnCount", "CyclomaticComplexMethod", "LongMethod")
    override fun readSignedPrecomputedFlags(
        context: EvaluationContext,
        datadogContext: DatadogContext,
        diagnostics: Diagnostics,
        assignmentToken: String?,
        generation: Long,
        onPrepared: () -> Unit
    ): PrecomputedAssignmentsResult? {
        val state = signing
            ?: return super.readSignedPrecomputedFlags(
                context,
                datadogContext,
                diagnostics,
                assignmentToken,
                generation,
                onPrepared
            )
        if (!state.isCurrent(generation)) return null
        val credentialUsed = assignmentToken != null
        val original = requestFactory.create(context, datadogContext, diagnostics, assignmentToken) ?: return null
        val prepared = state.prepare(original, generation)
        if (prepared == null && state.configuration.mode == ResponseSigningMode.REQUIRED) return null
        onPrepared()
        val request = prepared ?: original
        val transport =
            (callFactory as? OkHttpClient)?.newBuilder()?.followRedirects(false)?.followSslRedirects(false)?.build()
                ?: callFactory
        return try {
            val call = transport.newCall(request)
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    return handleResponse(response, credentialUsed)
                }
                if (redirected(response, request) || call.isCanceled()) return null
                val body = response.body ?: return null
                if (state.configuration.mode == ResponseSigningMode.REQUIRED &&
                    body.source().request(ResponseSigningVerifier.MAX_RESPONSE_BYTES.toLong() + 1)
                ) {
                    return null
                }
                val bytes = body.bytes()
                val proof = if (prepared != null &&
                    ResponseSigningVerifier.matchesRequest(prepared, response.request)
                ) {
                    state.verifier.verify(prepared, response.code, response.headers, bytes, state.now())
                } else {
                    null
                }
                state.report(proof != null)
                if (proof == null && state.configuration.mode == ResponseSigningMode.REQUIRED) return null
                SignedAssignmentsResponse(
                    bytes.toString(Charsets.UTF_8),
                    proof,
                    if (requestFactory.sendsFlagSelection) {
                        response.header(HEADER_FLAG_SELECTION_IGNORED)?.takeIf(String::isNotEmpty)
                            ?.let { if (credentialUsed) REDACTED_REASON else FlagSelectionIgnoredReason.sanitize(it) }
                    } else {
                        null
                    }
                ) { call.isCanceled() }
            }
        } catch (_: IllegalArgumentException) {
            failedSignedDownload(null)
        } catch (_: IllegalStateException) {
            failedSignedDownload(null)
        } catch (exception: IOException) {
            // Transports may include request headers in exception messages, causes, or custom fields.
            failedSignedDownload(if (credentialUsed) null else exception)
        }
    }

    private fun redirected(response: Response, request: Request): Boolean {
        var prior = response.priorResponse
        var redirected = response.request.url != request.url || response.request.method != request.method
        while (prior != null) {
            redirected = redirected || prior.isRedirect
            prior = prior.priorResponse
        }
        return redirected
    }

    private fun failedSignedDownload(exception: IOException?): PrecomputedAssignmentsResult? {
        internalLogger.log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.MAINTAINER,
            { "Unable to download signed flag assignments" },
            exception,
            onlyOnce = true
        )
        return null
    }

    @Suppress("TooGenericExceptionCaught")
    private fun executeDownloadRequest(request: Request, credentialUsed: Boolean): PrecomputedAssignmentsResult? = try {
        val response = callFactory.newCall(request).execute()
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

    private fun isAuthorizationRejection(body: String): Boolean = try {
        AssignmentAuthorizationErrorResponse.fromJson(body).errors.any {
            it.code == AssignmentAuthorization.AUTHORIZATION_FAILED
        }
    } catch (_: JsonParseException) {
        false
    }

    private companion object {
        const val HEADER_FLAG_SELECTION_IGNORED = "DD-Flag-Selection-Ignored"
        const val HTTP_UNAUTHORIZED = 401
        const val MAX_ERROR_BODY_BYTES = 64L * 1024L
        const val REDACTED_REASON = "redacted"
    }
}
