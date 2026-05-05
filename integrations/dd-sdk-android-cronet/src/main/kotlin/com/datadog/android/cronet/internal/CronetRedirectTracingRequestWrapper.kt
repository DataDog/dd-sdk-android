/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.cronet.internal

import com.datadog.android.api.instrumentation.network.HttpRequestInfo
import com.datadog.android.api.instrumentation.network.MutableHttpRequestInfo
import com.datadog.android.internal.network.HttpSpec
import com.datadog.android.trace.internal.ApmNetworkInstrumentation
import com.datadog.android.trace.internal.net.RequestTracingState
import org.chromium.net.UrlRequest
import java.util.concurrent.atomic.AtomicReference

internal class CronetRedirectTracingRequestWrapper(
    override val delegate: UrlRequest?,
    private val previousRequestInfo: HttpRequestInfo?,
    private val newLocationUrl: String?,
    private val redirectStatusCode: Int?,
    private val apmNetworkInstrumentation: ApmNetworkInstrumentation?,
    private val apmTracingStateHolder: AtomicReference<RequestTracingState?>
) : CronetUrlRequestWrapper() {

    override fun followRedirect() {
        previousRequestInfo?.instrumentRedirect(newLocationUrl)
        super.followRedirect()
    }

    private fun HttpRequestInfo.instrumentRedirect(redirectUrl: String?) {
        if (this !is MutableHttpRequestInfo || redirectUrl == null || apmNetworkInstrumentation == null) {
            apmNetworkInstrumentation?.reportInstrumentationError {
                "Cannot instrument redirect in cronet. redirectUrl=$redirectUrl, ${this.javaClass.name}"
            }
            return
        }

        val redirectRequestInfo = newBuilder()
            .setUrl(redirectUrl)
            // For 301, 302, 303 redirects the HTTP client rewrites POST/PUT/PATCH to GET and drops the body.
            // We mirror that here so the span metadata (http.method) stays accurate.
            // See https://chromium.googlesource.com/chromium/src/%2B/HEAD/net/url_request/redirect_info.cc#20.
            .setMethod(computeMethodForRedirect(method, redirectStatusCode))
            // Clear existing tracing headers so that each redirect creates a new independent span/trace
            // This matches OkHttp behavior where each redirect is a separate root span
            .also(apmNetworkInstrumentation::removeTracingHeaders)
            .build()

        apmNetworkInstrumentation.onRequest(redirectRequestInfo)
            .also(apmTracingStateHolder::set)
    }

    private fun computeMethodForRedirect(
        originalMethod: String,
        redirectStatusCode: Int?
    ): String {
        val seeOtherMethodChangeAllowed =
            redirectStatusCode == HttpSpec.StatusCode.SEE_OTHER && originalMethod != HttpSpec.Method.HEAD

        val postMethodChangeCode = redirectStatusCode == HttpSpec.StatusCode.MOVED_PERMANENTLY ||
            redirectStatusCode == HttpSpec.StatusCode.FOUND

        return if (seeOtherMethodChangeAllowed || (postMethodChangeCode && originalMethod == HttpSpec.Method.POST)
        ) {
            HttpSpec.Method.GET
        } else {
            originalMethod
        }
    }
}
