/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import android.app.ActivityManager
import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.internal.net.FirstPartyHostHeaderTypeResolver
import com.datadog.android.core.metrics.MethodCallSamplingRate
import com.datadog.android.internal.telemetry.InternalTelemetryEvent
import com.datadog.android.rum.DdRumContentProvider
import com.datadog.android.rum.internal.anr.ANRException
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.metric.SessionEndedMetric
import com.datadog.android.rum.internal.metric.SessionMetricDispatcher
import com.datadog.android.rum.internal.metric.ViewEndedMetricDispatcher
import com.datadog.android.rum.internal.metric.ViewMetricDispatcher
import com.datadog.android.rum.internal.metric.interactiontonextview.InteractionToNextViewMetricResolver
import com.datadog.android.rum.internal.metric.networksettled.NetworkSettledMetricResolver
import com.datadog.android.rum.internal.vitals.NoOpVitalMonitor
import com.datadog.android.rum.internal.vitals.VitalMonitor
import com.datadog.android.rum.metric.interactiontonextview.LastInteractionIdentifier
import com.datadog.android.rum.metric.networksettled.InitialResourceIdentifier
import java.util.Locale
import java.util.concurrent.TimeUnit

@Suppress("LongParameterList")
internal class RumViewManagerScope(
    private val parentScope: RumScope,
    private val sdkCore: InternalSdkCore,
    private val sessionEndedMetricDispatcher: SessionMetricDispatcher,
    private val backgroundTrackingEnabled: Boolean,
    private val trackFrustrations: Boolean,
    private val viewChangedListener: RumViewChangedListener?,
    internal val firstPartyHostHeaderTypeResolver: FirstPartyHostHeaderTypeResolver,
    private val cpuVitalMonitor: VitalMonitor,
    private val memoryVitalMonitor: VitalMonitor,
    private val frameRateVitalMonitor: VitalMonitor,
    internal var applicationDisplayed: Boolean,
    internal val sampleRate: Float,
    internal val initialResourceIdentifier: InitialResourceIdentifier,
    lastInteractionIdentifier: LastInteractionIdentifier
) : RumScope {

    private val interactionToNextViewMetricResolver: InteractionToNextViewMetricResolver =
        InteractionToNextViewMetricResolver(
            internalLogger = sdkCore.internalLogger,
            lastInteractionIdentifier = lastInteractionIdentifier
        )

    internal val childrenScopes = mutableListOf<RumScope>()
    internal var stopped = false
    private var lastStoppedViewTime: Time? = null

    // region RumScope

    @WorkerThread
    override fun handleEvent(event: RumRawEvent, writer: DataWriter<Any>): RumScope? {
        if (event is RumRawEvent.ApplicationStarted &&
            !applicationDisplayed &&
            !stopped
        ) {
            startApplicationLaunchView(event, writer)
            return this
        }

        delegateToChildren(event, writer)

        if (event is RumRawEvent.StartView && !stopped) {
            startForegroundView(event, writer)
            lastStoppedViewTime?.let {
                val gap = event.eventTime.nanoTime - it.nanoTime
                if (gap in 1 until THREE_SECONDS_GAP_NS) {
                    sdkCore.internalLogger.logMetric(
                        messageBuilder = { MESSAGE_GAP_BETWEEN_VIEWS.format(Locale.US, gap) },
                        additionalProperties = mapOf(ATTR_GAP_BETWEEN_VIEWS to gap),
                        samplingRate = MethodCallSamplingRate.MEDIUM.rate
                    )
                } else if (gap < 0) {
                    sdkCore.internalLogger.logMetric(
                        messageBuilder = { MESSAGE_NEG_GAP_BETWEEN_VIEWS.format(Locale.US, gap) },
                        additionalProperties = mapOf(ATTR_GAP_BETWEEN_VIEWS to gap),
                        samplingRate = MethodCallSamplingRate.MEDIUM.rate
                    )
                }
            }
            lastStoppedViewTime = null
        } else if (event is RumRawEvent.StopSession) {
            stopped = true
        } else if (childrenScopes.count { it.isActive() } == 0) {
            handleOrphanEvent(event, writer)
        }

        return if (isViewManagerComplete()) {
            null
        } else {
            this
        }
    }

    override fun getRumContext(): RumContext {
        return parentScope.getRumContext()
    }

    override fun isActive(): Boolean {
        return !stopped
    }

    // endregion

    // region Internal

    private fun isViewManagerComplete(): Boolean {
        return stopped && childrenScopes.isEmpty()
    }

    @WorkerThread
    private fun startApplicationLaunchView(
        event: RumRawEvent.ApplicationStarted,
        writer: DataWriter<Any>
    ) {
        val viewScope = createAppLaunchViewScope(event.eventTime)
        applicationDisplayed = true
        viewScope.handleEvent(event, writer)
        childrenScopes.add(viewScope)
    }

    @WorkerThread
    private fun delegateToChildren(
        event: RumRawEvent,
        writer: DataWriter<Any>
    ) {
        val hasNoView = childrenScopes.isEmpty()
        val iterator = childrenScopes.iterator()
        var hasActiveView = false
        @Suppress("UnsafeThirdPartyFunctionCall") // next/remove can't fail: we checked hasNext
        while (iterator.hasNext()) {
            val childScope = iterator.next()
            hasActiveView = hasActiveView or childScope.isActive()
            if (event is RumRawEvent.StopView) {
                if (childScope.isActive() && (childScope as? RumViewScope)?.key?.id == event.key.id) {
                    lastStoppedViewTime = event.eventTime
                }
            }
            val result = childScope.handleEvent(event, writer)
            if (result == null) {
                iterator.remove()
            }
        }

        if (event is RumRawEvent.AddViewLoadingTime && !hasActiveView) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                { NO_ACTIVE_VIEW_FOR_LOADING_TIME_WARNING_MESSAGE }
            )
            sdkCore.internalLogger.logApiUsage {
                InternalTelemetryEvent.ApiUsage.AddViewLoadingTime(
                    overwrite = event.overwrite,
                    noView = hasNoView,
                    noActiveView = !hasNoView
                )
            }
        }
    }

    @WorkerThread
    private fun handleOrphanEvent(event: RumRawEvent, writer: DataWriter<Any>) {
        val processFlag = DdRumContentProvider.processImportance
        val importanceForeground = ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        val isForegroundProcess = processFlag == importanceForeground

        if (event is RumRawEvent.AddViewLoadingTime) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                { MESSAGE_MISSING_VIEW }
            )
            // we should return here and not add the event to the session ended metric missed events as we already
            // send the API usage telemetry
            return
        } else if (applicationDisplayed || !isForegroundProcess) {
            handleBackgroundEvent(event, writer)
        } else {
            val isSilentOrphanEvent = event.javaClass in silentOrphanEventTypes
            if (!isSilentOrphanEvent) {
                sdkCore.internalLogger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.USER,
                    { MESSAGE_MISSING_VIEW }
                )
            }
        }

        // Track the orphan event both in foreground and background.
        SessionEndedMetric.MissedEventType.fromRawEvent(rawEvent = event)?.let {
            sessionEndedMetricDispatcher.onMissedEventTracked(sessionId = parentScope.getRumContext().sessionId, it)
        } ?: sdkCore.internalLogger.log(
            InternalLogger.Level.INFO,
            InternalLogger.Target.MAINTAINER,
            { MESSAGE_UNKNOWN_MISSED_TYPE }
        )
    }

    @WorkerThread
    private fun startForegroundView(event: RumRawEvent.StartView, writer: DataWriter<Any>) {
        val viewScope = RumViewScope.fromEvent(
            this,
            sessionEndedMetricDispatcher,
            sdkCore,
            event,
            viewChangedListener,
            firstPartyHostHeaderTypeResolver,
            cpuVitalMonitor,
            memoryVitalMonitor,
            frameRateVitalMonitor,
            trackFrustrations,
            sampleRate,
            interactionToNextViewMetricResolver,
            initialResourceIdentifier
        )
        applicationDisplayed = true
        childrenScopes.add(viewScope)
        viewScope.handleEvent(RumRawEvent.KeepAlive(), writer)
        viewChangedListener?.onViewChanged(
            RumViewInfo(
                key = event.key,
                attributes = event.attributes,
                isActive = true
            )
        )
    }

    @WorkerThread
    private fun handleBackgroundEvent(
        event: RumRawEvent,
        writer: DataWriter<Any>
    ) {
        if (event is RumRawEvent.AddError && event.throwable is ANRException) {
            // RUMM-2931 ignore ANR detected when the app is not in foreground
            return
        }
        val isValidBackgroundEvent = event.javaClass in validBackgroundEventTypes
        val isSilentOrphanEvent = event.javaClass in silentOrphanEventTypes

        if (isValidBackgroundEvent && backgroundTrackingEnabled) {
            // there is no active ViewScope to handle this event. We will assume the application
            // is in background and we will create a special ViewScope (background)
            // to handle all the events.
            val viewScope = createBackgroundViewScope(event)
            viewScope.handleEvent(event, writer)
            childrenScopes.add(viewScope)
            lastStoppedViewTime = null
        } else if (!isSilentOrphanEvent) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                { MESSAGE_MISSING_VIEW }
            )
        }
    }

    private fun createBackgroundViewScope(event: RumRawEvent): RumViewScope {
        val networkSettledMetricResolver = NetworkSettledMetricResolver(
            initialResourceIdentifier,
            sdkCore.internalLogger
        )

        val viewEndedMetricDispatcher = ViewEndedMetricDispatcher(
            viewType = ViewMetricDispatcher.ViewType.BACKGROUND,
            internalLogger = sdkCore.internalLogger
        )

        return RumViewScope(
            this,
            sdkCore,
            sessionEndedMetricDispatcher,
            RumScopeKey(
                RUM_BACKGROUND_VIEW_ID,
                RUM_BACKGROUND_VIEW_URL,
                RUM_BACKGROUND_VIEW_NAME
            ),
            event.eventTime,
            emptyMap(),
            viewChangedListener,
            firstPartyHostHeaderTypeResolver,
            NoOpVitalMonitor(),
            NoOpVitalMonitor(),
            NoOpVitalMonitor(),
            type = RumViewScope.RumViewType.BACKGROUND,
            trackFrustrations = trackFrustrations,
            sampleRate = sampleRate,
            interactionToNextViewMetricResolver = interactionToNextViewMetricResolver,
            networkSettledMetricResolver = networkSettledMetricResolver,
            viewEndedMetricDispatcher = viewEndedMetricDispatcher
        )
    }

    private fun createAppLaunchViewScope(time: Time): RumViewScope {
        val networkSettledMetricResolver = NetworkSettledMetricResolver(
            initialResourceIdentifier,
            sdkCore.internalLogger
        )

        val viewEndedMetricDispatcher = ViewEndedMetricDispatcher(
            viewType = ViewMetricDispatcher.ViewType.APPLICATION,
            internalLogger = sdkCore.internalLogger
        )

        return RumViewScope(
            this,
            sdkCore,
            sessionEndedMetricDispatcher,
            RumScopeKey(
                RUM_APP_LAUNCH_VIEW_ID,
                RUM_APP_LAUNCH_VIEW_URL,
                RUM_APP_LAUNCH_VIEW_NAME
            ),
            time,
            emptyMap(),
            viewChangedListener,
            firstPartyHostHeaderTypeResolver,
            NoOpVitalMonitor(),
            NoOpVitalMonitor(),
            NoOpVitalMonitor(),
            type = RumViewScope.RumViewType.APPLICATION_LAUNCH,
            trackFrustrations = trackFrustrations,
            sampleRate = sampleRate,
            interactionToNextViewMetricResolver = interactionToNextViewMetricResolver,
            networkSettledMetricResolver = networkSettledMetricResolver,
            viewEndedMetricDispatcher = viewEndedMetricDispatcher
        )
    }

    // endregion

    companion object {

        internal val validBackgroundEventTypes = arrayOf<Class<*>>(
            RumRawEvent.AddError::class.java,
            RumRawEvent.StartAction::class.java,
            RumRawEvent.StartResource::class.java
        )

        internal val silentOrphanEventTypes = arrayOf<Class<*>>(
            RumRawEvent.ApplicationStarted::class.java,
            RumRawEvent.KeepAlive::class.java,
            RumRawEvent.ResetSession::class.java,
            RumRawEvent.StopView::class.java,
            RumRawEvent.ActionDropped::class.java,
            RumRawEvent.ActionSent::class.java,
            RumRawEvent.ErrorDropped::class.java,
            RumRawEvent.ErrorSent::class.java,
            RumRawEvent.LongTaskDropped::class.java,
            RumRawEvent.LongTaskSent::class.java,
            RumRawEvent.ResourceDropped::class.java,
            RumRawEvent.ResourceSent::class.java,
            RumRawEvent.UpdatePerformanceMetric::class.java
        )

        internal const val RUM_BACKGROUND_VIEW_ID = "com.datadog.background.view"
        internal const val RUM_BACKGROUND_VIEW_URL = "com/datadog/background/view"
        internal const val RUM_BACKGROUND_VIEW_NAME = "Background"

        internal const val RUM_APP_LAUNCH_VIEW_ID = "com.datadog.application-launch.view"
        internal const val RUM_APP_LAUNCH_VIEW_URL = "com/datadog/application-launch/view"
        internal const val RUM_APP_LAUNCH_VIEW_NAME = "ApplicationLaunch"

        private const val MESSAGE_GAP_BETWEEN_VIEWS = "[Mobile Metric] Gap between views"
        private const val MESSAGE_NEG_GAP_BETWEEN_VIEWS = "[Mobile Metric] Negative gap between views"
        internal const val ATTR_GAP_BETWEEN_VIEWS = "view_gap"

        internal const val MESSAGE_MISSING_VIEW =
            "A RUM event was detected, but no view is active. " +
                "To track views automatically, try calling the " +
                "RumConfiguration.Builder.useViewTrackingStrategy() method.\n" +
                "You can also track views manually using the RumMonitor.startView() and " +
                "RumMonitor.stopView() methods."

        internal const val MESSAGE_UNKNOWN_MISSED_TYPE = "An RUM event was detected, but no view is active, " +
            "its missed type is unknown"

        internal const val NO_ACTIVE_VIEW_FOR_LOADING_TIME_WARNING_MESSAGE =
            "No active view found to add the loading time."

        internal val THREE_SECONDS_GAP_NS = TimeUnit.SECONDS.toNanos(3)
    }
}
