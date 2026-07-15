/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.heatmaps

import com.datadog.android.internal.heatmaps.HeatmapIdentifierRegistry

/**
 * Implemented by SDK features that own a [HeatmapIdentifierRegistry], allowing peer features
 * to obtain a typed reference via [com.datadog.android.api.feature.FeatureScope.unwrap].
 */
interface HeatmapIdentifierRegistryProvider {
    /**
     * The registry that maps view identity keys to their stable [HeatmapIdentifier]s for this
     * feature's current screen. Session Replay writes identifiers into this registry during
     * each traversal; the RUM layer reads from it when a tap action is sent.
     */
    val heatmapIdentifierRegistry: HeatmapIdentifierRegistry
}
