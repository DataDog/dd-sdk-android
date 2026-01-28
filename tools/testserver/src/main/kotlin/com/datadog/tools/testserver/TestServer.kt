/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.testserver

import io.ktor.serialization.gson.gson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing

/**
 * A simple test server supporting HTTP protocol for use in integration tests.
 *
 * Provides endpoints for all HTTP methods (GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS),
 * redirect endpoints, and error endpoints with configurable status codes.
 *
 * @param httpPort The port for HTTP server (default: 8080)
 */
class TestServer(
    private val httpPort: Int = DEFAULT_HTTP_PORT
) {

    private var httpServer: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

    /**
     * Starts the HTTP server.
     *
     * @param wait If true, blocks the current thread until the server stops
     */
    fun start(wait: Boolean = false) {
        httpServer = embeddedServer(Netty, port = httpPort) {
            configureServer()
        }.start(wait = wait)
    }

    /**
     * Stops the server.
     *
     * @param gracePeriodMillis The grace period for active requests
     * @param timeoutMillis The timeout for stopping
     */
    fun stop(gracePeriodMillis: Long = 1000, timeoutMillis: Long = 2000) {
        httpServer?.stop(gracePeriodMillis, timeoutMillis)
        httpServer = null
    }

    /**
     * Returns the base URL for HTTP requests.
     */
    fun httpUrl(): String = "http://localhost:$httpPort"

    private fun Application.configureServer() {
        install(ContentNegotiation) {
            gson {
                setPrettyPrinting()
            }
        }

        routing {
            // Basic method endpoints
            configureMethodEndpoints()

            // Redirect endpoints
            configureRedirectEndpoints()

            // Error endpoints
            configureErrorEndpoints()
        }
    }

    companion object {
        /** Default HTTP port for the test server. */
        const val DEFAULT_HTTP_PORT = 8080
    }
}
