/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

import com.datadog.android.flags.internal.protection.TlsPinningResponseProtection

/**
 * Describes how the Flags feature protects the flag assignment responses that it admits.
 *
 * Set it with [FlagsConfiguration.Builder.responseProtection]. When no response protection is configured,
 * the Flags feature uses the ordinary SDK transport and admission behavior.
 *
 * Each value selects one explicit protection method. The server cannot select a weaker method, endpoint, or
 * trust policy, and the SDK never falls back from one method to another.
 *
 * Create a value with [tlsPinning]. Future SDK versions can add other methods, such as response signatures.
 */
abstract class ResponseProtection internal constructor() {

    /**
     * The enforcement mode of this response protection.
     */
    abstract val mode: ResponseProtectionMode

    /**
     * Factory methods for [ResponseProtection].
     */
    companion object {

        /**
         * Requires TLS public-key pinning on the flag assignment connection.
         *
         * The SDK applies normal TLS validation (chain, hostname, and validity period). It then requires the
         * certificate selected by the [TlsPinningProfile.pinTarget] of [profile] to match one of the profile pins.
         * The SDK rejects the connection before it sends the HTTP request if the check fails.
         *
         * The SDK sends assignment requests only to the profile origin. A request to any other origin fails.
         * The SDK does not load flag assignments that were persisted without this exact policy.
         *
         * This protection applies only to flag assignment requests. Other SDK uploads, such as RUM, Logs,
         * exposures, and evaluations, keep their current transport.
         *
         * If the profile is not valid, the SDK logs an error when the feature is enabled. It does not fetch flag
         * assignments, and it does not fall back to an unprotected connection.
         *
         * @param mode the enforcement mode. Only [ResponseProtectionMode.REQUIRED] is available.
         * @param profile the pinning profile that defines the endpoint and the approved public keys.
         * @return a [ResponseProtection] to pass to [FlagsConfiguration.Builder.responseProtection].
         */
        @JvmStatic
        fun tlsPinning(mode: ResponseProtectionMode, profile: TlsPinningProfile): ResponseProtection =
            TlsPinningResponseProtection(mode, profile)
    }
}
