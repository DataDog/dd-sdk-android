/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.net

import com.datadog.android.v2.api.InternalLogger
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import okio.GzipSink
import okio.Okio
import java.io.IOException
import kotlin.jvm.Throws

/**
 * This interceptor compresses the HTTP request body.
 *
 * This class uses the [GzipSink] to compress the body content.
 */
internal class GzipRequestInterceptor(private val internalLogger: InternalLogger) : Interceptor {

    // region Interceptor

    /**
     * Observes, modifies, or short-circuits requests going out and the responses coming back in.
     */
    // let the proceed exception be handled by the caller
    @Suppress("UnsafeThirdPartyFunctionCall", "TooGenericExceptionCaught")
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest: Request = chain.request()
        val body = originalRequest.body()

        return if (body == null ||
            originalRequest.header(HEADER_ENCODING) != null ||
            body is MultipartBody
        ) {
            chain.proceed(originalRequest)
        } else {
            val compressedRequest = try {
                originalRequest.newBuilder()
                    .header(HEADER_ENCODING, ENCODING_GZIP)
                    .method(originalRequest.method(), gzip(body))
                    .build()
            } catch (e: Exception) {
                internalLogger.log(
                    InternalLogger.Level.WARN,
                    targets = listOf(
                        InternalLogger.Target.MAINTAINER,
                        InternalLogger.Target.TELEMETRY
                    ),
                    { "Unable to gzip request body" },
                    e
                )
                originalRequest
            }
            chain.proceed(compressedRequest)
        }
    }

    // endregion

    // region Internal

    private fun gzip(body: RequestBody): RequestBody? {
        return object : RequestBody() {
            override fun contentType(): MediaType? {
                return body.contentType()
            }

            override fun contentLength(): Long {
                return -1 // We don't know the compressed length in advance!
            }

            @Suppress("UnsafeThirdPartyFunctionCall") // write to is expected to throw IOExceptions
            override fun writeTo(sink: BufferedSink) {
                val gzipSink: BufferedSink = Okio.buffer(GzipSink(sink))
                body.writeTo(gzipSink)
                gzipSink.close()
            }
        }
    }

    // endregion

    companion object {
        private const val HEADER_ENCODING = "Content-Encoding"
        private const val ENCODING_GZIP = "gzip"
    }
}
