/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.api.feature.stub

import com.datadog.android.api.feature.FeatureEventReceiver

/**
 * A stub implementation of [FeatureEventReceiver].
 * @param onReceive a lambda to be called when an event is received.
 */
class StubFeatureEventReceiver(private val onReceive: () -> Unit = {}) : FeatureEventReceiver {

    private val events: MutableList<Any> = mutableListOf()

    /**
     * Get the received events.
     */
    fun getReceivedEvents(): List<Any> {
        synchronized(events) {
            return events.toList()
        }
    }

    override fun onReceive(event: Any) {
        synchronized(events) {
            events.add(event)
        }
        onReceive()
    }
}
