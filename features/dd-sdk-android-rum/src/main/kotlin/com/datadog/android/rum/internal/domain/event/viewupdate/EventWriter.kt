/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event.viewupdate

/**
 * Simple interface for writing view events.
 * This abstraction allows ViewEventTracker to be tested independently
 * of the full SDK event writing infrastructure.
 *
 * Implementations should handle serialization and persistence of events.
 */
internal interface EventWriter {
    /**
     * Writes an event to storage/upload queue.
     *
     * @param event Event data including "type" field ("view" or "view_update")
     * @return true if event was written successfully, false otherwise
     */
    fun write(event: Map<String, Any?>): Boolean
}
