/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.protection

import com.datadog.android.flags.ResponseProtection
import com.datadog.android.flags.ResponseProtectionMode
import com.datadog.android.flags.TlsPinTarget
import com.datadog.android.flags.TlsPinningProfile
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * Exercises the real OkHttp TLS stack with a local CA and two leaf keys, A and B, for `localhost`.
 *
 * The base client trusts the local CA, like the SDK trusts the platform CAs. The tests then apply the resolved
 * policy to that base client, the same way the Flags feature applies it to the shared SDK client.
 */
internal class TlsPinningEnforcementTest {

    private lateinit var rootCertificate: HeldCertificate
    private lateinit var leafA: HeldCertificate
    private lateinit var leafB: HeldCertificate
    private lateinit var baseClient: OkHttpClient
    private val servers = mutableListOf<MockWebServer>()

    @BeforeEach
    fun `set up`() {
        rootCertificate = HeldCertificate.Builder()
            .certificateAuthority(0)
            .commonName("Flags Test Root")
            .build()
        leafA = leafCertificate("leaf-a")
        leafB = leafCertificate("leaf-b")
        val clientCertificates = HandshakeCertificates.Builder()
            .addTrustedCertificate(rootCertificate.certificate)
            .build()
        baseClient = OkHttpClient.Builder()
            .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    @AfterEach
    fun `tear down`() {
        servers.forEach { it.shutdown() }
    }

    @Test
    fun `M admit the response W execute() { leaf pin matches }`() {
        // Given
        val server = startServer(leafA)
        val client = protectedClient(server, pin(leafA), UNRELATED_PIN)

        // When
        val response = client.newCall(request(server)).execute()

        // Then
        assertThat(response.code).isEqualTo(200)
        assertThat(response.body?.string()).isEqualTo(RESPONSE_BODY)
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun `M reject the connection before the request W execute() { wrong pins }`() {
        // Given
        val server = startServer(leafA)
        val client = protectedClient(server, UNRELATED_PIN, OTHER_UNRELATED_PIN)

        // When / Then
        assertThatThrownBy { client.newCall(request(server)).execute() }
            .isInstanceOf(SSLPeerUnverifiedException::class.java)
        assertThat(server.requestCount).isZero()
    }

    @Test
    fun `M reject the connection before the request W execute() { pin matches only the issuer }`() {
        // Given
        val server = startServer(leafA)
        val client = protectedClient(server, pin(rootCertificate), UNRELATED_PIN)

        // When / Then
        assertThatThrownBy { client.newCall(request(server)).execute() }
            .isInstanceOf(ResponseProtectionException::class.java)
            .hasMessage(TlsPinningInterceptor.ERROR_LEAF_PIN_MISMATCH)
        assertThat(server.requestCount).isZero()
    }

    @Test
    fun `M accept key B after a switch from key A W execute() { A+B pins, fresh handshake }`() {
        // Given
        val serverA = startServer(leafA)
        val port = serverA.port
        val client = protectedClient(serverA, pin(leafA), pin(leafB))
        val responseA = client.newCall(request(serverA)).execute()
        responseA.close()
        serverA.shutdown()
        servers.remove(serverA)
        client.connectionPool.evictAll()
        val serverB = startServer(leafB, port)

        // When
        val responseB = client.newCall(request(serverB)).execute()

        // Then
        assertThat(responseA.code).isEqualTo(200)
        assertThat(responseB.code).isEqualTo(200)
        assertThat(serverB.requestCount).isEqualTo(1)
    }

    @Test
    fun `M reject key B W execute() { A-only pins }`() {
        // Given
        val server = startServer(leafB)
        val client = protectedClient(server, pin(leafA), UNRELATED_PIN)

        // When / Then
        assertThatThrownBy { client.newCall(request(server)).execute() }
            .isInstanceOf(SSLPeerUnverifiedException::class.java)
        assertThat(server.requestCount).isZero()
    }

    @Test
    fun `M admit the response W execute() { no protection }`() {
        // Given
        val server = startServer(leafB)
        val client = baseClient.newBuilder()
            .apply { ResolvedResponseProtection.None.configure(this) }
            .build()

        // When
        val response = client.newCall(request(server)).execute()

        // Then
        assertThat(response.code).isEqualTo(200)
    }

    @Test
    fun `M reject the request without a connection W execute() { origin outside the profile }`() {
        // Given
        val server = startServer(leafA)
        val client = protectedClient(server, pin(leafA), pin(leafB))
        val otherOriginRequest = Request.Builder()
            .url(server.url("/").newBuilder().host("127.0.0.1").build())
            .build()

        // When / Then
        assertThatThrownBy { client.newCall(otherOriginRequest).execute() }
            .isInstanceOf(ResponseProtectionException::class.java)
            .hasMessage(TlsPinningInterceptor.ERROR_ORIGIN_OUTSIDE_POLICY)
        assertThat(server.requestCount).isZero()
    }

    @Test
    fun `M reject the redirect W execute() { redirect to an unprotected origin }`() {
        // Given
        val plainServer = MockWebServer().also { servers.add(it) }
        plainServer.start()
        val server = startServer(
            leafA,
            response = MockResponse()
                .setResponseCode(302)
                .setHeader("Location", plainServer.url("/precompute-assignments"))
        )
        val client = protectedClient(server, pin(leafA), pin(leafB))

        // When / Then
        assertThatThrownBy { client.newCall(request(server)).execute() }
            .isInstanceOf(ResponseProtectionException::class.java)
        assertThat(plainServer.requestCount).isZero()
    }

    @Test
    fun `M reject every request without a connection W execute() { invalid policy }`() {
        // Given
        val server = startServer(leafA)
        val resolved = ResponseProtectionResolver.resolve(
            ResponseProtection.tlsPinning(
                ResponseProtectionMode.REQUIRED,
                TlsPinningProfile.custom(origin(server), TlsPinTarget.LEAF, listOf(pin(leafA)))
            ),
            null
        )
        val client = baseClient.newBuilder().apply { resolved.configure(this) }.build()

        // When / Then
        assertThat(resolved).isInstanceOf(ResolvedResponseProtection.Invalid::class.java)
        assertThatThrownBy { client.newCall(request(server)).execute() }
            .isInstanceOf(ResponseProtectionException::class.java)
        assertThat(server.requestCount).isZero()
    }

    @Test
    fun `M keep the base client settings W configure()`() {
        // Given
        val server = startServer(leafA)
        val resolved = resolve(server, pin(leafA), pin(leafB))

        // When
        val client = baseClient.newBuilder().apply { resolved.configure(this) }.build()

        // Then
        assertThat(client.sslSocketFactory).isSameAs(baseClient.sslSocketFactory)
        assertThat(client.connectTimeoutMillis).isEqualTo(baseClient.connectTimeoutMillis)
        assertThat(client.readTimeoutMillis).isEqualTo(baseClient.readTimeoutMillis)
        assertThat(client.dns).isSameAs(baseClient.dns)
        assertThat(client.connectionPool).isSameAs(baseClient.connectionPool)
        assertThat(client.dispatcher).isSameAs(baseClient.dispatcher)
        assertThat(baseClient.certificatePinner.pins).isEmpty()
        assertThat(client.certificatePinner.pins).isNotEmpty()
        assertThat(baseClient.interceptors).isEmpty()
        assertThat(baseClient.networkInterceptors).isEmpty()
    }

    // region Helpers

    private fun leafCertificate(serialName: String): HeldCertificate = HeldCertificate.Builder()
        .commonName(serialName)
        .addSubjectAlternativeName("localhost")
        .signedBy(rootCertificate)
        .build()

    private fun startServer(
        leaf: HeldCertificate,
        port: Int = 0,
        response: MockResponse = MockResponse().setBody(RESPONSE_BODY)
    ): MockWebServer {
        val serverCertificates = HandshakeCertificates.Builder()
            .heldCertificate(leaf, rootCertificate.certificate)
            .build()
        val server = MockWebServer()
        servers.add(server)
        server.useHttps(serverCertificates.sslSocketFactory(), false)
        server.enqueue(response)
        server.start(port)
        return server
    }

    private fun origin(server: MockWebServer) = "https://localhost:${server.port}"

    private fun request(server: MockWebServer): Request = Request.Builder()
        .url("${origin(server)}/precompute-assignments")
        .build()

    private fun resolve(server: MockWebServer, vararg pins: String): ResolvedResponseProtection =
        ResponseProtectionResolver.resolve(
            ResponseProtection.tlsPinning(
                ResponseProtectionMode.REQUIRED,
                TlsPinningProfile.custom(origin(server), TlsPinTarget.LEAF, pins.toList())
            ),
            null
        )

    private fun protectedClient(server: MockWebServer, vararg pins: String): OkHttpClient {
        val resolved = resolve(server, *pins)
        assertThat(resolved).isInstanceOf(ResolvedResponseProtection.TlsPinning::class.java)
        return baseClient.newBuilder().apply { resolved.configure(this) }.build()
    }

    private fun pin(certificate: HeldCertificate): String = CertificatePinner.pin(certificate.certificate)

    // endregion

    companion object {
        private const val TIMEOUT_SECONDS = 10L
        private const val RESPONSE_BODY = "{\"data\":{}}"
        private const val UNRELATED_PIN = "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
        private const val OTHER_UNRELATED_PIN = "sha256/AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="
    }
}
