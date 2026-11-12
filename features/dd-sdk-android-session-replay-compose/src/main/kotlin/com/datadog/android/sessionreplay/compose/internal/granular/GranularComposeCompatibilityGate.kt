/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.granular

/**
 * Monotonic kill switch for one [GranularComposeDecomposer]: once a decomposition attempt hits a
 * [LinkageError] from a Compose UI runtime outside this artifact's tested range (a shifted internal
 * API, not an ordinary bug), that decomposer's hosts fall back to the generic native-View mapper
 * and are never re-attempted, mirroring `LayoutNodeUtils.MethodResolver`'s never-downgrading state
 * in `integrations/dd-sdk-android-compose`. Each SDK core configures its own
 * `ComposeExtensionSupport`, so the gate is scoped to that core. The decomposer propagates the
 * failure to its SDK caller, which owns error reporting and native fallback.
 */
internal class GranularComposeCompatibilityGate {

    @Volatile
    private var incompatible = false

    fun isAvailable(): Boolean = !incompatible

    fun markIncompatible() {
        incompatible = true
    }
}
