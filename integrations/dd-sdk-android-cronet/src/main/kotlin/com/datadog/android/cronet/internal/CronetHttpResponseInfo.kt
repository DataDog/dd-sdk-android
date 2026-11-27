/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.cronet.internal

import com.datadog.android.api.instrumentation.network.HttpCacheDelivery
import com.datadog.android.api.instrumentation.network.HttpCacheInfo
import com.datadog.android.api.instrumentation.network.HttpResponseInfo
import com.datadog.android.internal.network.HttpSpec
import org.chromium.net.UrlResponseInfo

// Deliberately does not implement PeekableBodyInfo, so HttpResponseInfo.peekBody() returns null for
// Cronet responses. CronetRequestCallback.onReadCompleted does see each chunk of the payload, but
// only in passing, in the buffer the application supplies and then consumes; nothing is retained,
// and this instance is only built once the request has finished, by which point the chunks are
// gone. Capturing them would mean copying every chunk of every response up front, at a memory cost
// paid whether or not a snapshot is ever asked for.
//
// metricsReceivedByteCount comes from RequestFinishedInfo.Metrics, which is only available once
// the request has finished. Instances built before that (e.g. for APM) pass null.
internal data class CronetHttpResponseInfo(
    private val response: UrlResponseInfo,
    override val request: CronetHttpRequestInfo?,
    private val metricsReceivedByteCount: Long?
) : HttpResponseInfo, HttpCacheInfo {

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
            return headers[HttpSpec.Header.CONTENT_TYPE]?.lastOrNull()
        }

    override val contentLength: Long?
        get() {
            /*
             *  Content-Length is expected to be declared once or in case of multiple repetitions should have same value.
             *  Otherwise, it's assumed that Content-Length is invalid
             *  according to  https://www.rfc-editor.org/rfc/rfc9110.pdf, page 63:
             *
             * Likewise, a sender forward a message with a Content-Length header field value that does not match the ABNF
             * above, with one exception: a recipient of a Content-Length header field value consisting of the same decimal
             * value repeated as a comma-separated list (e.g, "ContentLength: 42, 42")
             */
            val contentLengthHeaderValues = headers[HttpSpec.Header.CONTENT_LENGTH]?.toSet()

            return contentLengthHeaderValues?.firstOrNull()
                ?.takeIf { contentLengthHeaderValues.size == 1 }
                ?.toLongOrNull()
                ?: response.receivedByteCount.takeIf { it >= 0 }
        }

    // The Cronet docs contradict each other on whether the metrics byte count includes redirects:
    // RequestFinishedInfo.getMetrics() says it covers "all redirects and the ultimate response
    // combined", while RequestFinishedInfo.Metrics.getReceivedByteCount() says it "does not include
    // any previous redirects". UrlResponseInfo.getReceivedByteCount() is documented to include all
    // redirects.
    //
    // CronetCacheDeliveryTest (instrumented/integration) measures both counters against a real
    // engine: they are equal in every scenario, redirects included, so getMetrics() describes the
    // actual behavior. The metrics count therefore can't tell the bytes of a cache validation from
    // the ones of the redirect hops. After a redirect, the decision is taken from the URL chain
    // counter alone: only a chain without any network bytes is a local hit, and anything else is a
    // cached response of unknown reason.
    //
    // Known limitation: Cronet exposes no per-hop breakdown of the received bytes once the request
    // has finished, so after a network redirect an accurate value isn't available. A redirect to a
    // local hit (only the redirect bytes) and a redirect to a 304 validation (redirect and
    // validation bytes) look the same, and both are reported as a cached response of unknown
    // reason, where iOS and OkHttp report a local hit and a validated hit. Telling them apart would
    // need the byte count recorded at each redirect in the request callback, handed over to the
    // request-finished listener on another thread: not worth the added complexity.
    //
    // A network load reports the URL chain counter, which is available even without metrics. It
    // includes the redirect hops, where iOS only counts the last transaction.
    override val cacheDelivery: HttpCacheDelivery
        get() = when {
            !response.wasCached() -> HttpCacheDelivery.NetworkLoad(networkBytes = response.receivedByteCount)
            response.urlChain.size > 1 -> if (response.receivedByteCount == 0L) {
                HttpCacheDelivery.LocalHit
            } else {
                HttpCacheDelivery.CachedUnknownReason
            }
            metricsReceivedByteCount == 0L -> HttpCacheDelivery.LocalHit
            metricsReceivedByteCount != null && metricsReceivedByteCount > 0L -> HttpCacheDelivery.ValidatedHit(
                networkBytes = metricsReceivedByteCount
            )
            // not measured
            else -> HttpCacheDelivery.CachedUnknownReason
        }
}
