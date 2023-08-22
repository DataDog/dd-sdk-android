/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.vitals

import kotlin.math.max
import kotlin.math.min

internal class AggregatingVitalMonitor : VitalMonitor {

    private var lastKnownSample: Double = Double.NaN

    private val listeners: MutableMap<VitalListener, VitalInfo> = mutableMapOf()

    // region VitalObserver

    override fun onNewSample(value: Double) {
        lastKnownSample = value
        notifyListeners(value)
    }

    // endregion

    // region VitalMonitor

    override fun getLastSample(): Double {
        return lastKnownSample
    }

    override fun register(listener: VitalListener) {
        val value = lastKnownSample
        synchronized(listeners) {
            listeners[listener] = VitalInfo.EMPTY
        }
        if (!value.isNaN()) {
            notifyListener(listener, value)
        }
    }

    override fun unregister(listener: VitalListener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    // endregion

    // region Internal

    private fun notifyListeners(value: Double) {
        synchronized(listeners) {
            for (listener in listeners.keys) {
                notifyListener(listener, value)
            }
        }
    }

    private fun notifyListener(listener: VitalListener, value: Double) {
        val vitalInfo = listeners[listener] ?: VitalInfo.EMPTY
        val newSampleCount = vitalInfo.sampleCount + 1

        // Assuming M(n) is the mean value of the first n samples
        // M(n) = ∑ sample(n) / n
        // n⨉M(n) = ∑ sample(n)
        // M(n+1) = ∑ sample(n+1) / (n+1)
        //        = [ sample(n+1) + ∑ sample(n) ] / (n+1)
        //        = (sample(n+1) + n⨉M(n)) / (n+1)
        val meanValue = (value + (vitalInfo.sampleCount * vitalInfo.meanValue)) / newSampleCount

        val updatedInfo = VitalInfo(
            newSampleCount,
            min(value, vitalInfo.minValue),
            max(value, vitalInfo.maxValue),
            meanValue
        )
        listener.onVitalUpdate(updatedInfo)
        synchronized(listeners) {
            listeners[listener] = updatedInfo
        }
    }

    // endregion
}
