/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.granular

/**
 * Monotonic, process-lifetime kill switch for [GranularComposeDecomposer]: once a decomposition
 * attempt fails against the running Compose UI runtime (a version outside this artifact's tested
 * range - a [LinkageError]/[NoSuchMethodError] from a shifted internal API, not an ordinary bug),
 * every host falls back to the generic native-View mapper for the rest of the process, matching
 * production behavior from before this decomposer existed. Never re-attempted, mirroring
 * `LayoutNodeUtils.MethodResolver`'s escalating, never-downgrading state in
 * `integrations/dd-sdk-android-compose` - the only existing precedent for this shape in this
 * codebase. The decomposer propagates the first failure to its SDK caller, which owns error
 * reporting and native fallback; subsequent calls return unavailable without retrying.
 */
internal class GranularComposeCompatibilityGate {

    @Volatile
    private var incompatible = false

    fun isAvailable(): Boolean = !incompatible

    fun markIncompatible() {
        incompatible = true
    }

    internal companion object {
        internal val SHARED = GranularComposeCompatibilityGate()
    }
}
