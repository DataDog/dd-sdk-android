/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

/**
 * A local TLS pinning policy for flag assignment requests.
 *
 * A profile contains one exact HTTPS origin, a pin target, and a set of approved public-key pins.
 * Each pin is the SHA-256 hash of a DER-encoded SubjectPublicKeyInfo, in the OkHttp format
 * `sha256/<base64>`. A profile must contain at least two different pins: the active key and a backup key.
 *
 * The SDK validates a profile when the Flags feature is enabled. It reports an invalid profile through the SDK
 * logger and does not throw.
 *
 * A profile never applies to other hosts. The SDK does not use suffix matching or discover trust from a response.
 *
 * Use a bundled profile, such as [DATADOG_STAGING_V1], or create an advanced policy with [custom].
 */
class TlsPinningProfile internal constructor(
    /** The profile name. Custom profiles use the name `custom`. */
    val name: String,
    /** The exact HTTPS origin of the flag assignment endpoint, for example `https://flags.example.com`. */
    val origin: String,
    /** The certificate in the validated chain that the pins identify. */
    val pinTarget: TlsPinTarget,
    /** The approved pins, in the OkHttp format `sha256/<base64>`. */
    val pins: List<String>
) {

    /** @inheritDoc */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TlsPinningProfile) return false
        return name == other.name &&
            origin == other.origin &&
            pinTarget == other.pinTarget &&
            pins == other.pins
    }

    /** @inheritDoc */
    override fun hashCode(): Int {
        var result = name.hashCode()
        result = HASH_MULTIPLIER * result + origin.hashCode()
        result = HASH_MULTIPLIER * result + pinTarget.hashCode()
        result = HASH_MULTIPLIER * result + pins.hashCode()
        return result
    }

    /** @inheritDoc */
    override fun toString(): String =
        "TlsPinningProfile(name=$name, origin=$origin, pinTarget=$pinTarget, pins=$pins)"

    /**
     * Bundled profiles and factory methods for [TlsPinningProfile].
     */
    companion object {

        private const val HASH_MULTIPLIER = 31
        private const val CUSTOM_PROFILE_NAME = "custom"

        /**
         * The Datadog staging profile, version 1. Preview: for staging validation only.
         *
         * The origin is `https://protected.ff-cdn.datad0g.com`. The profile uses [TlsPinTarget.ISSUER] pins, because
         * the CDN manages the leaf certificate and can change its key at each renewal. The pins identify these
         * Certainly certificate authorities:
         * - Certainly Intermediate R1 (RSA)
         * - Certainly Intermediate E1 (EC)
         * - Certainly Root R1 (RSA)
         * - Certainly Root E1 (EC)
         */
        @JvmField
        val DATADOG_STAGING_V1: TlsPinningProfile = TlsPinningProfile(
            name = "datadog-staging-v1",
            // The pins below are the real Certainly CA SPKI hashes. The endpoint is pending provisioning and live
            // validation (ddoghq/terraform-config PR 62220). Do not add the Starfield Root G2 cross-signing root:
            // it trusts far more issuers than this endpoint needs.
            origin = "https://protected.ff-cdn.datad0g.com",
            pinTarget = TlsPinTarget.ISSUER,
            pins = listOf(
                // Certainly Intermediate R1 (RSA), valid to 2032-06-21.
                "sha256/oW7smChMJRcnzTObF7K+HzInReAPTxB/L1h6eZTmw9Q=",
                // Certainly Intermediate E1 (EC); the copy signed by Root E1 is valid to 2031-04-01.
                "sha256/XkxTESlVMF59ZsGRFSBQJyCYjYJYNYkLwv/sqaDnMJA=",
                // Certainly Root R1 (RSA), valid to 2046.
                "sha256/P5Pz/PedIl0hPu9qSj9Yhc+E/j16ejwRVTUXaIwOIQA=",
                // Certainly Root E1 (EC), valid to 2046.
                "sha256/HvZGJdqi5dQz10Sa4xogDRAl4AEqj+z6cJMvi1mbdd0="
            )
        )

        /**
         * Creates an advanced pinning profile for an exact HTTPS origin.
         *
         * The origin must use the `https` scheme and must contain only a host and an optional port. It must not
         * contain a wildcard, user information, a path other than `/`, a query, or a fragment.
         *
         * If you do not call [FlagsConfiguration.Builder.useCustomFlagEndpoint], the SDK sends flag assignment
         * requests to `<origin>/precompute-assignments`. If you call it, the custom endpoint must have the same
         * origin as this profile.
         *
         * The SDK validates the arguments when the Flags feature is enabled, not in this method.
         *
         * @param origin the exact HTTPS origin, for example `https://flags.example.com`.
         * @param pinTarget the certificate that the pins identify.
         * @param pins at least two different pins in the OkHttp format `sha256/<base64>`.
         * @return a new [TlsPinningProfile].
         */
        @JvmStatic
        fun custom(origin: String, pinTarget: TlsPinTarget, pins: List<String>): TlsPinningProfile =
            TlsPinningProfile(
                name = CUSTOM_PROFILE_NAME,
                origin = origin,
                pinTarget = pinTarget,
                pins = pins.toList()
            )
    }
}
