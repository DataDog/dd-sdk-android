/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import androidx.annotation.WorkerThread
import com.datadog.android.core.internal.net.FirstPartyHostHeaderTypeResolver
import com.datadog.android.core.internal.utils.percent
import com.datadog.android.rum.RumSessionListener
import com.datadog.android.rum.internal.AppStartTimeProvider
import com.datadog.android.rum.internal.DefaultAppStartTimeProvider
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.vitals.VitalMonitor
import com.datadog.android.v2.api.Feature
import com.datadog.android.v2.api.SdkCore
import com.datadog.android.v2.core.internal.ContextProvider
import com.datadog.android.v2.core.internal.storage.DataWriter
import com.datadog.android.v2.core.internal.storage.NoOpDataWriter
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

@Suppress("LongParameterList")
internal class RumSessionScope(
    private val parentScope: RumScope,
    private val sdkCore: SdkCore,
    internal val samplingRate: Float,
    internal val backgroundTrackingEnabled: Boolean,
    internal val trackFrustrations: Boolean,
    internal val viewChangedListener: RumViewChangedListener?,
    internal val firstPartyHostHeaderTypeResolver: FirstPartyHostHeaderTypeResolver,
    cpuVitalMonitor: VitalMonitor,
    memoryVitalMonitor: VitalMonitor,
    frameRateVitalMonitor: VitalMonitor,
    internal val sessionListener: RumSessionListener?,
    contextProvider: ContextProvider,
    applicationDisplayed: Boolean,
    appStartTimeProvider: AppStartTimeProvider = DefaultAppStartTimeProvider(),
    private val sessionInactivityNanos: Long = DEFAULT_SESSION_INACTIVITY_NS,
    private val sessionMaxDurationNanos: Long = DEFAULT_SESSION_MAX_DURATION_NS
) : RumScope {

    internal var sessionId = RumContext.NULL_UUID
    internal var sessionState: State = State.NOT_TRACKED
    internal var isActive: Boolean = true
    private val sessionStartNs = AtomicLong(System.nanoTime())
    private val lastUserInteractionNs = AtomicLong(0L)

    private val random = SecureRandom()

    private val noOpWriter = NoOpDataWriter<Any>()

    @Suppress("LongParameterList")
    internal var childScope: RumScope? = RumViewManagerScope(
        this,
        sdkCore,
        backgroundTrackingEnabled,
        trackFrustrations,
        viewChangedListener,
        firstPartyHostHeaderTypeResolver,
        cpuVitalMonitor,
        memoryVitalMonitor,
        frameRateVitalMonitor,
        appStartTimeProvider,
        contextProvider,
        applicationDisplayed
    )

    init {
        sdkCore.updateFeatureContext(Feature.RUM_FEATURE_NAME) {
            it.putAll(getRumContext().toMap())
        }
    }

    enum class State {
        NOT_TRACKED,
        TRACKED,
        EXPIRED
    }

    // region RumScope

    @WorkerThread
    override fun handleEvent(
        event: RumRawEvent,
        writer: DataWriter<Any>
    ): RumScope? {
        if (event is RumRawEvent.ResetSession) {
            renewSession(System.nanoTime())
        } else if (event is RumRawEvent.StopSession) {
            stopSession()
        }

        updateSession(event)

        val actualWriter = if (sessionState == State.TRACKED) writer else noOpWriter

        childScope = childScope?.handleEvent(event, actualWriter)

        return if (isSessionComplete()) {
            null
        } else {
            this
        }
    }

    override fun getRumContext(): RumContext {
        val parentContext = parentScope.getRumContext()
        return parentContext.copy(
            sessionId = sessionId,
            sessionState = sessionState,
            isSessionActive = isActive
        )
    }

    override fun isActive(): Boolean {
        return isActive
    }

    // endregion

    // region Internal

    private fun stopSession() {
        isActive = false
    }

    private fun isSessionComplete(): Boolean {
        return !isActive && childScope == null
    }

    @Suppress("ComplexMethod")
    private fun updateSession(event: RumRawEvent) {
        val nanoTime = System.nanoTime()
        val isNewSession = sessionId == RumContext.NULL_UUID

        val timeSinceLastInteractionNs = nanoTime - lastUserInteractionNs.get()
        val isExpired = timeSinceLastInteractionNs >= sessionInactivityNanos
        val timeSinceSessionStartNs = nanoTime - sessionStartNs.get()
        val isTimedOut = timeSinceSessionStartNs >= sessionMaxDurationNanos

        val isInteraction = (event is RumRawEvent.StartView) || (event is RumRawEvent.StartAction)
        val isBackgroundEvent = (event.javaClass in RumViewManagerScope.validBackgroundEventTypes)

        if (isInteraction) {
            if (isNewSession || isExpired || isTimedOut) {
                renewSession(nanoTime)
            }
            lastUserInteractionNs.set(nanoTime)
        } else if (isExpired) {
            if (backgroundTrackingEnabled && isBackgroundEvent) {
                renewSession(nanoTime)
                lastUserInteractionNs.set(nanoTime)
            } else {
                sessionState = State.EXPIRED
            }
        } else if (isTimedOut) {
            renewSession(nanoTime)
        }
    }

    private fun renewSession(nanoTime: Long) {
        val keepSession = random.nextFloat() < samplingRate.percent()
        sessionState = if (keepSession) State.TRACKED else State.NOT_TRACKED
        sessionId = UUID.randomUUID().toString()
        sessionStartNs.set(nanoTime)
        sdkCore.updateFeatureContext(Feature.RUM_FEATURE_NAME) {
            it.putAll(getRumContext().toMap())
        }
        sessionListener?.onSessionStarted(sessionId, !keepSession)
        sdkCore.getFeature(Feature.SESSION_REPLAY_FEATURE_NAME)?.sendEvent(
            mapOf(
                SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to RUM_SESSION_RENEWED_BUS_MESSAGE,
                RUM_KEEP_SESSION_BUS_MESSAGE_KEY to keepSession
            )
        )
    }

    // endregion

    companion object {

        internal const val SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY = "type"
        internal const val RUM_SESSION_RENEWED_BUS_MESSAGE = "rum_session_renewed"
        internal const val RUM_KEEP_SESSION_BUS_MESSAGE_KEY = "keepSession"
        internal val DEFAULT_SESSION_INACTIVITY_NS = TimeUnit.MINUTES.toNanos(15)
        internal val DEFAULT_SESSION_MAX_DURATION_NS = TimeUnit.HOURS.toNanos(4)
    }
}
