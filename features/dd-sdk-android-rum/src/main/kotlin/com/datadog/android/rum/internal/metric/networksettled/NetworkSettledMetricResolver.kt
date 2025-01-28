/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.metric.networksettled

import com.datadog.android.api.InternalLogger
import com.datadog.android.rum.internal.metric.NoValueReason
import com.datadog.android.rum.internal.metric.ViewInitializationMetricsConfig
import com.datadog.android.rum.internal.metric.ViewInitializationMetricsState
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

    private var currentViewDiagnostic = Diagnostic()

    fun viewWasCreated(eventTimestampInNanos: Long) {
        viewCreatedTimestamp = eventTimestampInNanos
        currentViewDiagnostic = Diagnostic()
    }

    fun resourceWasStarted(context: InternalResourceContext) {
        if (viewWasStopped) return
        currentViewDiagnostic.started += 1
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
            currentViewDiagnostic.initial += 1
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
            currentViewDiagnostic.stopped += 1
            val networkToSettledDuration = context.eventCreatedAtNanos - currentViewCreatedTimestamp
            if (networkToSettledDuration > currentNetworkSettleMaxValue) {
                networkSettleMaxValue = networkToSettledDuration
            }
        }
    }

    fun resourceWasDropped(resourceId: String) {
        if (viewWasStopped) return
        currentViewDiagnostic.dropped += 1
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

    fun getState(): ViewInitializationMetricsState = resolveMetric().let { value ->
        ViewInitializationMetricsState(
            initializationTime = value,
            config = initialResourceIdentifier.toConfig(),
            noValueReason = if (value == null) currentViewDiagnostic.resolveNoValueReason() else null
        )
    }

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

    companion object {

        private fun InitialResourceIdentifier.toConfig(): ViewInitializationMetricsConfig {
            if (this !is TimeBasedInitialResourceIdentifier) return ViewInitializationMetricsConfig.CUSTOM

            return if (defaultThresholdUsed()) {
                ViewInitializationMetricsConfig.TIME_BASED_DEFAULT
            } else {
                ViewInitializationMetricsConfig.TIME_BASED_CUSTOM
            }
        }

        private class Diagnostic(
            var started: Int = 0,
            var initial: Int = 0,
            var stopped: Int = 0,
            var dropped: Int = 0
        ) {
            fun resolveNoValueReason() = when {
                started == 0 -> NoValueReason.TimeToNetworkSettle.NO_RESOURCES
                initial == 0 -> NoValueReason.TimeToNetworkSettle.NO_INITIAL_RESOURCES
                initial > dropped + stopped -> NoValueReason.TimeToNetworkSettle.NOT_SETTLED_YET
                else -> NoValueReason.TimeToNetworkSettle.UNKNOWN
            }
        }
    }
}
