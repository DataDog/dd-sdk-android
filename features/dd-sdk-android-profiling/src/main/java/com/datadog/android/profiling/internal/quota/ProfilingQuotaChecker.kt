/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal.quota

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.core.internal.utils.submitSafe
import okhttp3.Call
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicReference

internal class ProfilingQuotaChecker(
    private val callFactory: Call.Factory,
    private val executor: ExecutorService,
    private val internalLogger: InternalLogger,
    private val onResult: (QuotaResult) -> Unit = {}
) : QuotaChecker {

    private val pendingFuture = AtomicReference<Future<QuotaResult>?>()
    private val lastSessionId = AtomicReference<String?>(null)

    @Volatile
    override var lastResult: QuotaResult? = null
        private set

    override fun checkAsync(sessionId: String, datadogContext: DatadogContext) {
        val previousId = lastSessionId.getAndSet(sessionId)
        if (previousId == sessionId) return // same session: in-flight check (if any) is still valid
        pendingFuture.getAndSet(null)?.cancel(true)
        val future = executor.submitSafe(
            operationName = OPERATION_NAME_QUOTA,
            internalLogger = internalLogger,
            callable = {
                val result = performCheck(sessionId, datadogContext)
                if (lastSessionId.get() == sessionId) {
                    lastResult = result
                    onResult(result)
                }
                result
            }
        )
        if (future != null) {
            pendingFuture.set(future)
        } else {
            lastSessionId.compareAndSet(sessionId, previousId)
        }
    }

    override fun reset() {
        lastSessionId.set(null)
        lastResult = null
        pendingFuture.getAndSet(null)?.cancel(true)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun performCheck(sessionId: String, datadogContext: DatadogContext): QuotaResult {
        return try {
            val request = buildRequest(sessionId, datadogContext)
            callFactory.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    parseQuotaResult(response.body?.string())
                } else {
                    handleHttpError(response.code)
                }
            }
        } catch (e: IOException) {
            logErrorToMaintainer(e) { LOG_NETWORK_ERROR.format(Locale.US, e.message) }
            QuotaResult.API_ERROR
        } catch (e: Exception) {
            logErrorToMaintainer(e) { LOG_UNEXPECTED_ERROR.format(Locale.US, e.message) }
            QuotaResult.API_ERROR
        }
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // Wrapped in the try-catch
    private fun buildRequest(sessionId: String, datadogContext: DatadogContext): Request {
        val intakeHost = datadogContext.site.intakeEndpoint.removePrefix("https://")
        val url = QUOTA_URL_TEMPLATE.format(Locale.US, intakeHost, sessionId)
        return Request.Builder()
            .url(url)
            .addHeader(HEADER_CLIENT_TOKEN, datadogContext.clientToken)
            .addHeader(HEADER_ACCEPT, MEDIA_TYPE_JSON_API)
            .get()
            .build()
    }

    private fun parseQuotaResult(body: String?): QuotaResult {
        if (body == null) return QuotaResult.API_ERROR
        return try {
            @Suppress("UnsafeThirdPartyFunctionCall") // JSONObject operations wrapped in try-catch
            val attrs = JSONObject(body)
                .getJSONObject(JSON_KEY_DATA)
                .getJSONObject(JSON_KEY_ATTRIBUTES)
            val admitted = attrs.optBoolean(JSON_KEY_ADMITTED, true)
            val rawReason = attrs.optString(JSON_KEY_REASON)
            val reason = parseReason(rawReason)
            if (!admitted) {
                logInfoToUser { LOG_QUOTA_DENIED.format(Locale.US, reason.rawValue) }
                QuotaResult(QuotaResult.Decision.DENIED, reason)
            } else {
                QuotaResult(QuotaResult.Decision.ALLOWED, reason)
            }
        } catch (_: JSONException) {
            QuotaResult.API_ERROR
        }
    }

    private fun parseReason(raw: String?): QuotaReason = when (raw) {
        "quota_ok" -> QuotaReason.QUOTA_OK
        "quota_exceeded" -> QuotaReason.QUOTA_EXCEEDED
        "org_disabled" -> QuotaReason.ORG_DISABLED
        "backend_unavailable",
        "backend_client_not_initialized" -> QuotaReason.BACKEND_UNAVAILABLE

        else -> QuotaReason.UNDEFINED
    }

    private fun handleHttpError(code: Int): QuotaResult {
        return if (code == HTTP_TOO_MANY_REQUESTS) {
            logInfoToUser { LOG_HTTP_ERROR_TELEMETRY.format(Locale.US, code) }
            QuotaResult.QUOTA_EXCEEDED
        } else {
            logErrorToUser { LOG_HTTP_ERROR_TELEMETRY.format(Locale.US, code) }
            QuotaResult.API_ERROR
        }
    }

    private fun logErrorToMaintainer(throwable: Throwable? = null, messageBuilder: () -> String) {
        internalLogger.log(InternalLogger.Level.ERROR, InternalLogger.Target.MAINTAINER, messageBuilder, throwable)
    }

    private fun logErrorToUser(messageBuilder: () -> String) {
        internalLogger.log(InternalLogger.Level.ERROR, InternalLogger.Target.USER, messageBuilder)
    }

    private fun logInfoToUser(messageBuilder: () -> String) {
        internalLogger.log(InternalLogger.Level.INFO, InternalLogger.Target.USER, messageBuilder)
    }

    companion object {
        private const val OPERATION_NAME_QUOTA = "profiling-quota-check"

        internal const val LOG_HTTP_ERROR_TELEMETRY = "Profiling quota check returned HTTP %d"
        internal const val LOG_NETWORK_ERROR = "Quota check network error: %s"
        internal const val LOG_UNEXPECTED_ERROR = "Quota check unexpected error: %s"
        internal const val LOG_QUOTA_DENIED = "Profiling quota denied: reason=%s"

        internal const val QUOTA_URL_TEMPLATE = "https://quota.%s/api/v2/profiling/quota?session_id=%s"
        internal const val HEADER_CLIENT_TOKEN = "DD-CLIENT-TOKEN"
        internal const val HEADER_ACCEPT = "Accept"
        internal const val MEDIA_TYPE_JSON_API = "application/vnd.api+json"

        internal const val JSON_KEY_DATA = "data"
        internal const val JSON_KEY_ATTRIBUTES = "attributes"
        internal const val JSON_KEY_ADMITTED = "admitted"
        internal const val JSON_KEY_REASON = "reason"

        internal const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
