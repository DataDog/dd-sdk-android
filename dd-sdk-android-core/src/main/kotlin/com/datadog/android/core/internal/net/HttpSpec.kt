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
    }

    /**
     * Standard HTTP header names.
     */
    object Headers {
        /** Content-Type header name. */
        const val CONTENT_TYPE: String = "Content-Type"

        /** Content-Length header name. */
        const val CONTENT_LENGTH: String = "Content-Length"

        /** Sec-WebSocket-Accept header name. */
        const val WEBSOCKET_ACCEPT_HEADER: String = "Sec-WebSocket-Accept"
    }

    /**
     * HTTP content type values and utilities.
     */
    object ContentType {

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
