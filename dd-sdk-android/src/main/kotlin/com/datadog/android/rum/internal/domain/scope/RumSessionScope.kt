/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import android.os.Build
import android.os.Process
import android.os.SystemClock
import com.datadog.android.Datadog
import com.datadog.android.core.internal.data.NoOpWriter
import com.datadog.android.core.internal.data.Writer
import com.datadog.android.core.internal.net.FirstPartyHostDetector
import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.event.RumEvent
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

internal class RumSessionScope(
    private val parentScope: RumScope,
    internal val samplingRate: Float,
    internal val firstPartyHostDetector: FirstPartyHostDetector,
    private val sessionInactivityNanos: Long = DEFAULT_SESSION_INACTIVITY_NS,
    private val sessionMaxDurationNanos: Long = DEFAULT_SESSION_MAX_DURATION_NS
) : RumScope {

    internal val activeChildrenScopes = mutableListOf<RumScope>()

    internal var keepSession: Boolean = false
    internal var sessionId = RumContext.NULL_UUID
    internal val sessionStartNs = AtomicLong(System.nanoTime())
    internal val lastUserInteractionNs = AtomicLong(0L)

    private var resetSessionTime: Long? = null

    private var applicationDisplayed: Boolean = false

    private val random = SecureRandom()
    private val noOpWriter = NoOpWriter<RumEvent>()

    init {
        GlobalRum.updateRumContext(getRumContext())
    }

    // region RumScope

    override fun handleEvent(
        event: RumRawEvent,
        writer: Writer<RumEvent>
    ): RumScope? {
        if (event is RumRawEvent.ResetSession) {
            sessionId = RumContext.NULL_UUID
            resetSessionTime = System.nanoTime()
            applicationDisplayed = false
        }
        updateSessionIdIfNeeded()

        val actualWriter = if (keepSession) writer else noOpWriter

        val activeChildrenCount = activeChildrenScopes.size
        val iterator = activeChildrenScopes.iterator()
        while (iterator.hasNext()) {
            val scope = iterator.next().handleEvent(event, actualWriter)
            if (scope == null) {
                iterator.remove()
            }
        }

        if (event is RumRawEvent.StartView) {
            val viewScope = RumViewScope.fromEvent(this, event, firstPartyHostDetector)

            onApplicationDisplayed(event, viewScope, actualWriter)
            activeChildrenScopes.add(viewScope)
        } else if (activeChildrenCount == 0) {
            devLogger.w(MESSAGE_MISSING_VIEW)
        }

        return this
    }

    override fun getRumContext(): RumContext {
        updateSessionIdIfNeeded()
        return if (keepSession) {
            parentScope.getRumContext().copy(sessionId = sessionId)
        } else {
            RumContext()
        }
    }

    // endregion

    // region Internal

    internal fun onApplicationDisplayed(
        event: RumRawEvent.StartView,
        viewScope: RumViewScope,
        writer: Writer<RumEvent>
    ) {
        if (!applicationDisplayed) {
            applicationDisplayed = true
            val applicationStartTime = resolveStartupTimeNs()
            viewScope.handleEvent(
                RumRawEvent.ApplicationStarted(event.eventTime, applicationStartTime),
                writer
            )
        }
    }

    private fun resolveStartupTimeNs(): Long {
        val resetTimeNs = resetSessionTime
        return when {
            resetTimeNs != null -> resetTimeNs
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> {
                val diffMs = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
                System.nanoTime() - TimeUnit.MILLISECONDS.toNanos(diffMs)
            }
            else -> Datadog.startupTimeNs
        }
    }

    @Synchronized
    private fun updateSessionIdIfNeeded() {
        val nanoTime = System.nanoTime()
        val isNewSession = sessionId == RumContext.NULL_UUID
        val sessionLength = nanoTime - sessionStartNs.get()
        val duration = nanoTime - lastUserInteractionNs.get()
        val isInactiveSession = duration >= sessionInactivityNanos
        val isLongSession = sessionLength >= sessionMaxDurationNanos

        if (isNewSession || isInactiveSession || isLongSession) {
            keepSession = (random.nextFloat() * 100f) < samplingRate
            sessionStartNs.set(nanoTime)
            sessionId = UUID.randomUUID().toString()
        }

        lastUserInteractionNs.set(nanoTime)
    }

    // endregion

    companion object {
        internal val DEFAULT_SESSION_INACTIVITY_NS = TimeUnit.MINUTES.toNanos(15)
        internal val DEFAULT_SESSION_MAX_DURATION_NS = TimeUnit.HOURS.toNanos(4)

        internal const val MESSAGE_MISSING_VIEW =
            "A RUM event was detected, but no view is active. " +
                "To track views automatically, try calling the " +
                "DatadogConfig.Builder.useViewTrackingStrategy() method.\n" +
                "You can also track views manually using the RumMonitor.startView() and " +
                "RumMonitor.stopView() methods."
    }
}
