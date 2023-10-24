/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.domain

import android.net.TrafficStats
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.net.Request
import com.datadog.android.api.net.RequestFactory
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale

// TODO: RUMM-2547 Drop this class and return a list of requests
//  instead from SessionReplayRequestFactory
// This class is not test as it is meant for non - production usage. It will be dropped later.
internal class SessionReplayOkHttpUploader(
    private val callFactory: Call.Factory
) {

    @Suppress("TooGenericExceptionCaught")
    fun upload(
        datadogContext: DatadogContext,
        request: Request
    ) {
        executeUploadRequest(request, datadogContext)
    }

    // region Internal

    // endregion

    private fun resolveUserAgent(datadogContext: DatadogContext): String {
        return sanitizeHeaderValue(System.getProperty(SYSTEM_UA))
            .ifBlank {
                "Datadog/$datadogContext " +
                    "(Linux; U; Android ${datadogContext.deviceInfo.osVersion}; " +
                    "${datadogContext.deviceInfo.deviceModel} " +
                    "Build/${datadogContext.deviceInfo.deviceBuildId})"
            }
    }

    // region Internal

    @Suppress("UnsafeThirdPartyFunctionCall") // Called within a try/catch block
    private fun executeUploadRequest(
        request: Request,
        datadogContext: DatadogContext
    ) {
        val apiKey = request.headers.entries
            .firstOrNull {
                it.key.equals(RequestFactory.HEADER_API_KEY, ignoreCase = true)
            }
            ?.value
        if (apiKey != null && (apiKey.isEmpty() || !isValidHeaderValue(apiKey))) {
            return
        }

        val okHttpRequest = buildOkHttpRequest(request, datadogContext)
        TrafficStats.setThreadStatsTag(Thread.currentThread().id.toInt())
        val call = callFactory.newCall(okHttpRequest)
        val response = call.execute()
        response.close()
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // Called within a try/catch block
    private fun buildOkHttpRequest(request: Request, datadogContext: DatadogContext): okhttp3.Request {
        val mediaType = if (request.contentType == null) {
            null
        } else {
            request.contentType!!.toMediaTypeOrNull()
        }
        val builder = okhttp3.Request.Builder()
            .url(request.url)
            .post(request.body.toRequestBody(mediaType))

        for ((header, value) in request.headers) {
            if (header.lowercase(Locale.US) == "user-agent") {
                continue
            }
            builder.addHeader(header, value)
        }

        builder.addHeader(HEADER_USER_AGENT, resolveUserAgent(datadogContext))

        return builder.build()
    }

    private fun sanitizeHeaderValue(value: String?): String {
        return value?.filter { isValidHeaderValueChar(it) }.orEmpty()
    }

    private fun isValidHeaderValue(value: String): Boolean {
        return value.all { isValidHeaderValueChar(it) }
    }

    private fun isValidHeaderValueChar(c: Char): Boolean {
        return c == '\t' || c in '\u0020' until '\u007F'
    }

    companion object {

        const val SYSTEM_UA = "http.agent"
        const val HEADER_USER_AGENT = "User-Agent"
    }
}
