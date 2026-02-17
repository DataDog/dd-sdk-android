/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event.viewupdate

import com.datadog.android.rum.RumConfiguration

/**
 * Manages view event tracking and implements partial view update logic.
 *
 * This class is responsible for:
 * - Storing the last sent event data per view
 * - Tracking document version counters per view
 * - Determining when to send full view vs partial view_update events
 *
 * The implementation is split across phases:
 * - Phase 1: Data structures and skeleton (current)
 * - Phase 2: Integration with diff computation
 * - Phase 3: Complete event flow and integration
 *
 * @param config The RUM configuration containing feature flags
 */
internal class ViewEventTracker(
    private val config: RumConfiguration
) {

    /**
     * Stores the last sent event data for each active view.
     * Key: view.id
     * Value: Complete event data as sent (for diff computation)
     *
     * Memory is freed when view ends via [onViewEnded].
     */
    private val lastSentEvents: MutableMap<String, Map<String, Any?>> = mutableMapOf()

    /**
     * Tracks document version counter per view.
     * Key: view.id
     * Value: Last used document_version (starts at 0, increments before each send)
     *
     * Version counter is per-view and starts at 1 for the first event.
     */
    private val documentVersions: MutableMap<String, Int> = mutableMapOf()

    /**
     * Sends a view event or view_update based on configuration and view state.
     *
     * Decision logic:
     * - If feature disabled → send full view event
     * - If first event for this view.id → send full view event
     * - If subsequent update and feature enabled → send view_update with changes
     *
     * @param viewId The unique identifier for this view
     * @param currentViewData Complete current view state
     *
     * TODO(Phase 3): Implement event sending logic
     */
    fun sendViewUpdate(viewId: String, currentViewData: Map<String, Any?>) {
        TODO("Phase 3: Implement event sending logic")
    }

    /**
     * Sends a full view event.
     * Used for first event or when feature is disabled.
     *
     * @param viewId The unique identifier for this view
     * @param viewData Complete view state to send
     *
     * TODO(Phase 3): Implement full view event sending
     */
    private fun sendFullViewEvent(viewId: String, viewData: Map<String, Any?>) {
        TODO("Phase 3: Implement full view event sending")
    }

    /**
     * Sends a partial view_update event with only changed fields.
     *
     * @param viewId The unique identifier for this view
     * @param changes Map of changed fields from diff computation
     * @param fullCurrentState Complete current state (stored for next diff)
     *
     * TODO(Phase 3): Implement partial view_update sending
     */
    private fun sendPartialViewUpdate(
        viewId: String,
        changes: Map<String, Any?>,
        fullCurrentState: Map<String, Any?>
    ) {
        TODO("Phase 3: Implement partial view_update sending")
    }

    /**
     * Cleanup when view ends.
     * Frees memory by removing stored state.
     *
     * @param viewId The view identifier to clean up
     */
    fun onViewEnded(viewId: String) {
        lastSentEvents.remove(viewId)
        documentVersions.remove(viewId)
    }

    /**
     * Cleanup when SDK shuts down.
     * Removes all stored state for all views.
     */
    fun onSdkShutdown() {
        lastSentEvents.clear()
        documentVersions.clear()
    }

    /**
     * Returns true if partial view updates feature is enabled in configuration.
     */
    private fun isPartialUpdatesEnabled(): Boolean {
        return config.featureConfiguration.enablePartialViewUpdates
    }

    /**
     * Returns true if this is the first event for the given view.
     * A view is considered "first" if no last sent event exists for it.
     */
    internal fun isFirstEvent(viewId: String): Boolean {
        return viewId !in lastSentEvents
    }

    /**
     * For testing: Get current document version for a view.
     * Returns null if no version has been assigned yet.
     */
    internal fun getDocumentVersion(viewId: String): Int? {
        return documentVersions[viewId]
    }

    /**
     * For testing: Check if we have stored last sent event for a view.
     */
    internal fun hasLastSentEvent(viewId: String): Boolean {
        return viewId in lastSentEvents
    }
}
