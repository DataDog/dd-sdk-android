/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.internal

import android.util.Log
import com.datadog.benchmark.DatadogExporterConfiguration
import com.datadog.benchmark.internal.model.BenchmarkContext
import com.datadog.benchmark.internal.model.SpanEvent
import io.opentelemetry.sdk.metrics.data.MetricData
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

internal class DatadogHttpClient(
    private val context: BenchmarkContext,
    private val exporterConfiguration: DatadogExporterConfiguration,
    private val callFactory: Call.Factory = OkHttpClient.Builder()
        .callTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
        .connectTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
        .cache(null)
        .build(),
    private val metricRequestBodyBuilder: MetricRequestBodyBuilder = MetricRequestBodyBuilder(context),
    private val spanRequestBuilder: SpanRequestBodyBuilder = SpanRequestBodyBuilder(context)
) {

    internal fun uploadMetric(metrics: List<MetricData>) {
        postRequest(
            operationName = OPERATION_NAME_METRICS,
            url = exporterConfiguration.endPoint.metricUrl(),
            exporterConfiguration = exporterConfiguration,
            metricRequestBodyBuilder.build(metrics)
        )
    }

    internal fun uploadSpanEvent(spanEvents: List<SpanEvent>) {
        postRequest(
            operationName = OPERATION_NAME_TRACES,
            url = exporterConfiguration.endPoint.tracesUrl(),
            exporterConfiguration = exporterConfiguration,
            body = spanRequestBuilder.build(spanEvents)
        )
    }

    private fun postRequest(
        operationName: String,
        url: String,
        exporterConfiguration: DatadogExporterConfiguration,
        body: String
    ) {
        val headers = buildHeaders(
            requestId = UUID.randomUUID().toString(),
            clientToken = exporterConfiguration.apiKey,
            source = exporterConfiguration.resource,
            sdkVersion = exporterConfiguration.applicationVersion
        )
        val request = Request.Builder()
            .apply {
                headers.forEach {
                    addHeader(it.key, it.value)
                }
                addHeader(HEADER_USER_AGENT, getUserAgent())
            }
            .post(body.toRequestBody(CONTENT_TYPE_TEXT_UTF8))
            .cacheControl(CacheControl.Builder().noCache().build())
            .url(url)
            .build()

        val result = processRequest(request)
        if (result.isSuccess) {
            Log.i(BENCHMARK_LOGCAT_TAG, "$operationName data uploaded.")
        } else {
            Log.e(BENCHMARK_LOGCAT_TAG, "$operationName data failed to upload. ${result.exceptionOrNull()}")
        }
    }

    private fun getUserAgent(): String {
        return sanitizeHeaderValue(System.getProperty(SYSTEM_UA)).ifBlank {
            "Datadog (Linux; U; Android ${context.osVersion}; " + "${context.deviceModel} "
        }
    }

    private fun sanitizeHeaderValue(value: String?): String {
        return value?.filter { isValidHeaderValueChar(it) }.orEmpty()
    }

    private fun isValidHeaderValueChar(c: Char): Boolean {
        return c == '\t' || c in '\u0020' until '\u007F'
    }

    private fun buildHeaders(
        requestId: String,
        clientToken: String,
        source: String?,
        sdkVersion: String?
    ): Map<String, String> {
        return mutableMapOf<String, String>().apply {
            put(HEADER_REQUEST_ID, requestId)
            put(HEADER_API_KEY, clientToken)
            source?.let {
                put(HEADER_EVP_ORIGIN, it)
            }
            sdkVersion?.let {
                put(HEADER_EVP_ORIGIN_VERSION, it)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun processRequest(request: Request): Result<ResponseBody> {
        try {
            val response = callFactory.newCall(request).execute()
            return if (response.isSuccessful) {
                val body = response.body
                if (body != null) {
                    Result.success(body)
                } else {
                    Result.failure(
                        IllegalStateException(
                            "Request successful with empty body:" +
                                " ${request.method} ${request.url}"
                        )
                    )
                }
            } else {
                Result.failure(IOException("Request failed: ${response.code} - ${response.message}"))
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    companion object {
        private const val TIMEOUT_SEC = 45L

        private const val BENCHMARK_LOGCAT_TAG = "DatadogBenchmark"

        private const val HEADER_USER_AGENT = "User-Agent"

        private const val SYSTEM_UA = "http.agent"

        private const val OPERATION_NAME_METRICS = "metrics"

        private const val OPERATION_NAME_TRACES = "traces"

        /**
         * Datadog API key header.
         */
        private const val HEADER_API_KEY: String = "DD-API-KEY"

        /**
         * Datadog Event Platform Origin header, e.g. android, flutter, etc.
         */
        private const val HEADER_EVP_ORIGIN: String = "DD-EVP-ORIGIN"

        /**
         * Datadog Event Platform Origin version header, e.g. SDK version.
         */
        private const val HEADER_EVP_ORIGIN_VERSION: String = "DD-EVP-ORIGIN-VERSION"

        /**
         * Datadog Request ID header, used for debugging purposes.
         */
        private const val HEADER_REQUEST_ID: String = "DD-REQUEST-ID"

        /**
         * text/plain;charset=UTF-8 content type.
         */
        private val CONTENT_TYPE_TEXT_UTF8 = "text/plain;charset=UTF-8".toMediaType()
    }
}
