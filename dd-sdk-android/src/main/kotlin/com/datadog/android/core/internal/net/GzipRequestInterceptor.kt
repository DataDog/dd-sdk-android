/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.core.internal.net

import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import okio.GzipSink
import okio.buffer

/**
 * This interceptor compresses the HTTP request body.
 *
 * This class uses the [GzipSink] to compress the body content.
 */
internal class GzipRequestInterceptor : Interceptor {

    // region Interceptor

    /**
     * Observes, modifies, or short-circuits requests going out and the responses coming back in.
     */
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest: Request = chain.request()
        val body = originalRequest.body

        return if (body == null || originalRequest.header(HEADER_ENCODING) != null) {
            chain.proceed(originalRequest)
        } else {
            val compressedRequest = originalRequest.newBuilder()
                .header(HEADER_ENCODING, ENCODING_GZIP)
                .method(originalRequest.method, gzip(body))
                .build()
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

            override fun writeTo(sink: BufferedSink) {
                val gzipSink: BufferedSink = GzipSink(sink).buffer()
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
