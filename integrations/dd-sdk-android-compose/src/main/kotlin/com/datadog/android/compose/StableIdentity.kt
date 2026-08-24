/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.compose

import androidx.compose.ui.semantics.SemanticsPropertyKey

/**
 * Semantics property reserved for a build-time-constant, per-call-site stable identity for a
 * composable, distinct from [DatadogSemanticsPropertyKey] (which carries the composable's bare
 * function name for action tracking, not a unique identity).
 *
 * Nothing writes this property yet. It is defined ahead of time so that future Datadog Kotlin
 * Compiler Plugin instrumentation has a stable, pre-agreed contract to write into, independent of
 * any single consuming feature (e.g. not named after Session Replay heatmaps specifically), since
 * unlike [androidx.compose.ui.semantics.SemanticsProperties.TestTag] this key is fully Datadog-owned
 * and never collides with identifiers an app sets for its own purposes (e.g. UI testing).
 */
internal val DatadogStableIdSemanticsPropertyKey: SemanticsPropertyKey<String> = SemanticsPropertyKey(
    name = "_dd_stable_id",
    mergePolicy = { parentValue, _ ->
        parentValue
    }
)
