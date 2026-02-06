/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.testserver

import io.ktor.network.tls.certificates.buildKeyStore
import io.ktor.network.tls.certificates.saveToFile
import io.ktor.serialization.gson.gson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import java.io.File
import java.security.KeyStore

/**
 * A secure test server supporting HTTPS with HTTP/2 protocol for use in integration tests.
 * This server uses a self-signed certificate suitable for testing purposes.
 *
 * For QUIC/HTTP3 support, clients like Cronet can negotiate HTTP/3 when connecting to
 * an HTTPS endpoint that advertises Alt-Svc header.
 *
 * @param httpsPort The port for HTTPS server (default: 8443)
 */
class SecureTestServer(
    private val httpsPort: Int = DEFAULT_HTTPS_PORT
) {

    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private var keyStoreFile: File? = null

    /**
     * The KeyStore containing the self-signed certificate.
     * Can be used by clients to trust this server's certificate.
     */
    var keyStore: KeyStore? = null
        private set

    /**
     * Starts the HTTPS server with a self-signed certificate.
     *
     * @param wait If true, blocks the current thread until the server stops
     */
    fun start(wait: Boolean = false) {
        // Generate self-signed certificate
        val tempKeyStore = File.createTempFile("testserver", ".jks")
        tempKeyStore.deleteOnExit()
        keyStoreFile = tempKeyStore

        val generatedKeyStore = buildKeyStore {
            certificate(CERTIFICATE_ALIAS) {
                password = KEY_PASSWORD
                domains = listOf("localhost", "127.0.0.1")
            }
        }
        generatedKeyStore.saveToFile(tempKeyStore, KEYSTORE_PASSWORD)
        keyStore = generatedKeyStore

        val environment = applicationEnvironment { }

        server = embeddedServer(
            Netty,
            environment,
            configure = {
                envConfig(generatedKeyStore, tempKeyStore)
            },
            module = { configureServer() }
        ).start(wait = wait)
    }

    private fun ApplicationEngine.Configuration.envConfig(keyStore: KeyStore, keyStoreFile: File) {
        sslConnector(
            keyStore = keyStore,
            keyAlias = CERTIFICATE_ALIAS,
            keyStorePassword = { KEYSTORE_PASSWORD.toCharArray() },
            privateKeyPassword = { KEY_PASSWORD.toCharArray() }
        ) {
            port = httpsPort
            keyStorePath = keyStoreFile
        }
    }

    /**
     * Stops the server.
     *
     * @param gracePeriodMillis The grace period for active requests
     * @param timeoutMillis The timeout for stopping
     */
    fun stop(gracePeriodMillis: Long = 1000, timeoutMillis: Long = 2000) {
        server?.stop(gracePeriodMillis, timeoutMillis)
        server = null
        keyStoreFile?.delete()
        keyStoreFile = null
        keyStore = null
    }

    /**
     * Returns the base URL for HTTPS requests.
     */
    fun httpsUrl(): String = "https://localhost:$httpsPort"

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
        /** Default HTTPS port for the secure test server. */
        const val DEFAULT_HTTPS_PORT = 8443
        private const val CERTIFICATE_ALIAS = "testserver"
        private const val KEYSTORE_PASSWORD = "testserver_password"
        private const val KEY_PASSWORD = "testserver_key_password"
    }
}
