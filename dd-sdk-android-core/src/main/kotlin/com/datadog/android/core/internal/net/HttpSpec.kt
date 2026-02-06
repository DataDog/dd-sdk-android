/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.core.internal.net

import com.datadog.android.lint.InternalApi

/**
 * HTTP specification constants and utilities.
 */
@InternalApi
object HttpSpec {

    /**
     * Standard HTTP request methods.
     */
    object Method {
        /** HTTP GET method. */
        const val GET: String = "GET"

        /** HTTP POST method. */
        const val POST: String = "POST"

        /** HTTP PATCH method. */
        const val PATCH: String = "PATCH"

        /** HTTP PUT method. */
        const val PUT: String = "PUT"

        /** HTTP HEAD method. */
        const val HEAD: String = "HEAD"

        /** HTTP DELETE method. */
        const val DELETE: String = "DELETE"

        /** HTTP TRACE method. */
        const val TRACE: String = "TRACE"

        /** HTTP OPTIONS method. */
        const val OPTIONS: String = "OPTIONS"

        /** HTTP CONNECT method. */
        const val CONNECT: String = "CONNECT"

        /**
         * Returns a list of all HTTP methods.
         */
        fun values() = listOf(GET, POST, PATCH, PUT, HEAD, DELETE, TRACE, OPTIONS, CONNECT)

        /**
         * Checks if the given HTTP method typically includes a request body.
         * @param method the HTTP method to check
         * @return true if the method is POST, PUT, or PATCH; false otherwise
         */
        fun isMethodWithBody(method: String) =
            POST == method || PUT == method || PATCH == method
    }

    /**
     * Standard HTTP header names.
     */
    object Headers {
        /** Content-Type header name. */
        const val CONTENT_TYPE: String = "Content-Type"

        /** Content-Length header name. */
        const val CONTENT_LENGTH: String = "Content-Length"

        /** Location header name (used in redirects). */
        const val LOCATION: String = "Location"

        /** Retry-After header name. */
        const val RETRY_AFTER: String = "Retry-After"

        /** Sec-WebSocket-Accept header name. */
        const val WEBSOCKET_ACCEPT_HEADER: String = "Sec-WebSocket-Accept"
    }

    /**
     * Standard HTTP status codes.
     */
    object StatusCodes {
        /** HTTP 200 OK status code. */
        const val OK: Int = 200

        /** HTTP 301 Moved Permanently status code. */
        const val MOVED_PERMANENTLY: Int = 301

        /** HTTP 302 Found status code. */
        const val FOUND: Int = 302

        /** HTTP 303 See Other status code. */
        const val SEE_OTHER: Int = 303

        /** HTTP 307 Temporary Redirect status code. */
        const val TEMPORARY_REDIRECT: Int = 307

        /** HTTP 308 Permanent Redirect status code. */
        const val PERMANENT_REDIRECT: Int = 308

        /** HTTP 400 Bad Request status code. */
        const val BAD_REQUEST: Int = 400

        /** HTTP 401 Unauthorized status code. */
        const val UNAUTHORIZED: Int = 401

        /** HTTP 403 Forbidden status code. */
        const val FORBIDDEN: Int = 403

        /** HTTP 404 Not Found status code. */
        const val NOT_FOUND: Int = 404

        /** HTTP 405 Method Not Allowed status code. */
        const val METHOD_NOT_ALLOWED: Int = 405

        /** HTTP 408 Request Timeout status code. */
        const val REQUEST_TIMEOUT: Int = 408

        /** HTTP 429 Too Many Requests status code. */
        const val TOO_MANY_REQUESTS: Int = 429

        /** HTTP 500 Internal Server Error status code. */
        const val INTERNAL_ERROR: Int = 500

        /** HTTP 502 Bad Gateway status code. */
        const val BAD_GATEWAY: Int = 502

        /** HTTP 503 Service Unavailable status code. */
        const val SERVICE_UNAVAILABLE: Int = 503

        /** HTTP 504 Gateway Timeout status code. */
        const val GATEWAY_TIMEOUT: Int = 504

        /**
         * Returns a list of common HTTP client error status codes (4xx).
         * @return list containing BAD_REQUEST, UNAUTHORIZED, FORBIDDEN, and NOT_FOUND
         */
        fun clientErrors() = listOf(BAD_REQUEST, UNAUTHORIZED, FORBIDDEN, NOT_FOUND)

        /**
         * Returns a list of common HTTP server error status codes (5xx).
         * @return list containing INTERNAL_ERROR, BAD_GATEWAY, and SERVICE_UNAVAILABLE
         */
        fun serverErrors() = listOf(INTERNAL_ERROR, BAD_GATEWAY, SERVICE_UNAVAILABLE)
    }

    /**
     * HTTP content type values and utilities.
     */
    object ContentType {

        /** JSON content type. */
        const val APPLICATION_JSON: String = "application/json"

        /** Plain text content type. */
        const val TEXT_PLAIN: String = "text/plain"

        /** Server-Sent Events content type. */
        const val TEXT_EVENT_STREAM: String = "text/event-stream"

        /** gRPC content type. */
        const val APPLICATION_GRPC: String = "application/grpc"

        /** gRPC with Protocol Buffers content type. */
        const val APPLICATION_GRPC_PROTO: String = "application/grpc+proto"

        /** gRPC with JSON content type. */
        const val APPLICATION_GRPC_JSON: String = "application/grpc+json"

        /**
         * Checks if the given content type represents a streaming protocol.
         * @param contentType the content type to check
         * @return true if the content type is a stream type, false otherwise
         */
        fun isStream(contentType: String?): Boolean {
            return contentType != null && contentType in STREAM_CONTENT_TYPES
        }

        private val STREAM_CONTENT_TYPES = setOf(
            TEXT_EVENT_STREAM,
            APPLICATION_GRPC,
            APPLICATION_GRPC_PROTO,
            APPLICATION_GRPC_JSON
        )
    }
}
