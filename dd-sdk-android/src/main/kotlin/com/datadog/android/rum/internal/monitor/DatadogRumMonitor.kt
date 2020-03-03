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
import com.datadog.android.rum.internal.domain.RumEventSerializer
import java.lang.ref.WeakReference
import java.util.UUID

internal class DatadogRumMonitor(
    private val timeProvider: TimeProvider,
    private val writer: Writer<RumEvent>
) : RumMonitor {

    private var activeViewEvent: RumEvent? = null
    private var activeViewData: RumEventData.View? = null
    private var activeViewKey: WeakReference<Any?> = WeakReference(null)

    private val activeResources = mutableMapOf<WeakReference<Any>, RumEvent>()

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
        val startedKey = activeViewKey.get()
        if (startedKey != null) {
            stopView(startedKey, emptyMap())
        }

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
        activeViewEvent = event
        activeViewData = eventData
        activeViewKey = WeakReference(key)
    }

    override fun stopView(
        key: Any,
        attributes: Map<String, Any?>
    ) {
        val startedKey = activeViewKey.get()
        val startedEvent = activeViewEvent
        val startedEventData = activeViewData

        when {
            startedEvent == null || startedEventData == null -> devLogger.w(
                "Unable to end view with key <$key> (missing start). " +
                    "This can mean that the view was not started or already ended."
            )
            startedKey == null -> {
                devLogger.e(
                    "Unable to end view with key <$key> (missing key)." +
                        "Closing previous view automatically."
                )
                sendCompleteView(
                    startedEvent,
                    startedEventData,
                    mapOf(RumEventSerializer.TAG_EVENT_UNSTOPPED to true)
                )
            }
            startedKey != key -> {
                devLogger.e(
                    "Unable to end view with key <$key> (mismatched key). " +
                        "Another view with key <$startedKey> has been started."
                )
            }
            else -> sendCompleteView(startedEvent, startedEventData, attributes)
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
        activeResources[WeakReference(key)] = event
    }

    override fun stopResource(
        key: Any,
        kind: RumResourceKind,
        attributes: Map<String, Any?>
    ) {
        val keyRef = activeResources.keys.firstOrNull { it.get() == key }
        val startedEvent = if (keyRef == null) null else activeResources.remove(keyRef)
        val startedEventData = startedEvent?.eventData as? RumEventData.Resource

        sendResource(key, startedEvent, startedEventData, kind, attributes)
        sendUnclosedResources()
    }

    override fun stopResourceWithError(
        key: Any,
        message: String,
        origin: String,
        throwable: Throwable
    ) {
        val keyRef = activeResources.keys.firstOrNull { it.get() == key }
        val startedEvent = if (keyRef == null) null else activeResources.remove(keyRef)
        val startedEventData = startedEvent?.eventData as? RumEventData.Resource

        when {
            startedEvent == null -> devLogger.w(
                "Unable to end resource with key <$key>. " +
                    "This can mean that the resource was not started or already ended."
            )
            startedEventData == null -> devLogger.e(
                "Unable to end resource with key <$key>. The related data was inconsistent."
            )
            else -> addError(
                message,
                origin,
                throwable,
                startedEvent.attributes
                    .toMutableMap()
                    .apply {
                        set(RumEventSerializer.TAG_HTTP_URL, startedEventData.url)
                    }
            )
        }
        sendUnclosedResources()
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

    // region Internal

    private fun sendCompleteView(
        startedEvent: RumEvent,
        startedEventData: RumEventData.View,
        attributes: Map<String, Any?>
    ) {
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

    private fun sendResource(
        key: Any?,
        startedEvent: RumEvent?,
        startedEventData: RumEventData.Resource?,
        kind: RumResourceKind,
        attributes: Map<String, Any?>
    ) {
        when {
            startedEvent == null -> devLogger.w(
                "Unable to end resource with key <$key>. " +
                    "This can mean that the resource was not started or already ended."
            )
            startedEventData == null -> devLogger.e(
                "Unable to end resource with key <$key>. The related data was inconsistent."
            )
            else -> sendCompleteResource(startedEvent, startedEventData, kind, attributes)
        }
    }

    private fun sendCompleteResource(
        startedEvent: RumEvent,
        startedEventData: RumEventData.Resource,
        kind: RumResourceKind,
        attributes: Map<String, Any?>
    ) {
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

    private fun sendUnclosedResources() {
        activeResources.keys
            .filter { it.get() == null }
            .forEach {
                val startedEvent = activeResources.remove(it)
                val startedEventData = startedEvent?.eventData as? RumEventData.Resource

                sendResource(
                    null,
                    startedEvent,
                    startedEventData,
                    RumResourceKind.UNKNOWN,
                    mapOf(RumEventSerializer.TAG_EVENT_UNSTOPPED to true)
                )
            }
    }

    // endregion
}
