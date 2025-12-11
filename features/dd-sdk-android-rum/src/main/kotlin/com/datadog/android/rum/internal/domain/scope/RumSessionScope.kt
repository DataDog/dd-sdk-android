/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import androidx.annotation.WorkerThread
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.EventWriteScope
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.NoOpDataWriter
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.internal.net.FirstPartyHostHeaderTypeResolver
import com.datadog.android.rum.RumSessionListener
import com.datadog.android.rum.RumSessionType
import com.datadog.android.rum.internal.domain.InfoProvider
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.domain.accessibility.AccessibilitySnapshotManager
import com.datadog.android.rum.internal.domain.battery.BatteryInfo
import com.datadog.android.rum.internal.domain.display.DisplayInfo
import com.datadog.android.rum.internal.metric.SessionMetricDispatcher
import com.datadog.android.rum.internal.metric.slowframes.SlowFramesListener
import com.datadog.android.rum.internal.startup.RumSessionScopeStartupManager
import com.datadog.android.rum.internal.utils.percent
import com.datadog.android.rum.internal.vitals.VitalMonitor
import com.datadog.android.rum.metric.interactiontonextview.LastInteractionIdentifier
import com.datadog.android.rum.metric.networksettled.InitialResourceIdentifier
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

@Suppress("LongParameterList")
internal class RumSessionScope(
    override val parentScope: RumScope,
    private val sdkCore: InternalSdkCore,
    private val sessionEndedMetricDispatcher: SessionMetricDispatcher,
    internal val sampleRate: Float,
    internal val backgroundTrackingEnabled: Boolean,
    trackFrustrations: Boolean,
    viewChangedListener: RumViewChangedListener?,
    internal val firstPartyHostHeaderTypeResolver: FirstPartyHostHeaderTypeResolver,
    cpuVitalMonitor: VitalMonitor,
    memoryVitalMonitor: VitalMonitor,
    frameRateVitalMonitor: VitalMonitor,
    private val sessionListener: RumSessionListener?,
    applicationDisplayed: Boolean,
    networkSettledResourceIdentifier: InitialResourceIdentifier,
    lastInteractionIdentifier: LastInteractionIdentifier?,
    slowFramesListener: SlowFramesListener?,
    accessibilitySnapshotManager: AccessibilitySnapshotManager,
    batteryInfoProvider: InfoProvider<BatteryInfo>,
    displayInfoProvider: InfoProvider<DisplayInfo>,
    private val sessionInactivityNanos: Long = DEFAULT_SESSION_INACTIVITY_NS,
    private val sessionMaxDurationNanos: Long = DEFAULT_SESSION_MAX_DURATION_NS,
    rumSessionTypeOverride: RumSessionType?,
    private val rumSessionScopeStartupManagerFactory: () -> RumSessionScopeStartupManager
) : RumScope {

    internal var sessionId = RumContext.NULL_UUID
    internal var sessionState: State = State.NOT_TRACKED
    private var startReason: StartReason = StartReason.USER_APP_LAUNCH
    internal var isActive: Boolean = true
    private val sessionStartNs = AtomicLong(sdkCore.timeProvider.getDeviceElapsedTimeNs())

    private val lastUserInteractionNs = AtomicLong(0L)

    private val random = SecureRandom()

    private val noOpWriter = NoOpDataWriter<Any>()

    private var rumSessionScopeStartupManager: RumSessionScopeStartupManager? = null

    @Suppress("LongParameterList")
    internal var childScope: RumViewManagerScope? = RumViewManagerScope(
        parentScope = this,
        sdkCore = sdkCore,
        sessionEndedMetricDispatcher = sessionEndedMetricDispatcher,
        backgroundTrackingEnabled = backgroundTrackingEnabled,
        trackFrustrations = trackFrustrations,
        viewChangedListener = viewChangedListener,
        firstPartyHostHeaderTypeResolver = firstPartyHostHeaderTypeResolver,
        cpuVitalMonitor = cpuVitalMonitor,
        memoryVitalMonitor = memoryVitalMonitor,
        frameRateVitalMonitor = frameRateVitalMonitor,
        applicationDisplayed = applicationDisplayed,
        sampleRate = sampleRate,
        initialResourceIdentifier = networkSettledResourceIdentifier,
        slowFramesListener = slowFramesListener,
        lastInteractionIdentifier = lastInteractionIdentifier,
        rumSessionTypeOverride = rumSessionTypeOverride,
        accessibilitySnapshotManager = accessibilitySnapshotManager,
        batteryInfoProvider = batteryInfoProvider,
        displayInfoProvider = displayInfoProvider
    )

    internal val activeView: RumViewScope?
        get() = if (isActive() && childScope != null) {
            childScope?.activeView
        } else {
            null
        }

    enum class State(val asString: String) {
        NOT_TRACKED("NOT_TRACKED"),
        TRACKED("TRACKED"),
        EXPIRED("EXPIRED");

        companion object {
            fun fromString(string: String?): State? {
                return values().firstOrNull { it.asString == string }
            }
        }
    }

    enum class StartReason(val asString: String) {
        USER_APP_LAUNCH("user_app_launch"),
        INACTIVITY_TIMEOUT("inactivity_timeout"),
        MAX_DURATION("max_duration"),
        BACKGROUND_LAUNCH("background_launch"),
        PREWARM("prewarm"),
        FROM_NON_INTERACTIVE_SESSION("from_non_interactive_session"),
        EXPLICIT_STOP("explicit_stop")
        ;

        companion object {
            fun fromString(string: String?): StartReason? {
                return values().firstOrNull { it.asString == string }
            }
        }
    }

    // region RumScope

    @WorkerThread
    override fun handleEvent(
        event: RumRawEvent,
        datadogContext: DatadogContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>
    ): RumScope? {
        if (event is RumRawEvent.ResetSession) {
            renewSession(event.eventTime, StartReason.EXPLICIT_STOP)
        } else if (event is RumRawEvent.StopSession) {
            stopSession()
        }

        updateSession(event)

        val actualWriter = if (sessionState == State.TRACKED) writer else noOpWriter

        val rumContext = activeView?.getRumContext() ?: getRumContext()

        when (event) {
            is RumRawEvent.AppStartTTIDEvent -> {
                if (sessionState == State.TRACKED) {
                    rumSessionScopeStartupManager?.onTTIDEvent(
                        event = event,
                        datadogContext = datadogContext,
                        writeScope = writeScope,
                        writer = actualWriter,
                        rumContext = rumContext,
                        customAttributes = getCustomAttributes()
                    )
                }
            }
            is RumRawEvent.AppStartEvent -> {
                if (sessionState == State.TRACKED) {
                    rumSessionScopeStartupManager?.onAppStartEvent(event = event)
                }
            }
            is RumRawEvent.AppStartTTFDEvent -> {
                if (sessionState == State.TRACKED) {
                    rumSessionScopeStartupManager?.onTTFDEvent(
                        event = event,
                        datadogContext = datadogContext,
                        writeScope = writeScope,
                        writer = actualWriter,
                        rumContext = rumContext,
                        customAttributes = getCustomAttributes()
                    )
                }
            }
            is RumRawEvent.SdkInit -> {}
            else -> {
                childScope =
                    childScope?.handleEvent(event, datadogContext, writeScope, actualWriter) as? RumViewManagerScope
            }
        }

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
            sessionStartReason = startReason,
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
        sessionEndedMetricDispatcher.onSessionStopped(sessionId)
    }

    private fun isSessionComplete(): Boolean {
        return !isActive && childScope == null
    }

    @Suppress("ComplexMethod")
    private fun updateSession(event: RumRawEvent) {
        val nanoTime = sdkCore.timeProvider.getDeviceElapsedTimeNs()
        val isNewSession = sessionId == RumContext.NULL_UUID

        val timeSinceLastInteractionNs = nanoTime - lastUserInteractionNs.get()
        val isExpired = timeSinceLastInteractionNs >= sessionInactivityNanos
        val timeSinceSessionStartNs = nanoTime - sessionStartNs.get()
        val isTimedOut = timeSinceSessionStartNs >= sessionMaxDurationNanos

        val isInteraction = (event is RumRawEvent.StartView) || (event is RumRawEvent.StartAction)
        val isBackgroundEvent = event.javaClass in RumViewManagerScope.validBackgroundEventTypes
        val isSdkInitInForeground = event is RumRawEvent.SdkInit && event.isAppInForeground
        val isSdkInitInBackground = event is RumRawEvent.SdkInit && !event.isAppInForeground

        // When the session is expired, time-out or stopSession API is called, session ended metric should be sent
        if (isExpired || isTimedOut || isActive.not()) {
            sessionEndedMetricDispatcher.endMetric(sessionId, sdkCore.time.serverTimeOffsetMs)
        }

        if (isInteraction || isSdkInitInForeground) {
            if (isNewSession || isExpired || isTimedOut) {
                val reason = if (isNewSession) {
                    StartReason.USER_APP_LAUNCH
                } else if (isExpired) {
                    StartReason.INACTIVITY_TIMEOUT
                } else {
                    StartReason.MAX_DURATION
                }
                renewSession(event.eventTime, reason)
            }
            lastUserInteractionNs.set(nanoTime)
        } else if (isExpired) {
            if (backgroundTrackingEnabled && (isBackgroundEvent || isSdkInitInBackground)) {
                renewSession(event.eventTime, StartReason.BACKGROUND_LAUNCH)
                lastUserInteractionNs.set(nanoTime)
            } else {
                sessionState = State.EXPIRED
            }
        } else if (isTimedOut) {
            renewSession(event.eventTime, StartReason.MAX_DURATION)
        }

        updateSessionStateForSessionReplay(sessionState, sessionId)
    }

    private fun renewSession(time: Time, reason: StartReason) {
        val keepSession = random.nextFloat() < sampleRate.percent()
        startReason = reason
        sessionState = if (keepSession) State.TRACKED else State.NOT_TRACKED
        sessionId = UUID.randomUUID().toString()
        sessionStartNs.set(time.nanoTime)
        rumSessionScopeStartupManager = rumSessionScopeStartupManagerFactory()
        childScope?.renewViewScopes(time)
        if (keepSession) {
            sessionEndedMetricDispatcher.startMetric(
                sessionId = sessionId,
                startReason = reason,
                ntpOffsetAtStartMs = sdkCore.time.serverTimeOffsetMs,
                backgroundEventTracking = backgroundTrackingEnabled
            )
        }
        sessionListener?.onSessionStarted(sessionId, !keepSession)
    }

    private fun updateSessionStateForSessionReplay(state: State, sessionId: String) {
        val keepSession = (state == State.TRACKED)
        sdkCore.getFeature(Feature.SESSION_REPLAY_FEATURE_NAME)?.sendEvent(
            mapOf(
                SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY to RUM_SESSION_RENEWED_BUS_MESSAGE,
                RUM_KEEP_SESSION_BUS_MESSAGE_KEY to keepSession,
                RUM_SESSION_ID_BUS_MESSAGE_KEY to sessionId
            )
        )
    }

    // endregion

    companion object {

        internal const val SESSION_REPLAY_BUS_MESSAGE_TYPE_KEY = "type"
        internal const val RUM_SESSION_RENEWED_BUS_MESSAGE = "rum_session_renewed"
        internal const val RUM_KEEP_SESSION_BUS_MESSAGE_KEY = "keepSession"
        internal const val RUM_SESSION_ID_BUS_MESSAGE_KEY = "sessionId"
        internal val DEFAULT_SESSION_INACTIVITY_NS = TimeUnit.MINUTES.toNanos(15)
        internal val DEFAULT_SESSION_MAX_DURATION_NS = TimeUnit.HOURS.toNanos(4)
    }
}
