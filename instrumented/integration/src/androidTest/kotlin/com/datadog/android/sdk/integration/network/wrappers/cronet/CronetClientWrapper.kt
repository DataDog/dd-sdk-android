/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.network.wrappers.cronet

import android.content.Context
import com.datadog.android.cronet.configureDatadogInstrumentation
import com.datadog.android.internal.network.HttpSpec
import com.datadog.android.internal.network.HttpSpec.Method.isMethodWithBody
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.sdk.integration.network.models.ClientExecutionResult
import com.datadog.android.sdk.integration.network.models.TestRequest
import com.datadog.android.sdk.integration.network.models.TestResponse
import com.datadog.android.sdk.integration.network.wrappers.HttpTestClientWrapper
import com.datadog.android.sdk.integration.network.wrappers.HttpTestClientWrapper.Companion.tracedHosts
import com.datadog.android.trace.ApmNetworkInstrumentationConfiguration
import com.datadog.android.trace.ExperimentalTracingApi
import kotlinx.coroutines.suspendCancellableCoroutine
import org.chromium.net.CronetEngine
import org.chromium.net.CronetException
import org.chromium.net.UploadDataProvider
import org.chromium.net.UploadDataSink
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.coroutines.resume

@OptIn(ExperimentalRumApi::class, ExperimentalTracingApi::class)
internal class CronetClientWrapper(
    private val context: Context,
    private val baseUrl: String
) : HttpTestClientWrapper {

    override val name: String = "Cronet"
    private val executor = Executors.newSingleThreadExecutor()

    override suspend fun execute(request: TestRequest): ClientExecutionResult =
        suspendCancellableCoroutine { continuation ->
            val cronetSpansCollector = CronetSpansCollector()
            val callback = object : UrlRequest.Callback() {
                private val responseBody = ByteArrayOutputStream()

                override fun onRedirectReceived(
                    urlRequest: UrlRequest,
                    info: UrlResponseInfo,
                    newLocationUrl: String
                ) {
                    urlRequest.followRedirect()
                }

                override fun onResponseStarted(urlRequest: UrlRequest, info: UrlResponseInfo) {
                    urlRequest.read(ByteBuffer.allocateDirect(BUFFER_SIZE))
                }

                override fun onReadCompleted(
                    urlRequest: UrlRequest,
                    info: UrlResponseInfo,
                    byteBuffer: ByteBuffer
                ) {
                    byteBuffer.flip()
                    val bytes = ByteArray(byteBuffer.remaining())
                    byteBuffer.get(bytes)
                    responseBody.write(bytes)
                    byteBuffer.clear()
                    urlRequest.read(byteBuffer)
                }

                override fun onSucceeded(urlRequest: UrlRequest, info: UrlResponseInfo) = continuation.resume(
                    newExecutionResult(
                        error = null,
                        response = TestResponse(
                            statusCode = info.httpStatusCode,
                            headers = info.allHeaders.mapValues { it.value.toList() },
                            body = responseBody.toString(Charset.defaultCharset().name())
                        )
                    )
                )

                override fun onFailed(
                    urlRequest: UrlRequest,
                    info: UrlResponseInfo?,
                    error: CronetException
                ) = continuation.resume(newExecutionResult(response = null, error = error))

                override fun onCanceled(
                    urlRequest: UrlRequest,
                    info: UrlResponseInfo?
                ) = continuation.resume(newExecutionResult(response = null, error = IOException("Request cancelled")))

                private fun newExecutionResult(
                    response: TestResponse?,
                    error: Throwable?
                ) = ClientExecutionResult(
                    name = name,
                    request = request,
                    response = response,
                    error = error,
                    collectedSpans = cronetSpansCollector.spans
                )
            }

            val engine = CronetEngine.Builder(context)
                .configureDatadogInstrumentation(
                    rumInstrumentationConfiguration = null,
                    apmInstrumentationConfiguration = ApmNetworkInstrumentationConfiguration(tracedHosts)
                        .setTraceSampleRate(100f)
                        .setTracedRequestListener(cronetSpansCollector)
                )
                .build()

            engine.newUrlRequestBuilder(baseUrl + request.url, callback, executor)
                .setMethod(request.method, request.body)
                .addHeader(NETWORK_FRAMEWORK_HEADER, name + UUID.randomUUID().toString())
                .setHeaders(request.headers)
                .build()
                .start()
        }

    override fun shutdown() = executor.shutdown()

    private fun UrlRequest.Builder.setMethod(method: String, body: String?) = apply {
        setHttpMethod(method)
        if (body != null && isMethodWithBody(method)) {
            addHeader(HttpSpec.Header.CONTENT_TYPE, HttpSpec.ContentType.TEXT_PLAIN)
            setUploadDataProvider(StringUploadDataProvider(body), executor)
        }
    }

    companion object {
        private const val NETWORK_FRAMEWORK_HEADER = "NetworkFramework"
        private const val BUFFER_SIZE = 32 * 1024

        private fun UrlRequest.Builder.setHeaders(headers: Map<String, List<String>>) = apply {
            headers.forEach { (key, values) ->
                values.forEach { value -> addHeader(key, value) }
            }
        }
    }
}

private class StringUploadDataProvider(
    data: String
) : UploadDataProvider() {
    private val bytes = data.toByteArray()
    private var offset = 0

    override fun getLength(): Long = bytes.size.toLong()

    override fun read(uploadDataSink: UploadDataSink, byteBuffer: ByteBuffer) {
        val remaining = bytes.size - offset
        val toWrite = minOf(remaining, byteBuffer.remaining())
        byteBuffer.put(bytes, offset, toWrite)
        offset += toWrite
        uploadDataSink.onReadSucceeded(false)
    }

    override fun rewind(uploadDataSink: UploadDataSink) {
        offset = 0
        uploadDataSink.onRewindSucceeded()
    }
}
