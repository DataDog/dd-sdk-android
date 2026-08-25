/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.heatmaps.HeatmapIdentifierRegistryProvider
import com.datadog.android.internal.heatmaps.HeatmapIdentifier
import com.datadog.android.internal.heatmaps.HeatmapIdentifierRegistry

/**
 * Session Replay may be initialized before RUM — the SDK does not mandate a registration order.
 * This wrapper defers the `getFeature(RUM)` lookup to the first heatmap operation and caches
 * the result once resolved so the lookup cost is paid at most once.
 *
 * Each recorded window drives its snapshot traversal on its own Looper thread, so [delegate] can
 * be entered concurrently from multiple threads. The cached resolution is `@Volatile` so a
 * resolution made on one thread is visible to the others; a redundant [FeatureSdkCore.getFeature]
 * lookup racing on first use is harmless since the result is deterministic.
 */
internal class LazyHeatmapIdentifierRegistry(
    private val sdkCore: FeatureSdkCore
) : HeatmapIdentifierRegistry {

    // null = not yet attempted; UNAVAILABLE = permanently failed; anything else = real registry
    @Volatile
    private var resolved: HeatmapIdentifierRegistry? = null

    private fun delegate(): HeatmapIdentifierRegistry? {
        if (resolved == null) {
            val scope = sdkCore.getFeature(Feature.RUM_FEATURE_NAME)
            if (scope != null) {
                val registry = (scope.unwrap<Feature>() as? HeatmapIdentifierRegistryProvider)
                    ?.heatmapIdentifierRegistry
                if (registry != null) {
                    resolved = registry
                } else {
                    sdkCore.internalLogger.log(
                        InternalLogger.Level.WARN,
                        listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                        { RUM_FEATURE_NOT_A_REGISTRY_PROVIDER }
                    )
                    resolved = UNAVAILABLE
                }
            }
        }
        return resolved.takeUnless { it === UNAVAILABLE }
    }

    override fun setHeatmapIdentifiers(identifiers: Map<Long, HeatmapIdentifier>, screenName: String) {
        delegate()?.setHeatmapIdentifiers(identifiers, screenName)
    }

    override fun getHeatmapIdentifier(heatmapViewKey: Long, currentScreenName: String): HeatmapIdentifier? {
        return delegate()?.getHeatmapIdentifier(heatmapViewKey, currentScreenName)
    }

    companion object {
        // Sentinel for a permanent failure (RUM registered but not a HeatmapIdentifierRegistryProvider).
        // Won't change without an SDK restart, so we stop retrying once set.
        private val UNAVAILABLE: HeatmapIdentifierRegistry = NoOpHeatmapIdentifierRegistry()

        const val RUM_FEATURE_NOT_A_REGISTRY_PROVIDER =
            "RUM feature is registered but does not implement HeatmapIdentifierRegistryProvider; " +
                "heatmap identifiers will not be correlated with RUM actions."
    }
}

private class NoOpHeatmapIdentifierRegistry : HeatmapIdentifierRegistry {
    override fun setHeatmapIdentifiers(identifiers: Map<Long, HeatmapIdentifier>, screenName: String) = Unit
    override fun getHeatmapIdentifier(heatmapViewKey: Long, currentScreenName: String): HeatmapIdentifier? = null
}
