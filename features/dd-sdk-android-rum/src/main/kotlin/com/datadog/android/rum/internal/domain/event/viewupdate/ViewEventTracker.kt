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
 * - Computing diffs and building minimal view_update events
 *
 * @param config The RUM configuration containing feature flags
 * @param writer The event writer for persisting events
 * @param diffComputer The diff computation engine (defaults to new instance)
 */
internal class ViewEventTracker(
    private val config: RumConfiguration,
    private val writer: EventWriter,
    private val diffComputer: ViewDiffComputer = ViewDiffComputer()
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
     */
    fun sendViewUpdate(viewId: String, currentViewData: Map<String, Any?>) {
        if (!isPartialUpdatesEnabled()) {
            // Feature disabled: use legacy behavior (full view events)
            sendFullViewEvent(viewId, currentViewData)
            return
        }

        val lastSent = lastSentEvents[viewId]
        if (lastSent == null) {
            // First event: send full view
            sendFullViewEvent(viewId, currentViewData)
            return
        }

        // Compute diff
        val changes = diffComputer.computeDiff(lastSent, currentViewData)

        if (changes.isEmpty()) {
            // No changes: skip sending event
            return
        }

        // Send view_update with only changed fields
        sendPartialViewUpdate(viewId, changes, currentViewData)
    }

    /**
     * Sends a full view event.
     * Used for first event or when feature is disabled.
     *
     * @param viewId The unique identifier for this view
     * @param viewData Complete view state to send
     */
    private fun sendFullViewEvent(viewId: String, viewData: Map<String, Any?>) {
        val version = incrementDocumentVersion(viewId)

        // Build event with type and version
        val event = viewData.toMutableMap().apply {
            put("type", "view")
            // Merge or add _dd section
            @Suppress("UNCHECKED_CAST")
            val existingDd = this["_dd"] as? Map<String, Any?>
            val updatedDd = mutableMapOf<String, Any?>().apply {
                if (existingDd != null) {
                    putAll(existingDd)
                }
                put("document_version", version)
            }
            put("_dd", updatedDd)
        }

        // Write to storage
        writer.write(event)

        // Store for next diff
        lastSentEvents[viewId] = viewData
    }

    /**
     * Increments and returns the document version for a view.
     * Version starts at 1 for first event.
     *
     * @param viewId The view identifier
     * @return The new document version
     */
    private fun incrementDocumentVersion(viewId: String): Int {
        val currentVersion = documentVersions.getOrDefault(viewId, 0)
        val newVersion = currentVersion + 1
        documentVersions[viewId] = newVersion
        return newVersion
    }

    /**
     * Sends a partial view_update event with only changed fields.
     *
     * The event includes:
     * - Required fields: application.id, session.id, view.id
     * - Changed fields from diff
     * - type="view_update"
     * - _dd.document_version
     *
     * @param viewId The unique identifier for this view
     * @param changes Map of changed fields from diff computation
     * @param fullCurrentState Complete current state (stored for next diff)
     */
    private fun sendPartialViewUpdate(
        viewId: String,
        changes: Map<String, Any?>,
        fullCurrentState: Map<String, Any?>
    ) {
        val version = incrementDocumentVersion(viewId)

        // Build event starting with changed fields
        val event = changes.toMutableMap().apply {
            // Ensure required fields are present (if not in changes)
            if (!containsKey("application")) {
                put("application", fullCurrentState["application"])
            }
            if (!containsKey("session")) {
                put("session", fullCurrentState["session"])
            }
            if (!containsKey("view") || (this["view"] as? Map<*, *>)?.containsKey("id") != true) {
                // Merge view.id if needed
                @Suppress("UNCHECKED_CAST")
                val currentView = this["view"] as? MutableMap<String, Any?> ?: mutableMapOf()
                @Suppress("UNCHECKED_CAST")
                val fullView = fullCurrentState["view"] as? Map<String, Any?>
                if (fullView != null && !currentView.containsKey("id")) {
                    currentView["id"] = fullView["id"]
                }
                put("view", currentView)
            }

            // Set event type
            put("type", "view_update")

            // Merge or add _dd section with document_version
            @Suppress("UNCHECKED_CAST")
            val existingDd = this["_dd"] as? Map<String, Any?>
            val updatedDd = mutableMapOf<String, Any?>().apply {
                if (existingDd != null) {
                    putAll(existingDd)
                }
                put("document_version", version)
            }
            put("_dd", updatedDd)
        }

        // Write to storage
        writer.write(event)

        // Store full current state for next diff
        lastSentEvents[viewId] = fullCurrentState
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
