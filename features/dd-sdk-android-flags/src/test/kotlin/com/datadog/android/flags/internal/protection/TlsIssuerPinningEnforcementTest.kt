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
import java.net.Socket
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.X509ExtendedKeyManager

/**
 * Exercises [TlsPinTarget.ISSUER] with the real OkHttp TLS stack.
 *
 * The approved PKI is root -> intermediate -> leaf, and the server sends the leaf and the intermediate. A second,
 * also trusted root simulates an interception CA that a user or a device policy installed.
 */
internal class TlsIssuerPinningEnforcementTest {

    private lateinit var root: HeldCertificate
    private lateinit var intermediate: HeldCertificate
    private lateinit var leafA: HeldCertificate
    private lateinit var leafB: HeldCertificate
    private lateinit var interceptionRoot: HeldCertificate
    private lateinit var interceptionLeaf: HeldCertificate
    private lateinit var baseClient: OkHttpClient
    private val servers = mutableListOf<MockWebServer>()

    @BeforeEach
    fun `set up`() {
        root = HeldCertificate.Builder()
            .certificateAuthority(1)
            .commonName("Flags Test Root")
            .build()
        intermediate = HeldCertificate.Builder()
            .certificateAuthority(0)
            .commonName("Flags Test Intermediate")
            .signedBy(root)
            .build()
        leafA = leafCertificate("leaf-a", intermediate)
        leafB = leafCertificate("leaf-b", intermediate)
        interceptionRoot = HeldCertificate.Builder()
            .certificateAuthority(0)
            .commonName("Interception Root")
            .build()
        interceptionLeaf = leafCertificate("intercepted", interceptionRoot)
        val clientCertificates = HandshakeCertificates.Builder()
            .addTrustedCertificate(root.certificate)
            .addTrustedCertificate(interceptionRoot.certificate)
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
    fun `M accept two different leaf keys W execute() { intermediate pin, renewal with a key change }`() {
        // Given
        val serverA = startServer(leafA, intermediate)
        val port = serverA.port
        val client = issuerClient(serverA, pin(intermediate), UNRELATED_PIN)
        val responseA = client.newCall(request(serverA)).execute()
        responseA.close()
        serverA.shutdown()
        servers.remove(serverA)
        client.connectionPool.evictAll()
        val serverB = startServer(leafB, intermediate, port = port)

        // When
        val responseB = client.newCall(request(serverB)).execute()

        // Then
        assertThat(pin(leafA)).isNotEqualTo(pin(leafB))
        assertThat(responseA.code).isEqualTo(200)
        assertThat(responseB.code).isEqualTo(200)
        assertThat(serverB.requestCount).isEqualTo(1)
    }

    @Test
    fun `M accept W execute() { root pin, root not sent by the server }`() {
        // Given
        val server = startServer(leafA, intermediate)
        val client = issuerClient(server, pin(root), UNRELATED_PIN)

        // When
        val response = client.newCall(request(server)).execute()

        // Then
        assertThat(response.code).isEqualTo(200)
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun `M reject before the request W execute() { trusted interception CA, pinned intermediate appended }`() {
        // Given
        val server = startServer(interceptionLeaf, intermediate)
        val client = issuerClient(server, pin(intermediate), pin(root))

        // When / Then
        assertThatThrownBy { client.newCall(request(server)).execute() }
            .isInstanceOf(SSLPeerUnverifiedException::class.java)
        assertThat(server.requestCount).isZero()
    }

    @Test
    fun `M succeed W execute() { trusted interception CA, no protection }`() {
        // Given
        val server = startServer(interceptionLeaf, intermediate)

        // When
        val response = baseClient.newCall(request(server)).execute()

        // Then
        assertThat(response.code).isEqualTo(200)
    }

    @Test
    fun `M reject before the request W execute() { leaf key pin in an issuer policy }`() {
        // Given
        val server = startServer(leafA, intermediate)
        val client = issuerClient(server, pin(leafA), UNRELATED_PIN)

        // When / Then
        assertThatThrownBy { client.newCall(request(server)).execute() }
            .isInstanceOf(ResponseProtectionException::class.java)
            .hasMessage(TlsPinningInterceptor.ERROR_ISSUER_PIN_MISMATCH)
        assertThat(server.requestCount).isZero()
    }

    @Test
    fun `M expose the validated chain W Handshake_peerCertificates { extra certificate sent by the server }`() {
        // Given
        val server = startServer(leafA, intermediate, interceptionRoot)

        // When
        val response = baseClient.newCall(request(server)).execute()

        // Then
        assertThat(response.handshake?.peerCertificates)
            .containsExactly(leafA.certificate, intermediate.certificate, root.certificate)
    }

    // region Helpers

    private fun leafCertificate(name: String, issuer: HeldCertificate): HeldCertificate = HeldCertificate.Builder()
        .commonName(name)
        .addSubjectAlternativeName("localhost")
        .signedBy(issuer)
        .build()

    private fun startServer(
        leaf: HeldCertificate,
        vararg sentChain: HeldCertificate,
        port: Int = 0
    ): MockWebServer {
        // A KeyStore rejects a chain whose certificates do not link, so a key manager sends the chain as given.
        val chain = (listOf(leaf) + sentChain).map { it.certificate }.toTypedArray()
        val keyManager = FixedChainKeyManager(chain, leaf.keyPair.private)
        val sslContext = SSLContext.getInstance("TLS").apply { init(arrayOf(keyManager), null, null) }
        val server = MockWebServer()
        servers.add(server)
        server.useHttps(sslContext.socketFactory, false)
        server.enqueue(MockResponse().setBody(RESPONSE_BODY))
        server.start(port)
        return server
    }

    private fun origin(server: MockWebServer) = "https://localhost:${server.port}"

    private fun request(server: MockWebServer): Request = Request.Builder()
        .url("${origin(server)}/precompute-assignments")
        .build()

    private fun issuerClient(server: MockWebServer, vararg pins: String): OkHttpClient {
        val resolved = ResponseProtectionResolver.resolve(
            ResponseProtection.tlsPinning(
                ResponseProtectionMode.REQUIRED,
                TlsPinningProfile.custom(origin(server), TlsPinTarget.ISSUER, pins.toList())
            ),
            null
        )
        assertThat(resolved).isInstanceOf(ResolvedResponseProtection.TlsPinning::class.java)
        return baseClient.newBuilder().apply { resolved.configure(this) }.build()
    }

    private fun pin(certificate: HeldCertificate): String = CertificatePinner.pin(certificate.certificate)

    private class FixedChainKeyManager(
        private val chain: Array<X509Certificate>,
        private val privateKey: PrivateKey
    ) : X509ExtendedKeyManager() {
        override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?) = ALIAS
        override fun chooseEngineServerAlias(keyType: String?, issuers: Array<out Principal>?, engine: SSLEngine?) =
            ALIAS
        override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?) = arrayOf(ALIAS)
        override fun getCertificateChain(alias: String?) = chain
        override fun getPrivateKey(alias: String?) = privateKey
        override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null
        override fun chooseClientAlias(
            keyType: Array<out String>?,
            issuers: Array<out Principal>?,
            socket: Socket?
        ): String? = null
    }

    // endregion

    companion object {
        private const val TIMEOUT_SECONDS = 10L
        private const val RESPONSE_BODY = "{\"data\":{}}"
        private const val ALIAS = "server"
        private const val UNRELATED_PIN = "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    }
}
