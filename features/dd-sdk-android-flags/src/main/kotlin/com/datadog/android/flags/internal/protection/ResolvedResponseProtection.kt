/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.protection

import com.datadog.android.flags.TlsPinTarget
import okhttp3.CertificatePinner
import okhttp3.HttpUrl
import okhttp3.OkHttpClient

/**
 * The response protection after validation at feature enable time.
 *
 * Each value configures the flag assignment [OkHttpClient.Builder] and the persistence scope.
 */
internal sealed interface ResolvedResponseProtection {

    /**
     * A stable identifier of the protection policy, or null when no protection is configured.
     * Persisted assignments are only read back under the same scope.
     */
    val persistenceScope: String?

    /**
     * The assignment endpoint that the policy selects, or null to keep the default endpoint selection.
     */
    val assignmentEndpoint: String?

    /**
     * Applies the policy to the flag assignment client. The builder keeps the shared SDK settings.
     */
    fun configure(builder: OkHttpClient.Builder)

    /** No response protection: today's transport and admission behavior. */
    object None : ResolvedResponseProtection {
        override val persistenceScope: String? = null
        override val assignmentEndpoint: String? = null
        override fun configure(builder: OkHttpClient.Builder) = Unit
    }

    /**
     * Required TLS public-key pinning for one exact HTTPS origin.
     *
     * OkHttp's [CertificatePinner] accepts a pin that matches any certificate of the cleaned (validated) chain. It
     * rejects the handshake before any HTTP data is sent. The [TlsPinningInterceptor] network interceptor then applies
     * the [pinTarget]: the leaf for [TlsPinTarget.LEAF], or a certificate other than the leaf for
     * [TlsPinTarget.ISSUER]. Both checks run before OkHttp writes the request headers.
     */
    class TlsPinning(
        val profileName: String,
        val origin: HttpUrl,
        val pinTarget: TlsPinTarget,
        val pins: Set<String>,
        val certificatePinner: CertificatePinner,
        override val assignmentEndpoint: String,
        override val persistenceScope: String
    ) : ResolvedResponseProtection {
        override fun configure(builder: OkHttpClient.Builder) {
            val interceptor = TlsPinningInterceptor(origin, pinTarget, pins)
            @Suppress("UnsafeThirdPartyFunctionCall") // Safe: the interceptors are not null.
            builder
                .certificatePinner(certificatePinner)
                .addInterceptor(interceptor)
                .addNetworkInterceptor(interceptor)
        }
    }

    /**
     * Required protection with a policy that failed validation. Every assignment request fails without a connection.
     */
    class Invalid(val reason: String) : ResolvedResponseProtection {
        override val persistenceScope: String = INVALID_PERSISTENCE_SCOPE
        override val assignmentEndpoint: String? = null
        override fun configure(builder: OkHttpClient.Builder) {
            @Suppress("UnsafeThirdPartyFunctionCall") // Safe: the interceptor is not null.
            builder.addInterceptor(RejectingInterceptor(reason))
        }
    }

    companion object {
        internal const val INVALID_PERSISTENCE_SCOPE = "invalid"
    }
}
