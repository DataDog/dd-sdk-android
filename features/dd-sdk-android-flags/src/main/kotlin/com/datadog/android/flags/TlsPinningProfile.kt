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
    val pins: List<String>,
    internal val isPlaceholder: Boolean
) {

    /** @inheritDoc */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TlsPinningProfile) return false
        return name == other.name &&
            origin == other.origin &&
            pinTarget == other.pinTarget &&
            pins == other.pins &&
            isPlaceholder == other.isPlaceholder
    }

    /** @inheritDoc */
    override fun hashCode(): Int {
        var result = name.hashCode()
        result = HASH_MULTIPLIER * result + origin.hashCode()
        result = HASH_MULTIPLIER * result + pinTarget.hashCode()
        result = HASH_MULTIPLIER * result + pins.hashCode()
        result = HASH_MULTIPLIER * result + isPlaceholder.hashCode()
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
         * The proposed Datadog staging profile, version 1.
         *
         * **Placeholder: do not use in production.** The origin and both pins in this profile are unverified
         * placeholder values. They do not identify a reviewed Datadog trust catalog. The pins do not match any real
         * key, so required pinning with this profile rejects every assignment connection. The SDK logs a warning when
         * the feature is enabled with this profile.
         *
         * Replace the origin and pins with the reviewed staging policy before activation.
         */
        @JvmField
        val DATADOG_STAGING_V1: TlsPinningProfile = TlsPinningProfile(
            name = "datadog-staging-v1",
            // Unverified placeholder: replace with the dedicated, reviewed staging hostname before activation.
            origin = "https://preview.ff-cdn.datad0g.com",
            pinTarget = TlsPinTarget.LEAF,
            // Unverified placeholder pins: replace with the reviewed active and backup leaf SPKI pins.
            pins = listOf(
                "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                "sha256/AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="
            ),
            isPlaceholder = true
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
                pins = pins.toList(),
                isPlaceholder = false
            )
    }
}
