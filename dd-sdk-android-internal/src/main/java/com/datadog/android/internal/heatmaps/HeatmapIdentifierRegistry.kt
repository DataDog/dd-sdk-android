/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.heatmaps

import com.datadog.tools.annotation.NoOpImplementation

/**
 * Stores and retrieves [HeatmapIdentifier]s keyed by composite view identity key
 * (see [heatmapViewKey]).
 *
 * Implementations must be thread-safe: the write side (Session Replay) may call
 * [setHeatmapIdentifiers] from a background traversal thread while the read side (RUM) calls
 * [getHeatmapIdentifier] from the main thread.
 */
@NoOpImplementation(publicNoOpImplementation = true)
interface HeatmapIdentifierRegistry {

    /**
     * Replaces the current snapshot with [identifiers], scoped to [screenName].
     *
     * @param identifiers a map of view identity keys (see [heatmapViewKey]) to their
     *   [HeatmapIdentifier]s. Keys must be computed with [heatmapViewKey] at the time the
     *   snapshot is captured so they match the keys produced at lookup time.
     * @param screenName the RUM view URL active when the snapshot was computed. Used to
     *   guard against stale reads after screen navigation.
     */
    fun setHeatmapIdentifiers(identifiers: Map<Long, HeatmapIdentifier>, screenName: String)

    /**
     * Returns the [HeatmapIdentifier] for the view with the given [heatmapViewKey], or null if
     * the view is unknown or if [currentScreenName] does not match the screen that produced the
     * current snapshot (indicating the snapshot is stale).
     *
     * @param heatmapViewKey the composite identity key of the tapped view, as returned by [heatmapViewKey].
     *   Must match the key used when populating [setHeatmapIdentifiers].
     * @param currentScreenName the RUM view URL active at the time of the tap.
     */
    fun getHeatmapIdentifier(heatmapViewKey: Long, currentScreenName: String): HeatmapIdentifier?

    companion object {

        /**
         * Creates a default [HeatmapIdentifierRegistry] backed by an in-memory snapshot store.
         */
        fun create(): HeatmapIdentifierRegistry = HeatmapIdentifierStore()
    }
}
