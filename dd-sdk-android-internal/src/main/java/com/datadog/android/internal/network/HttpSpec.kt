/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.network

/**
 * HTTP specification constants and utilities.
 */
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
    object Header {
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

        /**
         * Returns a list of all header name values.
         */
        fun values() = listOf(
            CONTENT_TYPE,
            CONTENT_LENGTH,
            LOCATION,
            RETRY_AFTER,
            WEBSOCKET_ACCEPT_HEADER
        )
    }

    /**
     * Standard HTTP status codes.
     */
    object StatusCode {
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

        /** Network Authentication Required (RFC 6585) status code. */
        const val NETWORK_AUTHENTICATION_REQUIRED: Int = 511

        /**
         * Returns a list of all HTTP status codes.
         */
        fun values() = listOf(
            OK,
            MOVED_PERMANENTLY,
            FOUND,
            SEE_OTHER,
            TEMPORARY_REDIRECT,
            PERMANENT_REDIRECT,
            BAD_REQUEST,
            UNAUTHORIZED,
            FORBIDDEN,
            NOT_FOUND,
            METHOD_NOT_ALLOWED,
            REQUEST_TIMEOUT,
            TOO_MANY_REQUESTS,
            INTERNAL_ERROR,
            BAD_GATEWAY,
            SERVICE_UNAVAILABLE,
            GATEWAY_TIMEOUT,
            NETWORK_AUTHENTICATION_REQUIRED
        )

        /**
         * Returns a list of common HTTP client error status codes (4xx).
         * @return list containing BAD_REQUEST, UNAUTHORIZED, FORBIDDEN, and NOT_FOUND
         */
        fun clientErrors(vararg except: Int): List<Int> {
            val prohibited = except.toSet()
            return values().filterNot { it in prohibited }.filter { it / STATUS_CODE_TYPE == STATUS_CODE_TYPE_CLIENT }
        }

        /**
         * Returns a list of common HTTP server error status codes (5xx).
         * @return list containing INTERNAL_ERROR, BAD_GATEWAY, and SERVICE_UNAVAILABLE
         */
        fun serverErrors() = values().filter { it / STATUS_CODE_TYPE == STATUS_CODE_TYPE_SERVER }

        private const val STATUS_CODE_TYPE = 100
        private const val STATUS_CODE_TYPE_SERVER = 5
        private const val STATUS_CODE_TYPE_CLIENT = 4
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
         * Returns a list of all content type values.
         */
        fun values() = listOf(
            APPLICATION_JSON,
            TEXT_PLAIN,
            TEXT_EVENT_STREAM,
            APPLICATION_GRPC,
            APPLICATION_GRPC_PROTO,
            APPLICATION_GRPC_JSON
        )

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
