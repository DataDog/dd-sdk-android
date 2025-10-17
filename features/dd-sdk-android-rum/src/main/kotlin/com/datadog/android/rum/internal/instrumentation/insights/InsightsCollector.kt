/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.internal.instrumentation.insights

import com.datadog.android.lint.InternalApi
import com.datadog.tools.annotation.NoOpImplementation

/**
 * Collects performance related events and notifies its listeners at a regular interval.
 */
@InternalApi
@NoOpImplementation
interface InsightsCollector {

    /**
     * Maximum number of events stored in memory.
     */
    var maxSize: Int

    /**
     * Time interval in milliseconds at which the listeners will be notified of data updates.
     */
    var updateIntervalMs: Long

    /**
     * Registers the given [listener] to be notified of data updates.
     *
     * @param listener the listener to register.
     */
    fun addUpdateListener(listener: InsightsUpdatesListener)

    /**
     * Unregisters the given [listener] from data updates notifications.
     *
     * @param listener the listener to unregister.
     */
    fun removeUpdateListener(listener: InsightsUpdatesListener)

    /**
     * Sets a new active view and resets the current view data.
     *
     * @param viewId the unique identifier of the new view.
     * @param name the name of the new view.
     */

    fun onNewView(viewId: String, name: String)

    /**
     * Notifies the collector that a user action has been detected.
     */
    fun onAction()

    /**
     * Notifies the collector that a long task has been detected.
     *
     * @param startedTimestamp the timestamp when the long task started, in nanoseconds.
     * @param durationNs the duration of the long task, in nanoseconds.
     */
    fun onLongTask(startedTimestamp: Long, durationNs: Long)

    /**
     * Notifies the collector that a slow frame has been detected.
     *
     * @param startedTimestamp the timestamp when the slow frame started, in nanoseconds.
     * @param durationNs the duration of the slow frame, in nanoseconds.
     */
    fun onSlowFrame(startedTimestamp: Long, durationNs: Long)

    /**
     * Notifies the collector that a network request has been detected.
     *
     * @param startedTimestamp the timestamp when the network request started, in nanoseconds.
     * @param durationNs the duration of the network request, in nanoseconds.
     */
    fun onNetworkRequest(startedTimestamp: Long, durationNs: Long)

    /**
     * Notifies the collector with the latest CPU vital value.
     *
     * @param cpuTicks the CPU ticks per second value.
     */
    fun onCpuVital(cpuTicks: Double?)

    /**
     * Notifies the collector with the latest Memory vital value.
     *
     * @param memoryValue the memory value in megabytes.
     */
    fun onMemoryVital(memoryValue: Double?)

    /**
     * Notifies the collector with the latest Slow Frame Rate vital value.
     *
     * @param rate the slow frame rate value.
     */
    fun onSlowFrameRate(rate: Double?)
}

/**
 * Listener for data updates from the [InsightsCollector].
 */
@InternalApi
interface InsightsUpdatesListener {

    /**
     * Called when the data has been updated.
     */
    fun onDataUpdated()
}
