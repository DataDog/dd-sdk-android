/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.net

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.flags.ResponseSigningMode
import com.datadog.android.flags.internal.signing.ResponseSigningState
import com.datadog.android.flags.internal.signing.ResponseSigningVerifier
import com.datadog.android.flags.internal.signing.SignedAssignmentsResponse
import com.datadog.android.flags.model.EvaluationContext
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
 * @param signing optional signing verification state
 */
internal class PrecomputedAssignmentsDownloader(
    private val callFactory: Call.Factory,
    private val internalLogger: InternalLogger,
    private val requestFactory: PrecomputedAssignmentsRequestFactory,
    private val signing: ResponseSigningState? = null
) : PrecomputedAssignmentsReader {

    @WorkerThread
    override fun readPrecomputedFlags(context: EvaluationContext, datadogContext: DatadogContext): String? {
        val request = requestFactory.create(context, datadogContext) ?: return null

        return executeDownloadRequest(request)
    }

    @WorkerThread
    // Transport failures and proof failures have different fallback rules.
    @Suppress("ReturnCount", "CyclomaticComplexMethod")
    override fun readSignedPrecomputedFlags(
        context: EvaluationContext,
        datadogContext: DatadogContext,
        generation: Long,
        onPrepared: () -> Unit
    ): SignedAssignmentsResponse? {
        val state =
            signing ?: return readPrecomputedFlags(context, datadogContext)?.let { SignedAssignmentsResponse(it, null) }
        if (!state.isCurrent(generation)) return null
        val original = requestFactory.create(context, datadogContext) ?: return null
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
                    handleResponse(response)
                    return null
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
                SignedAssignmentsResponse(bytes.toString(Charsets.UTF_8), proof) { call.isCanceled() }
            }
        } catch (_: IllegalArgumentException) {
            failedSignedDownload()
        } catch (_: IllegalStateException) {
            failedSignedDownload()
        } catch (exception: IOException) {
            failedSignedDownload(exception)
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

    private fun failedSignedDownload(exception: IOException? = null): SignedAssignmentsResponse? {
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
    private fun executeDownloadRequest(request: Request): String? = try {
        val response = callFactory.newCall(request).execute()
        handleResponse(response)
    } catch (e: Throwable) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            { "Unexpected error while downloading flags" },
            e
        )
        null
    }

    private fun handleResponse(response: Response): String? = if (response.isSuccessful) {
        @Suppress("UnsafeThirdPartyFunctionCall") // Safe: wrapped in outer try-catch
        response.body?.use { it.string() }
    } else {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            { "Failed to download flags: ${response.code}" }
        )

        internalLogger.log(
            level = InternalLogger.Level.ERROR,
            target = InternalLogger.Target.TELEMETRY,
            messageBuilder = { "Flag assignment server returned error (${response.code})" },
            onlyOnce = true
        )

        @Suppress("UnsafeThirdPartyFunctionCall") // Safe: wrapped in outer try-catch
        response.body?.close()

        null
    }
}
