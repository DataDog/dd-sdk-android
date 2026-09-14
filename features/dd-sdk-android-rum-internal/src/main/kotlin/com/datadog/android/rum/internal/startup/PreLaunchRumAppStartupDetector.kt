/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.startup

import android.app.Application
import com.datadog.android.internal.time.AppStartTimeProvider
import com.datadog.android.internal.time.DefaultTimeProvider

object PreLaunchRumAppStartupDetector : RumAppStartupDetector.Listener {
    private sealed interface Event {
        data class AppStart(val scenario: RumStartupScenario) : Event

        data class TTID(
            val scenario: RumStartupScenario,
            val durationNs: Long,
            val wasForwarded: Boolean
        ) : Event
    }

    private val pendingEvents = mutableListOf<Event>()

    private var detector: RumAppStartupDetector? = null
    private var listener: RumAppStartupDetector.Listener? = null

    val isInstalled: Boolean
        get() = detector != null

    fun install(application: Application) {
        if (detector != null) return
        val timeProvider = DefaultTimeProvider()

        detector = RumAppStartupDetector.create(
            application = application,
            appStartTimeNs = AppStartTimeProvider.create(
                timeProviderFactory = { timeProvider }
            ).appStartTimeNs,
            timeProvider = timeProvider,
            listener = this,
            activityPredicate = { true },
            warningLogger = { _, _ -> }
        )
    }

    fun attach(listener: RumAppStartupDetector.Listener) {
        while (pendingEvents.isNotEmpty()) {
            val events = pendingEvents.toList()
            pendingEvents.clear()
            events.forEach { it.deliver(listener) }
        }
        this.listener = listener
    }

    fun detach(listener: RumAppStartupDetector.Listener) {
        if (this.listener === listener) this.listener = null
    }

    override fun onAppStartupDetected(scenario: RumStartupScenario) {
        dispatch(Event.AppStart(scenario))
    }

    override fun onTTIDComputed(
        scenario: RumStartupScenario,
        durationNs: Long,
        wasForwarded: Boolean
    ) {
        dispatch(Event.TTID(scenario, durationNs, wasForwarded))
    }

    private fun dispatch(event: Event) {
        val currentListener = listener
        if (currentListener == null) {
            pendingEvents += event
        } else {
            event.deliver(currentListener)
        }
    }

    private fun Event.deliver(listener: RumAppStartupDetector.Listener) {
        when (this) {
            is Event.AppStart -> listener.onAppStartupDetected(scenario)
            is Event.TTID -> listener.onTTIDComputed(scenario, durationNs, wasForwarded)
        }
    }

    internal fun resetForTests() {
        detector?.destroy()
        detector = null
        listener = null
        pendingEvents.clear()
    }
}
