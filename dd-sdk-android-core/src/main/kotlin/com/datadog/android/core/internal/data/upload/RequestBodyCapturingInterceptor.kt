/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.upload

import com.datadog.android.api.InternalLogger
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlin.jvm.Throws

/**
 * An OkHttp interceptor that captures raw request bodies before compression.
 * Used for benchmarking compression algorithms.
 *
 * @param captureDir The directory where captured request bodies will be saved.
 * @param internalLogger Logger for error reporting.
 */
internal class RequestBodyCapturingInterceptor(
    private val captureDir: File,
    private val internalLogger: InternalLogger
) : Interceptor {

    private val requestCounter = AtomicLong(0)

    init {
        if (!captureDir.exists()) {
            captureDir.mkdirs()
        }
    }

    @Suppress("TooGenericExceptionCaught")
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest: Request = chain.request()
        val body = originalRequest.body

        if (body != null) {
            try {
                captureRequestBody(originalRequest)
            } catch (e: Exception) {
                internalLogger.log(
                    InternalLogger.Level.WARN,
                    targets = listOf(
                        InternalLogger.Target.MAINTAINER,
                        InternalLogger.Target.TELEMETRY
                    ),
                    { "Unable to capture request body for benchmarking" },
                    e
                )
            }
        }

        return chain.proceed(originalRequest)
    }

    @Suppress("UnsafeThirdPartyFunctionCall")
    private fun captureRequestBody(request: Request) {
        val body = request.body ?: return

        // Extract feature type from URL path (e.g., /api/v2/rum -> rum)
        val featureType = extractFeatureType(request.url.encodedPath)

        // Create unique filename with timestamp and counter
        val timestamp = System.currentTimeMillis()
        val counter = requestCounter.incrementAndGet()
        val filename = "${timestamp}_${counter}_$featureType.bin"

        // Read the body into a buffer
        val buffer = Buffer()
        body.writeTo(buffer)
        val bodyBytes = buffer.readByteArray()

        // Write to file
        val outputFile = File(captureDir, filename)
        outputFile.writeBytes(bodyBytes)

        internalLogger.log(
            InternalLogger.Level.INFO,
            InternalLogger.Target.MAINTAINER,
            { "Captured request body: $filename (${bodyBytes.size} bytes)" }
        )
    }

    private fun extractFeatureType(path: String): String {
        // Extract feature type from paths like:
        // /api/v2/rum -> rum
        // /api/v2/logs -> logs
        // /api/v2/spans -> traces
        // /api/v2/replay -> session-replay
        // /api/v2/profiles -> profiling
        return when {
            path.contains("/rum") -> "rum"
            path.contains("/logs") -> "logs"
            path.contains("/spans") -> "traces"
            path.contains("/replay") -> "session-replay"
            path.contains("/profiles") -> "profiling"
            path.contains("/srcmap") -> "srcmap"
            path.contains("/ndkcrash") -> "ndk-crash"
            else -> "unknown"
        }
    }
}
