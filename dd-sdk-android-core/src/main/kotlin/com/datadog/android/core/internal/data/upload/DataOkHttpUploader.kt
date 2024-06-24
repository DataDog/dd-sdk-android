/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.upload

import android.net.TrafficStats
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.net.RequestFactory
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.core.ConnectionPoolInfo
import com.datadog.android.core.internal.system.AndroidInfoProvider
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.UnknownHostException
import java.util.Locale
import com.datadog.android.api.net.Request as DatadogRequest

internal class DataOkHttpUploader(
    val requestFactory: RequestFactory,
    val internalLogger: InternalLogger,
    val callFactory: Call.Factory,
    val sdkVersion: String,
    val androidInfoProvider: AndroidInfoProvider
) : DataUploader {

    // region DataUploader

    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    override fun upload(
        context: DatadogContext,
        batch: List<RawBatchEvent>,
        batchMeta: ByteArray?
    ): UploadStatus {
        val request = try {
            requestFactory.create(context, batch, batchMeta)
                ?: return UploadStatus.RequestCreationError(null)
        } catch (e: Exception) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
                {
                    "Unable to create the request, probably due to bad data format." +
                        " The batch will be dropped."
                },
                e
            )
            return UploadStatus.RequestCreationError(e)
        }

        val uploadStatus = try {
            executeUploadRequest(request)
        } catch (e: UnknownHostException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                { "Unable to find host for site ${context.site}; we will retry later." },
                e
            )
            UploadStatus.DNSError(e)
        } catch (e: IOException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                { "Unable to execute the request; we will retry later." },
                e
            )
            UploadStatus.NetworkError(e)
        } catch (e: Throwable) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                { "Unable to execute the request; we will retry later." },
                e
            )
            UploadStatus.UnknownException(throwable = e)
        }

        uploadStatus.logStatus(
            request.description,
            request.body.size,
            internalLogger,
            requestId = request.id
        )

        return uploadStatus
    }

    // endregion

    private val userAgent by lazy {
        sanitizeHeaderValue(System.getProperty(SYSTEM_UA))
            .ifBlank {
                "Datadog/$sdkVersion " +
                    "(Linux; U; Android ${androidInfoProvider.osVersion}; " +
                    "${androidInfoProvider.deviceModel} " +
                    "Build/${androidInfoProvider.deviceBuildId})"
            }
    }

    // region Internal

    @Suppress("UnsafeThirdPartyFunctionCall") // Called within a try/catch block
    private fun executeUploadRequest(
        request: DatadogRequest
    ): UploadStatus {
        val apiKey = request.headers.entries
            .firstOrNull {
                it.key.equals(RequestFactory.HEADER_API_KEY, ignoreCase = true)
            }
            ?.value
        if (apiKey != null && (apiKey.isEmpty() || !isValidHeaderValue(apiKey))) {
            return UploadStatus.InvalidTokenError(UploadStatus.UNKNOWN_RESPONSE_CODE)
        }

        val okHttpRequest = buildOkHttpRequest(request)
        TrafficStats.setThreadStatsTag(Thread.currentThread().id.toInt())
        val call = callFactory.newCall(okHttpRequest)
        val response = call.execute()
        response.close()
        return responseCodeToUploadStatus(response.code, request)
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // Called within a try/catch block
    private fun buildOkHttpRequest(request: DatadogRequest): Request {
        val mediaType = if (request.contentType == null) {
            null
        } else {
            request.contentType.toMediaTypeOrNull()
        }
        val builder = Request.Builder()
            .url(request.url)
            .post(request.body.toRequestBody(mediaType))

        for ((header, value) in request.headers) {
            if (header.lowercase(Locale.US) == "user-agent") {
                internalLogger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.MAINTAINER,
                    { WARNING_USER_AGENT_HEADER_RESERVED }
                )
                continue
            }
            builder.addHeader(header, value)
        }

        builder.addHeader(HEADER_USER_AGENT, userAgent)

        if (callFactory is OkHttpClient) {
            val stats = ConnectionPoolInfo(
                callFactory.connectionPool.connectionCount(),
                callFactory.connectionPool.idleConnectionCount()
            )

            builder.tag(ConnectionPoolInfo::class.java, stats)
        }

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

    private fun responseCodeToUploadStatus(
        code: Int,
        request: DatadogRequest
    ): UploadStatus {
        return when (code) {
            HTTP_ACCEPTED -> UploadStatus.Success(code)
            HTTP_BAD_REQUEST -> UploadStatus.HttpClientError(code)
            HTTP_UNAUTHORIZED -> UploadStatus.InvalidTokenError(code)
            HTTP_FORBIDDEN -> UploadStatus.InvalidTokenError(code)
            HTTP_CLIENT_TIMEOUT -> UploadStatus.HttpClientRateLimiting(code)
            HTTP_ENTITY_TOO_LARGE -> UploadStatus.HttpClientError(code)
            HTTP_TOO_MANY_REQUESTS -> UploadStatus.HttpClientRateLimiting(code)
            HTTP_INTERNAL_ERROR,
            HTTP_BAD_GATEWAY,
            HTTP_UNAVAILABLE,
            HTTP_GATEWAY_TIMEOUT,
            HTTP_INSUFFICIENT_STORAGE -> UploadStatus.HttpServerError(code)

            else -> {
                internalLogger.log(
                    InternalLogger.Level.WARN,
                    listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                    { "Unexpected status code $code on upload request: ${request.description}" }
                )
                UploadStatus.UnknownHttpError(code)
            }
        }
    }

    companion object {

        const val HTTP_ACCEPTED = 202

        const val HTTP_BAD_REQUEST = 400
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_CLIENT_TIMEOUT = 408
        const val HTTP_ENTITY_TOO_LARGE = 413
        const val HTTP_TOO_MANY_REQUESTS = 429

        const val HTTP_INTERNAL_ERROR = 500
        const val HTTP_BAD_GATEWAY = 502
        const val HTTP_UNAVAILABLE = 503
        const val HTTP_GATEWAY_TIMEOUT = 504
        const val HTTP_INSUFFICIENT_STORAGE = 507

        const val SYSTEM_UA = "http.agent"

        const val WARNING_USER_AGENT_HEADER_RESERVED =
            "Ignoring provided User-Agent header, because it is reserved."
        const val HEADER_USER_AGENT = "User-Agent"
    }
}
