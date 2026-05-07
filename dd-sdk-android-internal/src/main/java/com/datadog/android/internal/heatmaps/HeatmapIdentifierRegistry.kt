/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.heatmaps

import com.datadog.tools.annotation.NoOpImplementation

/**
 * Stores and retrieves [HeatmapIdentifier]s keyed by view identity.
 */
@NoOpImplementation(publicNoOpImplementation = true)
interface HeatmapIdentifierRegistry {

    /**
     * Replaces the current set of identifiers with [identifiers].
     */
    fun setHeatmapIdentifiers(identifiers: Map<Long, HeatmapIdentifier>)

    /**
     * Returns the [HeatmapIdentifier] for the view with the given [viewId], or null if unknown.
     */
    fun heatmapIdentifier(viewId: Long): HeatmapIdentifier?

    companion object {

        /**
         * Creates a default [HeatmapIdentifierRegistry] backed by an in-memory snapshot store.
         */
        fun create(): HeatmapIdentifierRegistry = HeatmapIdentifierStore()
    }
}
