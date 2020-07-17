/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import com.datadog.android.core.internal.data.Writer
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.event.RumEvent
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

internal class RumSessionScope(
    internal val parentScope: RumScope,
    private val sessionInactivityNanos: Long = DEFAULT_SESSION_INACTIVITY_NS,
    private val sessionMaxDurationNanos: Long = DEFAULT_SESSION_MAX_DURATION_NS
) : RumScope {

    internal val activeChildrenScopes = mutableListOf<RumScope>()

    internal var sessionId = RumContext.NULL_SESSION_ID
    internal val sessionStartNs = AtomicLong(System.nanoTime())
    internal val lastUserInteractionNs = AtomicLong(0L)

    internal var applicationDisplayed: Boolean = false

    init {
        GlobalRum.updateRumContext(getRumContext())
    }

    // region RumScope

    override fun handleEvent(
        event: RumRawEvent,
        writer: Writer<RumEvent>
    ): RumScope? {
        if (event is RumRawEvent.ResetSession) {
            sessionId = RumContext.NULL_SESSION_ID
        }
        updateSessionIdIfNeeded()

        val iterator = activeChildrenScopes.iterator()
        while (iterator.hasNext()) {
            val scope = iterator.next().handleEvent(event, writer)
            if (scope == null) {
                iterator.remove()
            }
        }

        if (event is RumRawEvent.StartView) {
            val viewScope = RumViewScope.fromEvent(this, event)
            if (!applicationDisplayed) {
                onApplicationDisplayed(event, viewScope, writer)
            }
            activeChildrenScopes.add(viewScope)
        }

        return this
    }

    override fun getRumContext(): RumContext {
        updateSessionIdIfNeeded()
        return parentScope.getRumContext().copy(sessionId = sessionId)
    }

    // endregion

    // region Internal

    private fun onApplicationDisplayed(
        event: RumRawEvent.StartView,
        viewScope: RumViewScope,
        writer: Writer<RumEvent>
    ) {
        applicationDisplayed = true
        viewScope.handleEvent(RumRawEvent.ApplicationStarted(event.eventTime), writer)
    }

    @Synchronized
    private fun updateSessionIdIfNeeded() {
        val nanoTime = System.nanoTime()
        val isNewSession = sessionId == RumContext.NULL_SESSION_ID
        val sessionLength = nanoTime - sessionStartNs.get()
        val duration = nanoTime - lastUserInteractionNs.get()
        val isInactiveSession = duration >= sessionInactivityNanos
        val isLongSession = sessionLength >= sessionMaxDurationNanos

        if (isNewSession || isInactiveSession || isLongSession) {
            sessionStartNs.set(nanoTime)
            sessionId = UUID.randomUUID().toString()
        }

        lastUserInteractionNs.set(nanoTime)
    }

    // endregion

    companion object {
        internal val DEFAULT_SESSION_INACTIVITY_NS = TimeUnit.MINUTES.toNanos(15)
        internal val DEFAULT_SESSION_MAX_DURATION_NS = TimeUnit.HOURS.toNanos(4)
    }
}
