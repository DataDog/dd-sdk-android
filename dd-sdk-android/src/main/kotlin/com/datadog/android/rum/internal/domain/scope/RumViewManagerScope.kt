/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.os.Build
import android.os.Process
import android.os.SystemClock
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.net.FirstPartyHostDetector
import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.internal.system.BuildSdkVersionProvider
import com.datadog.android.core.internal.system.DefaultBuildSdkVersionProvider
import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.event.RumEventSourceProvider
import com.datadog.android.rum.internal.vitals.NoOpVitalMonitor
import com.datadog.android.rum.internal.vitals.VitalMonitor
import com.datadog.android.v2.core.internal.ContextProvider
import java.util.concurrent.TimeUnit

internal class RumViewManagerScope(
    private val parentScope: RumScope,
    private val backgroundTrackingEnabled: Boolean,
    private val trackFrustrations: Boolean,
    internal val firstPartyHostDetector: FirstPartyHostDetector,
    private val cpuVitalMonitor: VitalMonitor,
    private val memoryVitalMonitor: VitalMonitor,
    private val frameRateVitalMonitor: VitalMonitor,
    private val rumEventSourceProvider: RumEventSourceProvider,
    private val buildSdkVersionProvider: BuildSdkVersionProvider = DefaultBuildSdkVersionProvider(),
    private val contextProvider: ContextProvider
) : RumScope {

    internal val childrenScopes = mutableListOf<RumScope>()
    internal var applicationDisplayed = false

    // region RumScope

    @WorkerThread
    override fun handleEvent(event: RumRawEvent, writer: DataWriter<Any>): RumScope {
        delegateToChildren(event, writer)

        if (event is RumRawEvent.StartView) {
            startForegroundView(event, writer)
        } else if (childrenScopes.count { it.isActive() } == 0) {
            handleOrphanEvent(event, writer)
        }

        return this
    }

    override fun getRumContext(): RumContext {
        return parentScope.getRumContext()
    }

    override fun isActive(): Boolean {
        return true
    }

    // endregion

    // region Internal

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
        val processFlag = CoreFeature.processImportance
        val importanceForeground = ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        val isForegroundProcess = processFlag == importanceForeground

        if (applicationDisplayed || !isForegroundProcess) {
            handleBackgroundEvent(event, writer)
        } else {
            handleAppLaunchEvent(event, writer)
        }
    }

    @WorkerThread
    private fun startForegroundView(event: RumRawEvent.StartView, writer: DataWriter<Any>) {
        val viewScope = RumViewScope.fromEvent(
            this,
            event,
            firstPartyHostDetector,
            cpuVitalMonitor,
            memoryVitalMonitor,
            frameRateVitalMonitor,
            rumEventSourceProvider,
            contextProvider,
            trackFrustrations
        )
        onViewDisplayed(event, viewScope, writer)
        childrenScopes.add(viewScope)
    }

    @WorkerThread
    private fun handleBackgroundEvent(
        event: RumRawEvent,
        writer: DataWriter<Any>
    ) {
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
            devLogger.w(MESSAGE_MISSING_VIEW)
        }
    }

    @WorkerThread
    private fun handleAppLaunchEvent(
        event: RumRawEvent,
        actualWriter: DataWriter<Any>
    ) {
        val isValidAppLaunchEvent = event.javaClass in validAppLaunchEventTypes
        val isSilentOrphanEvent = event.javaClass in silentOrphanEventTypes

        if (isValidAppLaunchEvent) {
            val viewScope = createAppLaunchViewScope(event)
            viewScope.handleEvent(event, actualWriter)
            childrenScopes.add(viewScope)
        } else if (!isSilentOrphanEvent) {
            devLogger.w(MESSAGE_MISSING_VIEW)
        }
    }

    private fun createBackgroundViewScope(event: RumRawEvent): RumViewScope {
        return RumViewScope(
            this,
            RUM_BACKGROUND_VIEW_URL,
            RUM_BACKGROUND_VIEW_NAME,
            event.eventTime,
            emptyMap(),
            firstPartyHostDetector,
            NoOpVitalMonitor(),
            NoOpVitalMonitor(),
            NoOpVitalMonitor(),
            rumEventSourceProvider,
            type = RumViewScope.RumViewType.BACKGROUND,
            contextProvider = contextProvider,
            trackFrustrations = trackFrustrations
        )
    }

    private fun createAppLaunchViewScope(event: RumRawEvent): RumViewScope {
        return RumViewScope(
            this,
            RUM_APP_LAUNCH_VIEW_URL,
            RUM_APP_LAUNCH_VIEW_NAME,
            event.eventTime,
            emptyMap(),
            firstPartyHostDetector,
            NoOpVitalMonitor(),
            NoOpVitalMonitor(),
            NoOpVitalMonitor(),
            rumEventSourceProvider,
            type = RumViewScope.RumViewType.APPLICATION_LAUNCH,
            contextProvider = contextProvider,
            trackFrustrations = trackFrustrations
        )
    }

    @WorkerThread
    @VisibleForTesting
    internal fun onViewDisplayed(
        event: RumRawEvent.StartView,
        viewScope: RumViewScope,
        writer: DataWriter<Any>
    ) {
        if (!applicationDisplayed) {
            applicationDisplayed = true
            val isForegroundProcess = CoreFeature.processImportance ==
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
            if (isForegroundProcess) {
                val applicationStartTime = resolveStartupTimeNs()
                viewScope.handleEvent(
                    RumRawEvent.ApplicationStarted(event.eventTime, applicationStartTime),
                    writer
                )
            }
        }
    }

    @SuppressLint("NewApi")
    private fun resolveStartupTimeNs(): Long {
        return when {
            buildSdkVersionProvider.version() >= Build.VERSION_CODES.N -> {
                val diffMs = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
                System.nanoTime() - TimeUnit.MILLISECONDS.toNanos(diffMs)
            }
            else -> RumFeature.startupTimeNs
        }
    }

    // endregion

    companion object {

        internal val validBackgroundEventTypes = arrayOf<Class<*>>(
            RumRawEvent.AddError::class.java,
            RumRawEvent.StartAction::class.java,
            RumRawEvent.StartResource::class.java
        )
        internal val validAppLaunchEventTypes = arrayOf<Class<*>>(
            RumRawEvent.AddError::class.java,
            RumRawEvent.AddLongTask::class.java,
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
