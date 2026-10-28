/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

/** Controls verification of assignment responses. Disabled unless explicitly configured. */
enum class ResponseSigningMode {
    /** Preserves the existing transport and cache behavior without cryptographic work. */
    OFF,

    /** Reports verification outcomes while accepting otherwise valid unsigned assignments. */
    OBSERVE,

    /** Requires valid proofs; verified assignments remain in memory until their original expiry. */
    REQUIRED
}
