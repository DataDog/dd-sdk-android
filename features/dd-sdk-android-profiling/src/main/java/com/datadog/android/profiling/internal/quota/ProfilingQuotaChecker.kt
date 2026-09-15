/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal.quota

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

internal class ProfilingQuotaChecker(
    private val callFactory: Call.Factory,
    private val internalLogger: InternalLogger,
    private val onResult: (QuotaResult) -> Unit = {}
) : QuotaChecker {

    private val pendingCall = AtomicReference<Call?>()
    private val lastSessionId = AtomicReference<String?>(null)

    @Volatile
    override var lastResult: QuotaResult? = null
        private set

    @Suppress("TooGenericExceptionCaught")
    override fun checkAsync(sessionId: String, datadogContext: DatadogContext) {
        val previousId = lastSessionId.getAndSet(sessionId)
        if (previousId == sessionId) return // same session: in-flight check (if any) is still valid
        @Suppress("UnsafeThirdPartyFunctionCall") // operates on our own fields, and Call#cancel() never throws
        pendingCall.getAndSet(null)?.cancel()
        val call = try {
            @Suppress("UnsafeThirdPartyFunctionCall") // wrapped in this try-catch
            callFactory.newCall(buildRequest(sessionId, datadogContext))
        } catch (e: Exception) {
            logErrorToMaintainer(e) { LOG_UNEXPECTED_ERROR.format(Locale.US, e.message) }
            lastSessionId.compareAndSet(sessionId, previousId)
            return
        }
        @Suppress("UnsafeThirdPartyFunctionCall") // operates on our own field, never throws
        pendingCall.set(call)
        // The 5s budget comes from callTimeout() on the OkHttp client, so no executor of our own
        // is needed: OkHttp's dispatcher is already a shared, zero-idle, on-demand thread pool.
        @Suppress("UnsafeThirdPartyFunctionCall") // callback bodies never throw
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // OkHttp surfaces a call timeout by cancelling the call, so a cancelled call is
                // only superfluous when it is no longer the in-flight call (superseded by a newer
                // session or cleared by reset()). A pending timeout must still produce a decision,
                // exactly as the old blocking execute() threw IOException and mapped to API_ERROR.
                if (call.isCanceled() && pendingCall.get() !== call) return
                logErrorToMaintainer(e) { LOG_NETWORK_ERROR.format(Locale.US, e.message) }
                deliver(call, sessionId, QuotaResult.API_ERROR)
            }

            override fun onResponse(call: Call, response: Response) {
                deliver(call, sessionId, readResult(response))
            }
        })
    }

    override fun reset() {
        lastSessionId.set(null)
        lastResult = null
        @Suppress("UnsafeThirdPartyFunctionCall") // operates on our own fields, and Call#cancel() never throws
        pendingCall.getAndSet(null)?.cancel()
    }

    @Suppress("TooGenericExceptionCaught")
    private fun readResult(response: Response): QuotaResult {
        return try {
            @Suppress("UnsafeThirdPartyFunctionCall") // wrapped in this try-catch
            response.use {
                if (it.isSuccessful) {
                    parseQuotaResult(it.body?.string())
                } else {
                    handleHttpError(it.code)
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

    private fun deliver(call: Call, sessionId: String, result: QuotaResult) {
        // compareAndSet, not set: a newer session's call must not be cleared by a late callback.
        @Suppress("UnsafeThirdPartyFunctionCall") // operates on our own field, never throws
        pendingCall.compareAndSet(call, null)
        if (lastSessionId.get() == sessionId) {
            lastResult = result
            onResult(result)
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
