/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.cronet.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.instrumentation.network.ResponseInfo
import com.datadog.android.core.internal.net.HttpSpec

internal data class CronetResponseInfo(
    private val response: org.chromium.net.UrlResponseInfo
) : ResponseInfo {

    override val url: String get() = response.url
    override val statusCode: Int get() = response.httpStatusCode
    override val headers: Map<String, List<String>> get() = response.allHeaders
    override val contentType: String?
        get() {
            /*
             * We're trying to take the latest header value if multiple specified, according to
             *  https://www.rfc-editor.org/rfc/rfc9110.pdf, page 57:
             *
             * Although Content-Type is defined as a singleton field, it is sometimes incorrectly generated
             * multiple times, resulting in a combined field value that appears to be a list. Recipients often
             * attempt to handle this error by using the last syntactically valid member of the list, leading to
             * potential interoperability and security issues if different implementations have different error
             * handling behaviors.
             */
            return headers[HttpSpec.Headers.CONTENT_TYPE]?.lastOrNull()
        }

    override fun computeContentLength(internalLogger: InternalLogger): Long? {
        /*
         *  Content-Length is expected to be declared once or in case of multiple repetitions should have same value.
         *  Otherwise it's assumed that Content-Length is invalid
         *  according to  https://www.rfc-editor.org/rfc/rfc9110.pdf, page 63:
         *
         * Likewise, a sender forward a message with a Content-Length header field value that does not match the ABNF
         * above, with one exception: a recipient of a Content-Length header field value consisting of the same decimal
         * value repeated as a comma-separated list (e.g, "ContentLength: 42, 42")
         */
        val contentLengthHeaderValues = headers[HttpSpec.Headers.CONTENT_LENGTH]?.toSet()

        return contentLengthHeaderValues?.firstOrNull()
            ?.takeIf { contentLengthHeaderValues.size == 1 }
            ?.toLongOrNull()
            ?: response.receivedByteCount.takeIf { it >= 0 }
    }
}
