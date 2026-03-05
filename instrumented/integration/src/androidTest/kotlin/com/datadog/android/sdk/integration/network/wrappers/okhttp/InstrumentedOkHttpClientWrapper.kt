/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.sdk.integration.network.wrappers.okhttp

import com.datadog.android.internal.network.HttpSpec
import com.datadog.android.okhttp.configureDatadogInstrumentation
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.sdk.integration.network.models.ClientExecutionResult
import com.datadog.android.sdk.integration.network.models.TestRequest
import com.datadog.android.sdk.integration.network.models.TestResponse
import com.datadog.android.sdk.integration.network.wrappers.HttpTestClientWrapper
import com.datadog.android.trace.ApmNetworkInstrumentationConfiguration
import com.datadog.android.trace.ApmNetworkTracingScope
import com.datadog.android.trace.ExperimentalTraceApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.UUID

@OptIn(ExperimentalTraceApi::class, ExperimentalRumApi::class)
internal class InstrumentedOkHttpClientWrapper(private val baseUrl: String) : HttpTestClientWrapper {

    override val name: String = "InstrumentedOkHttp"

    override suspend fun execute(request: TestRequest): ClientExecutionResult =
        withContext(Dispatchers.IO) {
            val spansCollector = InstrumentedOkHttpSpansCollector()

            val client = OkHttpClient.Builder()
                .configureDatadogInstrumentation(
                    rumInstrumentationConfiguration = null,
                    apmInstrumentationConfiguration = ApmNetworkInstrumentationConfiguration(
                        HttpTestClientWrapper.tracedHosts
                    )
                        .setTraceSampleRate(100f)
                        .setTraceScope(ApmNetworkTracingScope.ALL)
                        .setTracedRequestListener(spansCollector)
                )
                .build()

            val (response, error) = client.newCall(request.toOkHttpRequest(baseUrl, name))
                .executeRequest()

            ClientExecutionResult(
                name = name,
                request = request,
                response = response,
                collectedSpans = spansCollector.spans,
                error = error
            )
        }

    override fun shutdown() = Unit

    companion object {

        private fun Call.executeRequest(): Pair<TestResponse?, Throwable?> =
            try {
                execute().toTestResponse() to null
            } catch (e: Throwable) {
                null to e
            }

        private fun Response.toTestResponse() = TestResponse(
            statusCode = code,
            headers = headers.toMultimap(),
            body = body?.string()
        )

        private fun TestRequest.toOkHttpRequest(baseUrl: String, clientName: String) = Request.Builder()
            .url(baseUrl + url)
            .addHeader(NETWORK_FRAMEWORK_HEADER, clientName + UUID.randomUUID().toString())
            .addHeaders(headers)
            .setMethod(method, body?.toRequestBody(MEDIA_TYPE_TEXT))
            .build()

        private const val NETWORK_FRAMEWORK_HEADER = "NetworkFramework"

        private fun Request.Builder.addHeaders(headers: Map<String, List<String>>) = apply {
            headers.forEach { (key, values) ->
                values.forEach { value -> addHeader(key, value) }
            }
        }

        private fun Request.Builder.setMethod(method: String, body: RequestBody?) = apply {
            when (method) {
                HttpSpec.Method.GET -> get()
                HttpSpec.Method.HEAD -> head()
                HttpSpec.Method.DELETE -> delete()
                HttpSpec.Method.POST -> post(body ?: EMPTY_BODY)
                HttpSpec.Method.PATCH -> patch(body ?: EMPTY_BODY)
                HttpSpec.Method.PUT -> put(body ?: EMPTY_BODY)
                else -> method(method, body)
            }
        }

        private val MEDIA_TYPE_TEXT = HttpSpec.ContentType.TEXT_PLAIN.toMediaType()
        private val EMPTY_BODY = "".toRequestBody(MEDIA_TYPE_TEXT)
    }
}
