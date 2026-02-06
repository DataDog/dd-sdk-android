/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.network.utils

import com.datadog.android.core.internal.net.HttpSpec
import com.google.gson.GsonBuilder
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * A mock web server for network instrumentation tests.
 *
 * Provides endpoints compatible with TestServer:
 * - /{method} - Returns method info
 * - /error/{code}/{method} - Returns error response
 * - /redirect/{hopCount}/{method} - Handles redirects
 * - /retry/{failCount}/{method} - Handles rate limiting with retry
 */
internal class TestEchoWebServer {

    private val mockWebServer = MockWebServer()
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val retryCounters = ConcurrentHashMap<String, AtomicInteger>()

    /**
     * Base URL for HTTP requests to the mock server.
     */
    val baseUrl: String
        get() = mockWebServer.url("/").toString().removeSuffix("/")

    /**
     * Starts the mock web server.
     */
    fun start() {
        mockWebServer.dispatcher = createDispatcher()
        mockWebServer.start()
    }

    /**
     * Shuts down the mock web server.
     */
    fun shutdown() {
        mockWebServer.shutdown()
    }

    private fun createDispatcher() = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.path ?: return notFoundResponse()
            val method = request.method ?: HttpSpec.Method.GET

            return when {
                path.startsWith(ERROR_PATH_PREFIX) -> handleErrorEndpoint(path, method)
                path.startsWith(REDIRECT_PATH_PREFIX) -> handleRedirectEndpoint(path, method)
                path.startsWith(RETRY_PATH_PREFIX) -> handleRetryEndpoint(request, path, method)
                else -> handleMethodEndpoint(request, path, method)
            }
        }
    }

    private fun handleMethodEndpoint(
        request: RecordedRequest,
        path: String,
        method: String
    ): MockResponse {
        val response = MethodResponse(
            method = method,
            path = path,
            body = request.body.readUtf8().takeIf { it.isNotEmpty() },
            headers = request.headers.toMultimap().mapValues { it.value.joinToString(", ") }
        )
        return jsonResponse(HttpSpec.StatusCodes.OK, response)
    }

    private fun handleErrorEndpoint(path: String, method: String): MockResponse {
        // /error/{code}/{method}
        val parts = path.split("/")
        if (parts.size < PATH_PARTS_MIN_SIZE) {
            return jsonResponse(
                statusCode = HttpSpec.StatusCodes.BAD_REQUEST,
                body = ErrorResponse(
                    statusCode = HttpSpec.StatusCodes.BAD_REQUEST,
                    method = method,
                    message = STATUS_MESSAGE_BAD_REQUEST
                )
            )
        }

        val errorCode = parts[2].toIntOrNull() ?: HttpSpec.StatusCodes.INTERNAL_ERROR
        val response = ErrorResponse(
            statusCode = errorCode,
            method = method,
            message = getHttpStatusMessage(errorCode)
        )

        return jsonResponse(errorCode, response)
    }

    private fun handleRedirectEndpoint(path: String, method: String): MockResponse {
        // /redirect/{hopCount}/{method}
        val parts = path.split("/")
        if (parts.size < PATH_PARTS_MIN_SIZE) {
            return jsonResponse(
                statusCode = HttpSpec.StatusCodes.BAD_REQUEST,
                body = ErrorResponse(
                    statusCode = HttpSpec.StatusCodes.BAD_REQUEST,
                    method = method,
                    message = STATUS_MESSAGE_BAD_REQUEST
                )
            )
        }

        val hopCount = parts[2].toIntOrNull() ?: 0

        return if (hopCount > 0) {
            val nextPath = "/redirect/${hopCount - 1}/${parts[3]}"
            MockResponse()
                .setResponseCode(HttpSpec.StatusCodes.FOUND)
                .setHeader(HttpSpec.Headers.LOCATION, nextPath)
        } else {
            val response = MethodResponse(
                method = method,
                path = path
            )
            jsonResponse(HttpSpec.StatusCodes.OK, response)
        }
    }

    private fun handleRetryEndpoint(
        request: RecordedRequest,
        path: String,
        method: String
    ): MockResponse {
        // /retry/{failCount}/{method}
        val parts = path.split("/")
        if (parts.size < PATH_PARTS_MIN_SIZE) {
            return jsonResponse(
                statusCode = HttpSpec.StatusCodes.BAD_REQUEST,
                body = ErrorResponse(
                    statusCode = HttpSpec.StatusCodes.BAD_REQUEST,
                    method = method,
                    message = STATUS_MESSAGE_BAD_REQUEST
                )
            )
        }

        val failCount = parts[2].toIntOrNull() ?: 1
        val networkFramework = request.getHeader(NETWORK_FRAMEWORK_HEADER) ?: "unknown"
        val key = "$path-$method-$networkFramework"

        val counter = retryCounters.computeIfAbsent(key) { AtomicInteger(0) }
        val currentCount = counter.incrementAndGet()

        return if (currentCount <= failCount) {
            val retryResponse = RetryResponse(
                statusCode = HttpSpec.StatusCodes.TOO_MANY_REQUESTS,
                method = method,
                message = STATUS_MESSAGE_TOO_MANY_REQUESTS,
                retryCount = currentCount,
                remainingRetries = failCount - currentCount
            )
            MockResponse()
                .setResponseCode(HttpSpec.StatusCodes.TOO_MANY_REQUESTS)
                .setHeader(HttpSpec.Headers.RETRY_AFTER, "1")
                .setHeader(HttpSpec.Headers.CONTENT_TYPE, HttpSpec.ContentType.APPLICATION_JSON)
                .setBody(gson.toJson(retryResponse))
        } else {
            retryCounters.remove(key)
            val response = MethodResponse(
                method = method,
                path = path,
                body = request.body.readUtf8().takeIf { it.isNotEmpty() }
            )
            jsonResponse(HttpSpec.StatusCodes.OK, response)
        }
    }

    private fun jsonResponse(statusCode: Int, body: Any): MockResponse {
        return MockResponse()
            .setResponseCode(statusCode)
            .setHeader(HttpSpec.Headers.CONTENT_TYPE, HttpSpec.ContentType.APPLICATION_JSON)
            .setBody(gson.toJson(body))
    }

    private fun notFoundResponse(): MockResponse {
        return MockResponse().setResponseCode(HttpSpec.StatusCodes.NOT_FOUND)
    }

    private fun getHttpStatusMessage(code: Int): String = when (code) {
        HttpSpec.StatusCodes.BAD_REQUEST -> STATUS_MESSAGE_BAD_REQUEST
        HttpSpec.StatusCodes.UNAUTHORIZED -> STATUS_MESSAGE_UNAUTHORIZED
        HttpSpec.StatusCodes.FORBIDDEN -> STATUS_MESSAGE_FORBIDDEN
        HttpSpec.StatusCodes.NOT_FOUND -> STATUS_MESSAGE_NOT_FOUND
        HttpSpec.StatusCodes.METHOD_NOT_ALLOWED -> STATUS_MESSAGE_METHOD_NOT_ALLOWED
        HttpSpec.StatusCodes.REQUEST_TIMEOUT -> STATUS_MESSAGE_REQUEST_TIMEOUT
        HttpSpec.StatusCodes.TOO_MANY_REQUESTS -> STATUS_MESSAGE_TOO_MANY_REQUESTS
        HttpSpec.StatusCodes.INTERNAL_ERROR -> STATUS_MESSAGE_INTERNAL_ERROR
        HttpSpec.StatusCodes.BAD_GATEWAY -> STATUS_MESSAGE_BAD_GATEWAY
        HttpSpec.StatusCodes.SERVICE_UNAVAILABLE -> STATUS_MESSAGE_SERVICE_UNAVAILABLE
        HttpSpec.StatusCodes.GATEWAY_TIMEOUT -> STATUS_MESSAGE_GATEWAY_TIMEOUT
        else -> STATUS_MESSAGE_ERROR
    }

    private data class MethodResponse(
        val method: String,
        val path: String,
        val body: String? = null,
        val headers: Map<String, String> = emptyMap()
    )

    private data class ErrorResponse(
        val error: Boolean = true,
        val statusCode: Int,
        val method: String,
        val message: String
    )

    private data class RetryResponse(
        val statusCode: Int,
        val method: String,
        val message: String,
        val retryCount: Int,
        val remainingRetries: Int
    )

    companion object {
        private const val ERROR_PATH_PREFIX = "/error/"
        private const val REDIRECT_PATH_PREFIX = "/redirect/"
        private const val RETRY_PATH_PREFIX = "/retry/"
        private const val NETWORK_FRAMEWORK_HEADER = "NetworkFramework"
        private const val PATH_PARTS_MIN_SIZE = 4

        // Status messages
        private const val STATUS_MESSAGE_BAD_REQUEST = "Bad Request"
        private const val STATUS_MESSAGE_UNAUTHORIZED = "Unauthorized"
        private const val STATUS_MESSAGE_FORBIDDEN = "Forbidden"
        private const val STATUS_MESSAGE_NOT_FOUND = "Not Found"
        private const val STATUS_MESSAGE_METHOD_NOT_ALLOWED = "Method Not Allowed"
        private const val STATUS_MESSAGE_REQUEST_TIMEOUT = "Request Timeout"
        private const val STATUS_MESSAGE_TOO_MANY_REQUESTS = "Too Many Requests"
        private const val STATUS_MESSAGE_INTERNAL_ERROR = "Internal Server Error"
        private const val STATUS_MESSAGE_BAD_GATEWAY = "Bad Gateway"
        private const val STATUS_MESSAGE_SERVICE_UNAVAILABLE = "Service Unavailable"
        private const val STATUS_MESSAGE_GATEWAY_TIMEOUT = "Gateway Timeout"
        private const val STATUS_MESSAGE_ERROR = "Error"
    }
}
