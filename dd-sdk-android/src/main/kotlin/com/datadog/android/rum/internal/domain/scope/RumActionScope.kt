/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import com.datadog.android.core.internal.data.Writer
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.event.RumEvent
import com.datadog.android.rum.internal.domain.event.RumEventData
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.max

internal class RumActionScope(
    val parentScope: RumScope,
    val waitForStop: Boolean,
    initialName: String,
    initialAttributes: Map<String, Any?>
) : RumScope {

    internal val eventTimestamp = RumFeature.timeProvider.getDeviceTimestamp()
    internal val actionId: UUID = UUID.randomUUID()
    internal var name: String = initialName
    internal val startedNanos: Long = System.nanoTime()
    internal var lastInteractionNanos: Long = startedNanos

    internal val attributes: MutableMap<String, Any?> = initialAttributes.toMutableMap()

    internal val ongoingResourceKeys = mutableListOf<WeakReference<Any>>()

    internal var resourcesCount: Int = 0
    internal var viewTreeChangeCount: Int = 0

    private var sent = false

    // endregion

    override fun handleEvent(event: RumRawEvent, writer: Writer<RumEvent>): RumScope? {

        val now = System.nanoTime()
        val isInactive = now - lastInteractionNanos > ACTION_INACTIVITY_NS
        val isLongDuration = now - startedNanos > ACTION_MAX_DURATION_NS
        ongoingResourceKeys.removeAll { it.get() == null }
        val shouldStop = isInactive && ongoingResourceKeys.isEmpty() && !waitForStop

        when {
            shouldStop -> sendAction(lastInteractionNanos, writer)
            isLongDuration -> sendAction(now, writer)
            event is RumRawEvent.ViewTreeChanged -> onViewTreeChanged(now)
            event is RumRawEvent.StopView -> onStopView(now, writer)
            event is RumRawEvent.StopAction -> onStopAction(event, now, writer)
            event is RumRawEvent.StartResource -> onStartResource(now, event)
            event is RumRawEvent.StopResource -> onStopResource(event, now)
        }

        return if (sent) null else this
    }

    override fun getRumContext(): RumContext {
        return parentScope.getRumContext()
    }

    // endregion

    // region Internal

    private fun onViewTreeChanged(now: Long) {
        lastInteractionNanos = now
        viewTreeChangeCount++
    }

    private fun onStopView(
        now: Long,
        writer: Writer<RumEvent>
    ) {
        ongoingResourceKeys.clear()
        sendAction(now, writer)
    }

    private fun onStopAction(
        event: RumRawEvent.StopAction,
        now: Long,
        writer: Writer<RumEvent>
    ) {
        name = event.name
        attributes.putAll(event.attributes)
        sendAction(now, writer)
    }

    private fun onStartResource(
        now: Long,
        event: RumRawEvent.StartResource
    ) {
        lastInteractionNanos = now
        resourcesCount++
        ongoingResourceKeys.add(WeakReference(event.key))
    }

    private fun onStopResource(
        event: RumRawEvent.StopResource,
        now: Long
    ) {
        val keyRef = ongoingResourceKeys.firstOrNull { it.get() == event.key }
        if (keyRef != null) {
            ongoingResourceKeys.remove(keyRef)
            lastInteractionNanos = now
        }
    }

    private fun sendAction(
        endNanos: Long,
        writer: Writer<RumEvent>
    ) {
        if (sent) return

        if (resourcesCount > 0 || viewTreeChangeCount > 0) {
            val eventData = RumEventData.UserAction(
                name,
                actionId,
                max(endNanos - startedNanos, 1L)
            )

            val event = RumEvent(
                getRumContext(),
                eventTimestamp,
                eventData,
                RumFeature.userInfoProvider.getUserInfo(),
                attributes,
                networkInfo = null
            )

            writer.write(event)
            parentScope.handleEvent(RumRawEvent.SentAction(), writer)
        }
        sent = true
    }

    // endregion

    companion object {
        internal const val ACTION_INACTIVITY_MS = 100L
        internal const val ACTION_MAX_DURATION_MS = 10000L
        internal val ACTION_INACTIVITY_NS = TimeUnit.MILLISECONDS.toNanos(ACTION_INACTIVITY_MS)
        internal val ACTION_MAX_DURATION_NS = TimeUnit.MILLISECONDS.toNanos(ACTION_MAX_DURATION_MS)

        fun fromEvent(
            parentScope: RumScope,
            event: RumRawEvent.StartAction
        ): RumScope {
            return RumActionScope(parentScope, event.waitForStop, event.name, event.attributes)
        }
    }
}
