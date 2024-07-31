/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.internal

import android.util.Log
import com.datadog.benchmark.DatadogExporterConfiguration
import com.datadog.benchmark.internal.model.MetricContext
import com.google.gson.JsonElement
import io.opentelemetry.sdk.metrics.data.MetricData
import okhttp3.CacheControl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

internal class DatadogHttpClient(
    private val context: MetricContext,
    private val exporterConfiguration: DatadogExporterConfiguration
) {

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
        .connectTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
        .cache(null)
        .build()

    private val metricRequestBodyBuilder = MetricRequestBodyBuilder(context)

    fun uploadMetric(metrics: List<MetricData>) {
        val response = postRequest(
            exporterConfiguration = exporterConfiguration,
            metricRequestBodyBuilder.buildJsonElement(metrics)
        )
        if (response.isSuccess) {
            Log.i(BENCHMARK_LOGCAT_TAG, "${metrics.size} Metrics data uploaded.")
        } else {
            Log.e(BENCHMARK_LOGCAT_TAG, "Metrics data failed to upload. ${response.exceptionOrNull()}")
        }
    }

    private fun postRequest(
        exporterConfiguration: DatadogExporterConfiguration,
        body: JsonElement
    ): Result<ResponseBody> {
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
            .post(body.toString().toRequestBody(TYPE_JSON))
            .cacheControl(CacheControl.Builder().noCache().build())
            .url(exporterConfiguration.endPoint.metricUrl())
            .build()

        return processRequest(request)
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
            val response = okHttpClient.newCall(request).execute()
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

        private val TYPE_JSON = "application/json".toMediaTypeOrNull()

        /**
         * Datadog API key header.
         */
        const val HEADER_API_KEY: String = "DD-API-KEY"

        /**
         * Datadog Event Platform Origin header, e.g. android, flutter, etc.
         */
        const val HEADER_EVP_ORIGIN: String = "DD-EVP-ORIGIN"

        /**
         * Datadog Event Platform Origin version header, e.g. SDK version.
         */
        const val HEADER_EVP_ORIGIN_VERSION: String = "DD-EVP-ORIGIN-VERSION"

        /**
         * Datadog Request ID header, used for debugging purposes.
         */
        const val HEADER_REQUEST_ID: String = "DD-REQUEST-ID"
    }
}
