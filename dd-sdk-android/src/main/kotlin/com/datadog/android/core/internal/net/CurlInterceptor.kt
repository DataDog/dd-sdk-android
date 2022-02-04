/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.net

import android.util.Log
import java.io.IOException
import java.nio.charset.Charset
import java.util.Locale
import kotlin.jvm.Throws
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.Buffer

/**
 * This interceptor logs the request as a valid CURL command line.
 */
internal class CurlInterceptor(
    private val printBody: Boolean = false,
    private val output: (String) -> Unit = { Log.i("Curl", it) }
) : Interceptor {

    // region Interceptor

    /**
     * Observes, modifies, or short-circuits requests going out and the responses coming back in.
     */
    // let the proceed exception be handled by the caller
    @Suppress("UnsafeThirdPartyFunctionCall")
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        val copy = request.newBuilder().build()
        val curl: String = CurlBuilder(copy, printBody).toCommand()
        output(curl)

        return chain.proceed(request)
    }

    // endregion

    // region Internal

    class CurlBuilder(
        val url: String,
        val method: String,
        val contentType: String? = null,
        val body: String? = null,
        val headers: Map<String, List<String>> = emptyMap()
    ) {

        constructor(request: Request, printBody: Boolean) :
            this(
                url = request.url().toString(),
                method = request.method(),
                contentType = request.body()?.contentType()?.toString(),
                body = if (printBody) peekBody(request.body()) else null,
                headers = request.headers().toMultimap()
            )

        fun toCommand(): String {
            val parts = mutableListOf<String>()
            parts.add("curl")
            parts.add(FORMAT_METHOD.format(Locale.US, method.uppercase(Locale.US)))

            headers.forEach { (key, values) ->
                values.forEach { value ->
                    parts.add(FORMAT_HEADER.format(Locale.US, key, value))
                }
            }

            if (contentType != null && !headers.containsKey(CONTENT_TYPE)) {
                parts.add(FORMAT_HEADER.format(Locale.US, CONTENT_TYPE, contentType))
            }

            if (body != null) {
                parts.add(FORMAT_BODY.format(Locale.US, body))
            }

            parts.add(FORMAT_URL.format(Locale.US, url))

            return parts.joinToString(" ")
        }
    }

    // endregion

    companion object {

        private const val FORMAT_HEADER = "-H \"%1\$s:%2\$s\""
        private const val FORMAT_METHOD = "-X %1\$s"
        private const val FORMAT_BODY = "-d '%1\$s'"
        private const val FORMAT_URL = "\"%1\$s\""
        private const val CONTENT_TYPE = "Content-Type"

        private fun peekBody(body: RequestBody?): String? {
            if (body == null) return null

            return try {
                val sink = Buffer()
                val charset: Charset = Charset.defaultCharset()

                body.writeTo(sink)
                sink.readString(charset)
            } catch (e: IOException) {
                "Error while reading body: $e"
            } catch (e: IllegalArgumentException) {
                "Error while reading body: $e"
            }
        }
    }
}
