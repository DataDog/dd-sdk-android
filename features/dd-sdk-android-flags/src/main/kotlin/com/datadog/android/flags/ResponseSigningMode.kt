/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

/**
 * Controls verification of assignment responses. Disabled unless explicitly configured.
 *
 * Experimental. Response signing is in preview and works only on the Datadog staging site. The API can change or be
 * removed in a minor release, based on customer feedback.
 */
@ExperimentalFlagsApi
enum class ResponseSigningMode {
    /** Preserves the existing transport and cache behavior without cryptographic work. */
    OFF,

    /**
     * Reports verification outcomes while accepting otherwise valid unsigned assignments. Its persisted assignments are
     * scoped to the request, so a new context, token or environment can start without them.
     */
    OBSERVE,

    /**
     * Requires valid proofs. Verified assignments stay in memory only. At proof expiry the client becomes stale and keeps
     * serving them, unless [FlagsConfiguration.Builder.expireVerifiedFlags] is on.
     */
    REQUIRED
}
