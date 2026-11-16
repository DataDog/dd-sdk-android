/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.protection

import com.datadog.android.flags.TlsPinTarget
import okhttp3.CertificatePinner
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * Thrown when a flag assignment request does not satisfy the required response protection.
 *
 * It extends [SSLPeerUnverifiedException] so that OkHttp does not retry the call on another route.
 */
internal class ResponseProtectionException(message: String) : SSLPeerUnverifiedException(message)

/**
 * Enforces the origin scope and the pin target of a [ResolvedResponseProtection.TlsPinning] policy.
 *
 * As an application interceptor, it rejects a request to another origin before OkHttp opens a connection.
 * As a network interceptor, it runs for every hop (including redirects) after the TLS handshake and before OkHttp
 * writes the request. It then requires the same origin and a pinned certificate at the [pinTarget] position.
 *
 * The certificates come from the connection [okhttp3.Handshake]. After a TLS handshake, OkHttp 4 builds that
 * handshake from the chain that its `CertificateChainCleaner` returns for the client trust manager. That is the
 * validated chain from the leaf to the trusted root, not the list that the server sent. On Android, the cleaner uses
 * the platform `X509TrustManagerExtensions.checkServerTrusted` chain. `TlsPinningEnforcementTest` verifies this
 * assumption, so an OkHttp upgrade that changes it fails the tests.
 */
internal class TlsPinningInterceptor(
    private val origin: HttpUrl,
    private val pinTarget: TlsPinTarget,
    private val pins: Set<String>
) : Interceptor {

    // Interceptor.intercept() reports a rejected call by throwing an IOException.
    @Suppress("ThrowingInternalException", "ThrowsCount")
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        if (!url.isHttps || url.host != origin.host || url.port != origin.port) {
            throw ResponseProtectionException(ERROR_ORIGIN_OUTSIDE_POLICY)
        }
        val connection = chain.connection()
        if (connection != null) {
            val validatedChain = connection.handshake()?.peerCertificates.orEmpty()
            if (validatedChain.isEmpty()) {
                throw ResponseProtectionException(ERROR_NO_HANDSHAKE)
            }
            val matches = when (pinTarget) {
                TlsPinTarget.LEAF -> isPinned(validatedChain[0])
                TlsPinTarget.ISSUER -> hasPinnedIssuer(validatedChain)
            }
            if (!matches) {
                throw ResponseProtectionException(
                    if (pinTarget == TlsPinTarget.LEAF) ERROR_LEAF_PIN_MISMATCH else ERROR_ISSUER_PIN_MISMATCH
                )
            }
        }
        @Suppress("UnsafeThirdPartyFunctionCall") // intercept() allows throwing IOException.
        return chain.proceed(request)
    }

    // The leaf (index 0) never satisfies an issuer policy.
    private fun hasPinnedIssuer(validatedChain: List<Certificate>): Boolean {
        for (index in 1 until validatedChain.size) {
            if (isPinned(validatedChain[index])) return true
        }
        return false
    }

    private fun isPinned(certificate: Certificate): Boolean {
        if (certificate !is X509Certificate) return false
        @Suppress("UnsafeThirdPartyFunctionCall") // Safe: the certificate is an X509Certificate.
        val pin = CertificatePinner.pin(certificate)
        return pin in pins
    }

    internal companion object {
        internal const val ERROR_ORIGIN_OUTSIDE_POLICY =
            "Flag assignment request origin is outside the TLS pinning profile"
        internal const val ERROR_NO_HANDSHAKE =
            "Flag assignment connection has no TLS handshake"
        internal const val ERROR_LEAF_PIN_MISMATCH =
            "Flag assignment server certificate does not match the TLS pinning profile"
        internal const val ERROR_ISSUER_PIN_MISMATCH =
            "Flag assignment certificate chain has no issuer that matches the TLS pinning profile"
    }
}

/**
 * Rejects every request. Used when a required response protection policy is invalid.
 */
internal class RejectingInterceptor(private val reason: String) : Interceptor {
    // Interceptor.intercept() reports a rejected call by throwing an IOException.
    @Suppress("ThrowingInternalException")
    override fun intercept(chain: Interceptor.Chain): Response {
        throw ResponseProtectionException("Flag assignment request rejected: invalid response protection ($reason)")
    }
}
