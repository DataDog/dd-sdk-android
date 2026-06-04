/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.internal.heatmaps.HeatmapIdentifier
import com.datadog.android.internal.heatmaps.HeatmapIdentifierRegistry
import com.datadog.android.internal.heatmaps.HeatmapIdentifierRegistryProvider

/**
 * Session Replay may be initialized before RUM — the SDK does not mandate a registration order.
 * This wrapper defers the `getFeature(RUM)` lookup until the first heatmap operation, and caches
 * the result once resolved so the lookup cost is paid at most once.
 */
internal class DeferredHeatmapIdentifierRegistry(
    private val sdkCore: FeatureSdkCore
) : HeatmapIdentifierRegistry {

    @Volatile private var resolved: Resolved? = null

    private fun getDelegate(): HeatmapIdentifierRegistry? {
        if (resolved == null) {
            val rumFeatureScope = sdkCore.getFeature(Feature.RUM_FEATURE_NAME)
            if (rumFeatureScope != null) {
                val registry = (rumFeatureScope.unwrap<Feature>() as? HeatmapIdentifierRegistryProvider)
                    ?.heatmapIdentifierRegistry
                if (registry == null) {
                    sdkCore.internalLogger.log(
                        InternalLogger.Level.WARN,
                        listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                        { RUM_FEATURE_NOT_A_REGISTRY_PROVIDER }
                    )
                }
                resolved = Resolved(registry)
            }
        }
        return resolved?.registry
    }

    override fun setHeatmapIdentifiers(identifiers: Map<Long, HeatmapIdentifier>, screenName: String) {
        getDelegate()?.setHeatmapIdentifiers(identifiers, screenName)
    }

    override fun getHeatmapIdentifier(heatmapViewKey: Long, currentScreenName: String): HeatmapIdentifier? {
        return getDelegate()?.getHeatmapIdentifier(heatmapViewKey, currentScreenName)
    }

    private class Resolved(val registry: HeatmapIdentifierRegistry?)

    companion object {
        const val RUM_FEATURE_NOT_A_REGISTRY_PROVIDER =
            "RUM feature is registered but does not implement HeatmapIdentifierRegistryProvider; " +
                "heatmap identifiers will not be correlated with RUM actions."
    }
}
