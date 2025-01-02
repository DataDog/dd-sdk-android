/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.metric.networksettled

import androidx.annotation.VisibleForTesting
import com.datadog.android.api.InternalLogger
import com.datadog.android.rum.metric.networksettled.InitialResourceIdentifier
import com.datadog.android.rum.metric.networksettled.NetworkSettledResourceContext
import com.datadog.android.rum.metric.networksettled.TimeBasedInitialResourceIdentifier

internal class NetworkSettledMetricResolver(
    private val initialResourceIdentifier: InitialResourceIdentifier = TimeBasedInitialResourceIdentifier(),
    private val internalLogger: InternalLogger
) {
    private val resourceStartedTimestamps = HashSet<String>()

    @Volatile
    private var networkSettleMaxValue: Long? = null

    @Volatile
    private var viewCreatedTimestamp: Long? = null

    @Volatile
    private var lastComputedMetric: Long? = null

    @Volatile
    private var viewWasStopped: Boolean = false

    fun viewWasCreated(eventTimestampInNanos: Long) {
        viewCreatedTimestamp = eventTimestampInNanos
    }

    fun resourceWasStarted(context: InternalResourceContext) {
        if (viewWasStopped) return
        // check if the resource was is a network settled valid resource
        if (initialResourceIdentifier.validate(
                NetworkSettledResourceContext(
                    context.resourceId,
                    context.eventCreatedAtNanos,
                    viewCreatedTimestamp
                )
            )
        ) {
            // check if we have a view created entry for this resource
            resourceStartedTimestamps.add(context.resourceId)
        }
    }

    fun resourceWasStopped(context: InternalResourceContext) {
        if (viewWasStopped) return
        val currentViewCreatedTimestamp = viewCreatedTimestamp
        val currentNetworkSettleMaxValue = networkSettleMaxValue ?: 0L
        val resourceStartedTimestamp = resourceStartedTimestamps.remove(context.resourceId)
        // check if we have a start timestamp for this resource
        if (currentViewCreatedTimestamp != null && resourceStartedTimestamp) {
            val networkToSettledDuration = context.eventCreatedAtNanos - currentViewCreatedTimestamp
            if (networkToSettledDuration > currentNetworkSettleMaxValue) {
                networkSettleMaxValue = networkToSettledDuration
            }
        }
    }

    fun resourceWasDropped(resourceId: String) {
        if (viewWasStopped) return
        resourceStartedTimestamps.remove(resourceId)
    }

    fun viewWasStopped() {
        viewWasStopped = true
        // clear all the resources for this view
        resourceStartedTimestamps.clear()
    }

    fun resolveMetric(): Long? {
        if (viewWasStopped) {
            return lastComputedMetric
        }
        lastComputedMetric = computeMetric()
        return lastComputedMetric
    }

    @VisibleForTesting
    fun getResourceStartedCacheSize(): Int {
        return resourceStartedTimestamps.size
    }

    @Suppress("ReturnCount")
    private fun computeMetric(): Long? {
        if (viewCreatedTimestamp == null) {
            internalLogger.log(
                InternalLogger.Level.DEBUG,
                InternalLogger.Target.MAINTAINER,
                { "[ViewNetworkSettledMetric] There was no view created yet for this resource" }
            )
            return null
        }
        if (resourceStartedTimestamps.size > 0) {
            // not all resources were stopped
            internalLogger.log(
                InternalLogger.Level.DEBUG,
                InternalLogger.Target.MAINTAINER,
                { "[ViewNetworkSettledMetric] Not all the initial resources were stopped for this view" }
            )
            return null
        }
        return networkSettleMaxValue
    }
}
