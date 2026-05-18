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
     * Replaces the current snapshot with [identifiers], scoped to [screenName].
     *
     * @param identifiers a map of [System.identityHashCode] values to their [HeatmapIdentifier]s,
     *   computed during the most recent Session Replay view tree traversal.
     * @param screenName the RUM view URL active when the snapshot was computed. Used to
     *   guard against stale reads after screen navigation.
     */
    fun setHeatmapIdentifiers(identifiers: Map<Long, HeatmapIdentifier>, screenName: String)

    /**
     * Returns the [HeatmapIdentifier] for the view with the given [viewIdentityHash], or null if
     * the view is unknown or if [currentScreenName] does not match the screen that produced the
     * current snapshot (indicating the snapshot is stale).
     *
     * @param viewIdentityHash the identity hash of the tapped view (`System.identityHashCode(view).toLong()`).
     * @param currentScreenName the RUM view URL active at the time of the tap.
     */
    fun getHeatmapIdentifier(viewIdentityHash: Long, currentScreenName: String): HeatmapIdentifier?

    companion object {

        /**
         * Creates a default [HeatmapIdentifierRegistry] backed by an in-memory snapshot store.
         */
        fun create(): HeatmapIdentifierRegistry = HeatmapIdentifierStore()
    }
}
