/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.android.rum.internal.monitor

import com.datadog.android.core.internal.data.Writer
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.internal.domain.RumEvent
import com.datadog.android.rum.internal.domain.RumEventData
import java.util.UUID

internal class DatadogRumMonitor(
    private val timeProvider: TimeProvider,
    private val writer: Writer<RumEvent>
) : RumMonitor {

    private val activeView = mutableMapOf<Any, RumEvent>()
    private val activeResources = mutableMapOf<Any, RumEvent>()

    // region RumMonitor

    override fun addUserAction(
        action: String,
        attributes: Map<String, Any?>
    ) {
        val eventData = RumEventData.UserAction(action)
        val event = RumEvent(
            GlobalRum.getRumContext(),
            timeProvider.getServerTimestamp(),
            eventData,
            attributes
        )
        writer.write(event)
    }

    override fun startView(
        key: Any,
        name: String,
        attributes: Map<String, Any?>
    ) {
        val activeViewId = UUID.randomUUID()
        GlobalRum.updateViewId(activeViewId)
        val pathName = name.replace('.', '/')
        val eventData = RumEventData.View(pathName, System.nanoTime())
        val event = RumEvent(
            GlobalRum.getRumContext(),
            timeProvider.getServerTimestamp(),
            eventData,
            attributes
        )
        activeView[key] = event
    }

    override fun stopView(
        key: Any,
        attributes: Map<String, Any?>
    ) {
        val startedEvent = activeView.remove(key)
        val startedEventData = startedEvent?.eventData as? RumEventData.View

        when {
            startedEvent == null -> {
                devLogger.w(
                    "Unable to stop view with key <$key>. " +
                        "This can mean that the view was not started or already stopped."
                )
            }
            startedEventData == null -> {
                devLogger.e(
                    "Unable to stop view with key <$key>. " +
                        "The related data was inconsistent."
                )
            }
            else -> {
                val updatedDurationNs = System.nanoTime() - startedEventData.durationNanoSeconds
                val updatedEvent = startedEvent.copy(
                    eventData = startedEventData.copy(
                        durationNanoSeconds = updatedDurationNs,
                        version = startedEventData.version + 1
                    ),
                    attributes = startedEvent.attributes.toMutableMap().apply {
                        putAll(attributes)
                    }
                )
                writer.write(updatedEvent)
            }
        }
        GlobalRum.updateViewId(null)
    }

    override fun startResource(
        key: Any,
        url: String,
        attributes: Map<String, Any?>
    ) {
        val eventData = RumEventData.Resource(
            RumResourceKind.OTHER,
            url,
            System.nanoTime()
        )
        val event = RumEvent(
            GlobalRum.getRumContext(),
            timeProvider.getServerTimestamp(),
            eventData,
            attributes
        )
        activeResources[key] = event
    }

    override fun stopResource(
        key: Any,
        kind: RumResourceKind,
        attributes: Map<String, Any?>
    ) {
        val startedEvent = activeResources.remove(key)
        val startedEventData = startedEvent?.eventData as? RumEventData.Resource

        when {
            startedEvent == null -> {
                devLogger.w(
                    "Unable to end resource with key <$key>. " +
                        "This can mean that the resource was not started or already ended."
                )
            }
            startedEventData == null -> {
                devLogger.e(
                    "Unable to end resource with key <$key>. The related data was inconsistent."
                )
            }
            else -> {
                val updatedDurationNs = System.nanoTime() - startedEventData.durationNanoSeconds
                val updatedEvent = startedEvent.copy(
                    eventData = startedEventData.copy(
                        kind = kind,
                        durationNanoSeconds = updatedDurationNs
                    ),
                    attributes = startedEvent.attributes.toMutableMap().apply {
                        putAll(attributes)
                    }
                )
                writer.write(updatedEvent)
            }
        }
    }

    override fun stopResourceWithError(
        key: Any,
        message: String,
        origin: String,
        throwable: Throwable
    ) {
        val startedEvent = activeResources.remove(key)
        val startedEventData = startedEvent?.eventData as? RumEventData.Resource

        when {
            startedEvent == null -> {
                devLogger.w(
                    "Unable to end resource with key <$key>. " +
                        "This can mean that the resource was not started or already ended."
                )
            }
            startedEventData == null -> {
                devLogger.e(
                    "Unable to end resource with key <$key>. The related data was inconsistent."
                )
            }
            else -> {
                val attributes = startedEvent.attributes.toMutableMap()
                attributes["http.url"] = startedEventData.url
                addError(
                    message,
                    origin,
                    throwable,
                    attributes
                )
            }
        }
    }

    override fun addError(
        message: String,
        origin: String,
        throwable: Throwable,
        attributes: Map<String, Any?>
    ) {
        val eventData = RumEventData.Error(message, origin, throwable)
        val event = RumEvent(
            GlobalRum.getRumContext(),
            timeProvider.getServerTimestamp(),
            eventData,
            attributes
        )
        writer.write(event)
    }

    // endregion
}
