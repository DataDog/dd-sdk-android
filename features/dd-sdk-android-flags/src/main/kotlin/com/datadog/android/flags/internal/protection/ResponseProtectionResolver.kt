/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.protection

import com.datadog.android.flags.ResponseProtection
import com.datadog.android.flags.ResponseProtectionMode
import com.datadog.android.flags.TlsPinTarget
import com.datadog.android.flags.internal.FLAGS_PATH
import okhttp3.CertificatePinner
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.encodeUtf8

/**
 * Validates a public [ResponseProtection] and resolves it into a [ResolvedResponseProtection].
 *
 * Validation never throws. An invalid required policy resolves to [ResolvedResponseProtection.Invalid].
 */
internal object ResponseProtectionResolver {

    internal const val MIN_PINS = 2
    private const val PIN_PREFIX = "sha256/"
    private const val SHA256_BYTES = 32
    private const val SCOPE_HEX_LENGTH = 16
    private const val SCOPE_FORMAT_VERSION = "tls-pinning-v1"

    fun resolve(protection: ResponseProtection?, customFlagEndpoint: String?): ResolvedResponseProtection =
        when (protection) {
            null -> ResolvedResponseProtection.None
            is TlsPinningResponseProtection -> resolveTlsPinning(protection, customFlagEndpoint)
            else -> ResolvedResponseProtection.Invalid("unsupported response protection method")
        }

    @Suppress("ReturnCount")
    private fun resolveTlsPinning(
        protection: TlsPinningResponseProtection,
        customFlagEndpoint: String?
    ): ResolvedResponseProtection {
        val profile = protection.profile
        if (protection.mode != ResponseProtectionMode.REQUIRED) return invalid("mode must be REQUIRED")
        if (profile.pinTarget != TlsPinTarget.LEAF) return invalid("pin target must be LEAF")

        val origin = parseOrigin(profile.origin) ?: return invalid("origin must be an exact HTTPS origin")

        // Java callers can supply null entries despite the Kotlin list type.
        val inputPins: List<String?> = profile.pins
        // Canonical pins match the CertificatePinner.pin() format that the leaf check compares with.
        val canonicalPins = inputPins.map(::canonicalPin)
        if (canonicalPins.any { it == null }) {
            return invalid("each pin must be sha256/<base64 of a 32-byte SHA-256 hash>")
        }
        val pins = canonicalPins.filterNotNull().toSortedSet()
        if (pins.size < MIN_PINS) return invalid("at least $MIN_PINS different pins are required")

        val endpoint = if (customFlagEndpoint == null) {
            "${origin.toString().trimEnd('/')}$FLAGS_PATH"
        } else {
            val endpointUrl = customFlagEndpoint.toHttpUrlOrNull()
            if (endpointUrl == null || !endpointUrl.hasSameOrigin(origin)) {
                return invalid("custom flag endpoint must use the profile origin")
            }
            customFlagEndpoint
        }

        val certificatePinner = try {
            @Suppress("SpreadOperator") // Runs once per feature enable, with a few pins.
            CertificatePinner.Builder().add(origin.host, *pins.toTypedArray()).build()
        } catch (@Suppress("SwallowedException") e: IllegalArgumentException) {
            return invalid("pins are not accepted by the TLS client")
        }

        return ResolvedResponseProtection.TlsPinning(
            profileName = profile.name,
            origin = origin,
            pins = pins,
            certificatePinner = certificatePinner,
            isPlaceholder = profile.isPlaceholder,
            assignmentEndpoint = endpoint,
            persistenceScope = persistenceScope(origin, pins)
        )
    }

    private fun invalid(reason: String) = ResolvedResponseProtection.Invalid(reason)

    @Suppress("ComplexCondition", "ReturnCount")
    private fun parseOrigin(origin: String): HttpUrl? {
        val url = origin.toHttpUrlOrNull() ?: return null
        if (!url.isHttps ||
            url.host.contains('*') ||
            url.username.isNotEmpty() ||
            url.password.isNotEmpty() ||
            url.encodedPath != "/" ||
            url.query != null ||
            url.fragment != null
        ) {
            return null
        }
        return url
    }

    private fun HttpUrl.hasSameOrigin(other: HttpUrl): Boolean =
        isHttps && host == other.host && port == other.port

    private fun canonicalPin(pin: String?): String? {
        if (pin == null || !pin.startsWith(PIN_PREFIX)) return null
        @Suppress("UnsafeThirdPartyFunctionCall") // Safe: the pin starts with the prefix.
        val hash = pin.substring(PIN_PREFIX.length).decodeBase64()
        return if (hash != null && hash.size == SHA256_BYTES) PIN_PREFIX + hash.base64() else null
    }

    private fun persistenceScope(origin: HttpUrl, pins: Set<String>): String {
        val canonical = listOf(
            SCOPE_FORMAT_VERSION,
            TlsPinTarget.LEAF.name,
            "${origin.host}:${origin.port}",
            pins.sorted().joinToString(",")
        ).joinToString("|")
        @Suppress("UnsafeThirdPartyFunctionCall") // Safe: the length is positive.
        return "tls-" + canonical.encodeUtf8().sha256().hex().take(SCOPE_HEX_LENGTH)
    }
}
