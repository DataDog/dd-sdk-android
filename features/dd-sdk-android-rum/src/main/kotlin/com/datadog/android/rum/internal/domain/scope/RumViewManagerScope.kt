/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import android.app.ActivityManager
import androidx.annotation.WorkerThread
import com.datadog.android.core.internal.net.FirstPartyHostHeaderTypeResolver
import com.datadog.android.rum.DdRumContentProvider
import com.datadog.android.rum.internal.AppStartTimeProvider
import com.datadog.android.rum.internal.DefaultAppStartTimeProvider
import com.datadog.android.rum.internal.anr.ANRException
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.vitals.NoOpVitalMonitor
import com.datadog.android.rum.internal.vitals.VitalMonitor
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.core.InternalSdkCore
import com.datadog.android.v2.core.storage.DataWriter
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit

internal class RumViewManagerScope(
    private val parentScope: RumScope,
    private val sdkCore: InternalSdkCore,
    private val backgroundTrackingEnabled: Boolean,
    private val trackFrustrations: Boolean,
    private val viewChangedListener: RumViewChangedListener?,
    internal val firstPartyHostHeaderTypeResolver: FirstPartyHostHeaderTypeResolver,
    private val cpuVitalMonitor: VitalMonitor,
    private val memoryVitalMonitor: VitalMonitor,
    private val frameRateVitalMonitor: VitalMonitor,
    private val appStartTimeProvider: AppStartTimeProvider = DefaultAppStartTimeProvider(),
    internal var applicationDisplayed: Boolean
) : RumScope {

    internal val childrenScopes = mutableListOf<RumScope>()
    internal var stopped = false

    // region RumScope

    @WorkerThread
    override fun handleEvent(event: RumRawEvent, writer: DataWriter<Any>): RumScope? {
        val canDisplayApplication = !stopped && event !is RumRawEvent.StopSession
        if (!applicationDisplayed && canDisplayApplication) {
            val processImportance = DdRumContentProvider.processImportance
            val isForegroundProcess = processImportance ==
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
            if (isForegroundProcess) {
                startApplicationLaunchView(event, writer)
            }
        }

        delegateToChildren(event, writer)

        if (event is RumRawEvent.StartView && !stopped) {
            startForegroundView(event)
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
    private fun startApplicationLaunchView(event: RumRawEvent, writer: DataWriter<Any>) {
        val processStartTime = appStartTimeProvider.appStartTimeNs
        // processStartTime is the time in nanoseconds since VM start. To get a timestamp, we want
        // to convert it to milliseconds since epoch provided by System.currentTimeMillis.
        // To do so, we take the offset of those times in the event time, which should be consistent,
        // then add that to our processStartTime to get the correct value.
        val timestampNs = (
            TimeUnit.MILLISECONDS.toNanos(event.eventTime.timestamp) -
                event.eventTime.nanoTime
            ) + processStartTime
        val applicationLaunchViewTime = Time(
            timestamp = TimeUnit.NANOSECONDS.toMillis(timestampNs),
            nanoTime = processStartTime
        )
        val viewScope = createAppLaunchViewScope(applicationLaunchViewTime)
        val startupTime = event.eventTime.nanoTime - processStartTime
        applicationDisplayed = true
        viewScope.handleEvent(
            RumRawEvent.ApplicationStarted(applicationLaunchViewTime, startupTime),
            writer
        )
        childrenScopes.add(viewScope)
    }

    @WorkerThread
    private fun delegateToChildren(
        event: RumRawEvent,
        writer: DataWriter<Any>
    ) {
        val iterator = childrenScopes.iterator()
        @Suppress("UnsafeThirdPartyFunctionCall") // next/remove can't fail: we checked hasNext
        while (iterator.hasNext()) {
            val result = iterator.next().handleEvent(event, writer)
            if (result == null) {
                iterator.remove()
            }
        }
    }

    @WorkerThread
    private fun handleOrphanEvent(event: RumRawEvent, writer: DataWriter<Any>) {
        val processFlag = DdRumContentProvider.processImportance
        val importanceForeground = ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        val isForegroundProcess = processFlag == importanceForeground

        if (applicationDisplayed || !isForegroundProcess) {
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
    }

    @WorkerThread
    private fun startForegroundView(event: RumRawEvent.StartView) {
        val viewScope = RumViewScope.fromEvent(
            this,
            sdkCore,
            event,
            viewChangedListener,
            firstPartyHostHeaderTypeResolver,
            cpuVitalMonitor,
            memoryVitalMonitor,
            frameRateVitalMonitor,
            trackFrustrations
        )
        applicationDisplayed = true
        childrenScopes.add(viewScope)
        viewChangedListener?.onViewChanged(
            RumViewInfo(
                keyRef = WeakReference(event.key),
                name = event.name,
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
        } else if (!isSilentOrphanEvent) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                { MESSAGE_MISSING_VIEW }
            )
        }
    }

    private fun createBackgroundViewScope(event: RumRawEvent): RumViewScope {
        return RumViewScope(
            this,
            sdkCore,
            RUM_BACKGROUND_VIEW_URL,
            RUM_BACKGROUND_VIEW_NAME,
            event.eventTime,
            emptyMap(),
            viewChangedListener,
            firstPartyHostHeaderTypeResolver,
            NoOpVitalMonitor(),
            NoOpVitalMonitor(),
            NoOpVitalMonitor(),
            type = RumViewScope.RumViewType.BACKGROUND,
            trackFrustrations = trackFrustrations
        )
    }

    private fun createAppLaunchViewScope(time: Time): RumViewScope {
        return RumViewScope(
            this,
            sdkCore,
            RUM_APP_LAUNCH_VIEW_URL,
            RUM_APP_LAUNCH_VIEW_NAME,
            time,
            emptyMap(),
            viewChangedListener,
            firstPartyHostHeaderTypeResolver,
            NoOpVitalMonitor(),
            NoOpVitalMonitor(),
            NoOpVitalMonitor(),
            type = RumViewScope.RumViewType.APPLICATION_LAUNCH,
            trackFrustrations = trackFrustrations
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
            RumRawEvent.ResourceSent::class.java
        )

        internal const val RUM_BACKGROUND_VIEW_URL = "com/datadog/background/view"
        internal const val RUM_BACKGROUND_VIEW_NAME = "Background"

        internal const val RUM_APP_LAUNCH_VIEW_URL = "com/datadog/application-launch/view"
        internal const val RUM_APP_LAUNCH_VIEW_NAME = "ApplicationLaunch"

        internal const val MESSAGE_MISSING_VIEW =
            "A RUM event was detected, but no view is active. " +
                "To track views automatically, try calling the " +
                "Configuration.Builder.useViewTrackingStrategy() method.\n" +
                "You can also track views manually using the RumMonitor.startView() and " +
                "RumMonitor.stopView() methods."
    }
}
