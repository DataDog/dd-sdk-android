/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.android.rum.internal.monitor

import com.datadog.android.core.internal.data.Writer
import com.datadog.android.core.internal.time.NoOpTimeProvider
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.log.internal.user.NoOpUserInfoProvider
import com.datadog.android.log.internal.user.UserInfoProvider
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.internal.domain.RumEvent
import com.datadog.android.rum.internal.domain.RumEventData
import com.datadog.android.rum.internal.domain.RumEventSerializer
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

internal class DatadogRumMonitor(
    private val writer: Writer<RumEvent>,
    private val timeProvider: TimeProvider = NoOpTimeProvider(),
    private val userInfoProvider: UserInfoProvider = NoOpUserInfoProvider()
) : RumMonitor {

    @Volatile
    private var activeViewStartNanos: Long = 0L

    @Volatile
    private var activeViewEvent: RumEvent? = null

    @Volatile
    private var activeViewData: RumEventData.View? = null

    @Volatile
    private var activeViewKey: WeakReference<Any?> = WeakReference(null)

    private val activeResources = mutableMapOf<WeakReference<Any>, RumEvent>()

    @Volatile
    private var activeActionEvent: RumEvent? = null

    @Volatile
    private var activeActionData: RumEventData.UserAction? = null

    private var ongoingUserActionResources = mutableListOf<WeakReference<Any>>()

    private val lastActionRelatedEventNs = AtomicLong(0L)

    // region RumMonitor

    @Synchronized
    override fun startView(
        key: Any,
        name: String,
        attributes: Map<String, Any?>
    ) {
        GlobalRum.addUserInteraction()
        val startedKey = activeViewKey.get()
        if (startedKey != null) {
            stopView(startedKey, emptyMap())
        }

        val activeViewId = UUID.randomUUID()
        GlobalRum.updateViewId(activeViewId)
        val pathName = name.replace('.', '/')
        val eventData = RumEventData.View(pathName, 0)
        val event = rumEvent(eventData, attributes)

        activeViewStartNanos = System.nanoTime()
        activeViewEvent = event
        activeViewData = eventData
        activeViewKey = WeakReference(key)
    }

    @Synchronized
    override fun stopView(
        key: Any,
        attributes: Map<String, Any?>
    ) {
        sendUnclosedResources()
        sendUnclosedAction()

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
                updateAndSendView(
                    mapOf(RumEventSerializer.TAG_EVENT_UNSTOPPED to true)
                )
            }
            startedKey != key -> {
                devLogger.e(
                    "Unable to end view with key <$key> (mismatched key). " +
                            "Another view with key <$startedKey> has been started."
                )
            }
            else -> updateAndSendView(attributes)
        }
        activeViewKey = WeakReference(null)
        activeViewData = null
        activeViewEvent = null
        GlobalRum.updateViewId(null)
    }

    @Synchronized
    override fun addUserAction(
        action: String,
        attributes: Map<String, Any?>
    ) {
        if (isWithinActionScope()) {
            devLogger.w("User action $action was ignored: previous action still active.")
            return
        }
        sendUnclosedAction()
        val nanoTime = System.nanoTime()
        val eventData = RumEventData.UserAction(
            action,
            UUID.randomUUID(),
            nanoTime
        )

        val event = rumEvent(eventData, attributes)
        lastActionRelatedEventNs.set(nanoTime)
        activeActionEvent = event
        activeActionData = eventData
    }

    @Synchronized
    override fun startResource(
        key: Any,
        url: String,
        attributes: Map<String, Any?>
    ) {
        val updatedAttributes = updateAttributesWithActionId(attributes)
        val eventData = RumEventData.Resource(
            RumResourceKind.OTHER,
            url,
            System.nanoTime()
        )
        val event = rumEvent(eventData, updatedAttributes)
        activeResources[WeakReference(key)] = event
        ongoingUserActionResources.add(WeakReference(key))
    }

    @Synchronized
    override fun stopResource(
        key: Any,
        kind: RumResourceKind,
        attributes: Map<String, Any?>
    ) {
        removeUserActionResource(key)
        val keyRef = activeResources.keys.firstOrNull { it.get() == key }
        val startedEvent = if (keyRef == null) null else activeResources.remove(keyRef)
        val startedEventData = startedEvent?.eventData as? RumEventData.Resource

        sendResource(key, startedEvent, startedEventData, kind, attributes)
        sendUnclosedResources()
    }

    @Synchronized
    override fun stopResourceWithError(
        key: Any,
        message: String,
        origin: String,
        throwable: Throwable
    ) {
        removeUserActionResource(key)
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

    @Synchronized
    override fun addError(
        message: String,
        origin: String,
        throwable: Throwable?,
        attributes: Map<String, Any?>
    ) {
        val updatedAttributes = updateAttributesWithActionId(attributes)
        val eventData = RumEventData.Error(message, origin, throwable)
        val event = rumEvent(eventData, updatedAttributes)
        writer.write(event)
        updateAndSendView { it.incrementErrorCount() }
    }

    // endregion

    // region Internal

    private fun rumEvent(eventData: RumEventData, attributes: Map<String, Any?>): RumEvent {
        return RumEvent(
            GlobalRum.getRumContext(),
            timeProvider.getDeviceTimestamp(),
            eventData,
            userInfoProvider.getUserInfo(),
            attributes
        )
    }

    private fun updateAttributesWithActionId(
        attributes: Map<String, Any?>
    ): Map<String, Any?> {
        val actionId = getActiveActionId()
        return if (actionId == null) {
            attributes
        } else {
            attributes.toMutableMap()
                .apply {
                    put(RumEventSerializer.TAG_EVENT_USER_ACTION_ID, actionId.toString())
                }
        }
    }

    private fun getActiveActionId(): UUID? {
        val actionEvent = activeActionEvent
        val actionData = activeActionData
        val isWithinActionScope = isWithinActionScope()

        return if (
            actionEvent != null &&
            actionData != null &&
            isWithinActionScope
        ) {
            val nanoTime = System.nanoTime()
            lastActionRelatedEventNs.set(nanoTime)
            actionData.id
        } else {
            sendUnclosedAction()
            null
        }
    }

    private fun isWithinActionScope(): Boolean {
        val nanoTime = System.nanoTime()
        val lastAction = lastActionRelatedEventNs.get()
        val isLastEventRecent = (nanoTime - lastAction) < ACTION_INACTIVITY_NS
        ongoingUserActionResources.removeAll { it.get() == null }
        val hasUnclosedResources = ongoingUserActionResources.isNotEmpty()
        return isLastEventRecent || hasUnclosedResources
    }

    private fun sendUnclosedAction() {
        val lastAction = lastActionRelatedEventNs.get()
        val actionEvent = activeActionEvent
        val actionData = activeActionData
        sendUserAction(actionEvent, actionData, lastAction)
        activeActionEvent = null
        activeActionData = null
    }

    private fun removeUserActionResource(key: Any) {
        val nanoTime = System.nanoTime()
        val actionEvent = activeActionEvent
        val actionData = activeActionData

        val keyRef = ongoingUserActionResources.firstOrNull { it.get() == key }
        if (keyRef != null) {
            ongoingUserActionResources.remove(keyRef)
            if (actionEvent != null && actionData != null) {
                lastActionRelatedEventNs.set(nanoTime)
            }
        }

        ongoingUserActionResources.removeAll { it.get() == null }
    }

    private fun updateAndSendView(
        attributes: Map<String, Any?> = emptyMap(),
        update: (RumEventData.View) -> RumEventData.View = { it }
    ) {
        val startedEvent = activeViewEvent
        val startedEventData = activeViewData

        if (startedEvent == null || startedEventData == null) {
            sdkLogger.w("Unable to update view, none was started")
            return
        }

        val updatedDurationNs = System.nanoTime() - activeViewStartNanos
        val updatedData = update(startedEventData).copy(
            durationNanoSeconds = updatedDurationNs,
            version = startedEventData.version + 1
        )
        val updatedEvent = startedEvent.copy(
            eventData = updatedData,
            attributes = startedEvent.attributes
                .toMutableMap()
                .apply {
                    putAll(attributes)
                }
        )
        writer.write(updatedEvent)
        activeViewEvent = updatedEvent
        activeViewData = updatedData
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
        updateAndSendView { it.incrementResourceCount() }
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

    private fun sendUserAction(
        startedEvent: RumEvent?,
        startedEventData: RumEventData.UserAction?,
        endNanoTime: Long
    ) {
        when {
            startedEvent == null -> devLogger.w(
                "Unable to end user action. " +
                        "This can mean that the action was not started or already ended."
            )
            startedEventData == null -> devLogger.e(
                "Unable to end user action. The related data was inconsistent."
            )
            else -> sendCompleteUserAction(startedEvent, startedEventData, endNanoTime)
        }
    }

    private fun sendCompleteUserAction(
        startedEvent: RumEvent,
        startedEventData: RumEventData.UserAction,
        endNanoTime: Long
    ) {
        val updatedDurationNs = endNanoTime - startedEventData.durationNanoSeconds
        val updatedEvent = startedEvent.copy(
            eventData = startedEventData.copy(
                durationNanoSeconds = max(updatedDurationNs, 1L)
            )
        )
        writer.write(updatedEvent)
        updateAndSendView { it.incrementUserActionCount() }
    }

    // endregion

    companion object {
        internal const val ACTION_INACTIVITY_MS = 100L
        internal val ACTION_INACTIVITY_NS = TimeUnit.MILLISECONDS.toNanos(ACTION_INACTIVITY_MS)
    }
}
