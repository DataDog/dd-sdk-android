/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.fragment.app.Fragment
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.net.FirstPartyHostDetector
import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.core.internal.utils.loggableStackTrace
import com.datadog.android.core.internal.utils.resolveViewUrl
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.domain.event.RumEvent
import com.datadog.android.rum.internal.vitals.VitalInfo
import com.datadog.android.rum.internal.vitals.VitalListener
import com.datadog.android.rum.internal.vitals.VitalMonitor
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ViewEvent
import java.lang.ref.Reference
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.max

internal open class RumViewScope(
    private val parentScope: RumScope,
    key: Any,
    internal val name: String,
    eventTime: Time,
    initialAttributes: Map<String, Any?>,
    internal val firstPartyHostDetector: FirstPartyHostDetector,
    internal val cpuVitalMonitor: VitalMonitor,
    internal val memoryVitalMonitor: VitalMonitor,
    internal val frameRateVitalMonitor: VitalMonitor,
    internal val timeProvider: TimeProvider
) : RumScope {

    internal val url = key.resolveViewUrl().replace('.', '/')

    internal val keyRef: Reference<Any> = WeakReference(key)
    internal val attributes: MutableMap<String, Any?> = initialAttributes.toMutableMap()

    private var sessionId: String = parentScope.getRumContext().sessionId
    internal var viewId: String = UUID.randomUUID().toString()
        private set
    private val startedNanos: Long = eventTime.nanoTime

    internal val serverTimeOffsetInMs = timeProvider.getServerOffsetMillis()
    internal val eventTimestamp = eventTime.timestamp + serverTimeOffsetInMs

    internal var activeActionScope: RumScope? = null
    internal val activeResourceScopes = mutableMapOf<String, RumScope>()

    private var resourceCount: Long = 0
    private var actionCount: Long = 0
    private var errorCount: Long = 0
    private var crashCount: Long = 0
    private var longTaskCount: Long = 0
    private var frozenFrameCount: Long = 0

    internal var pendingResourceCount: Long = 0
    internal var pendingActionCount: Long = 0
    internal var pendingErrorCount: Long = 0
    internal var pendingLongTaskCount: Long = 0
    internal var pendingFrozenFrameCount: Long = 0

    private var version: Long = 1
    private var loadingTime: Long? = null
    private var loadingType: ViewEvent.LoadingType? = null
    private val customTimings: MutableMap<String, Long> = mutableMapOf()

    internal var stopped: Boolean = false

    // region Vitals Fields

    private var cpuTicks: Double? = null
    private var cpuVitalListener: VitalListener = object : VitalListener {
        private var initialTickCount: Double = Double.NaN
        override fun onVitalUpdate(info: VitalInfo) {
            // The CPU Ticks will always grow, as it's the total ticks since the app started
            if (initialTickCount.isNaN()) {
                initialTickCount = info.maxValue
            } else {
                cpuTicks = info.maxValue - initialTickCount
            }
        }
    }

    private var lastMemoryInfo: VitalInfo? = null
    private var memoryVitalListener: VitalListener = object : VitalListener {
        override fun onVitalUpdate(info: VitalInfo) {
            lastMemoryInfo = info
        }
    }

    private var refreshRateScale: Double = 1.0
    private var lastFrameRateInfo: VitalInfo? = null
    private var frameRateVitalListenr: VitalListener = object : VitalListener {
        override fun onVitalUpdate(info: VitalInfo) {
            lastFrameRateInfo = info
        }
    }

    // endregion

    init {
        GlobalRum.updateRumContext(getRumContext())
        attributes.putAll(GlobalRum.globalAttributes)
        cpuVitalMonitor.register(cpuVitalListener)
        memoryVitalMonitor.register(memoryVitalListener)
        frameRateVitalMonitor.register(frameRateVitalListenr)

        detectRefreshRateScale(key)
    }

    // region RumScope

    override fun handleEvent(
        event: RumRawEvent,
        writer: DataWriter<RumEvent>
    ): RumScope? {
        when (event) {
            is RumRawEvent.ResourceSent -> onResourceSent(event, writer)
            is RumRawEvent.ActionSent -> onActionSent(event, writer)
            is RumRawEvent.ErrorSent -> onErrorSent(event, writer)
            is RumRawEvent.LongTaskSent -> onLongTaskSent(event, writer)

            is RumRawEvent.ResourceDropped -> onResourceDropped(event)
            is RumRawEvent.ActionDropped -> onActionDropped(event)
            is RumRawEvent.ErrorDropped -> onErrorDropped(event)
            is RumRawEvent.LongTaskDropped -> onLongTaskDropped(event)

            is RumRawEvent.StartView -> onStartView(event, writer)
            is RumRawEvent.StopView -> onStopView(event, writer)

            is RumRawEvent.StartAction -> onStartAction(event, writer)
            is RumRawEvent.StartResource -> onStartResource(event, writer)
            is RumRawEvent.AddError -> onAddError(event, writer)
            is RumRawEvent.AddLongTask -> onAddLongTask(event, writer)

            is RumRawEvent.ApplicationStarted -> onApplicationStarted(event, writer)
            is RumRawEvent.UpdateViewLoadingTime -> onUpdateViewLoadingTime(event, writer)
            is RumRawEvent.AddCustomTiming -> onAddCustomTiming(event, writer)
            is RumRawEvent.KeepAlive -> onKeepAlive(event, writer)

            else -> delegateEventToChildren(event, writer)
        }

        return if (isViewComplete()) {
            null
        } else {
            this
        }
    }

    override fun getRumContext(): RumContext {
        val parentContext = parentScope.getRumContext()
        if (parentContext.sessionId != sessionId) {
            sessionId = parentContext.sessionId
            viewId = UUID.randomUUID().toString()
        }

        return parentContext
            .copy(
                viewId = viewId,
                viewName = name,
                viewUrl = url,
                actionId = (activeActionScope as? RumActionScope)?.actionId
            )
    }

    // endregion

    // region Internal

    private fun onStartView(
        event: RumRawEvent.StartView,
        writer: DataWriter<RumEvent>
    ) {
        if (!stopped) {
            stopped = true
            sendViewUpdate(event, writer)
            delegateEventToChildren(event, writer)
        }
    }

    private fun onStopView(
        event: RumRawEvent.StopView,
        writer: DataWriter<RumEvent>
    ) {
        delegateEventToChildren(event, writer)
        val startedKey = keyRef.get()
        val shouldStop = (event.key == startedKey) || (startedKey == null)
        if (shouldStop && !stopped) {
            attributes.putAll(event.attributes)
            stopped = true
            sendViewUpdate(event, writer)
        }
    }

    private fun onStartAction(
        event: RumRawEvent.StartAction,
        writer: DataWriter<RumEvent>
    ) {
        delegateEventToChildren(event, writer)

        if (stopped) return

        if (activeActionScope != null) {
            if (event.type == RumActionType.CUSTOM && !event.waitForStop) {
                // deliver it anyway, even if there is active action ongoing
                val customActionScope = RumActionScope.fromEvent(this, event, serverTimeOffsetInMs)
                pendingActionCount++
                customActionScope.handleEvent(RumRawEvent.SendCustomActionNow(), writer)
                return
            } else {
                devLogger.w(ACTION_DROPPED_WARNING.format(Locale.US, event.type, event.name))
                return
            }
        }

        activeActionScope = RumActionScope.fromEvent(this, event, serverTimeOffsetInMs)
        pendingActionCount++
    }

    private fun onStartResource(
        event: RumRawEvent.StartResource,
        writer: DataWriter<RumEvent>
    ) {
        delegateEventToChildren(event, writer)
        if (stopped) return

        val updatedEvent = event.copy(
            attributes = addExtraAttributes(event.attributes)
        )
        activeResourceScopes[event.key] = RumResourceScope.fromEvent(
            this,
            updatedEvent,
            firstPartyHostDetector,
            serverTimeOffsetInMs
        )
        pendingResourceCount++
    }

    private fun onAddError(
        event: RumRawEvent.AddError,
        writer: DataWriter<RumEvent>
    ) {
        delegateEventToChildren(event, writer)
        if (stopped) return

        val context = getRumContext()
        val user = CoreFeature.userInfoProvider.getUserInfo()
        val updatedAttributes = addExtraAttributes(event.attributes)
        val networkInfo = CoreFeature.networkInfoProvider.getLatestNetworkInfo()
        val errorType = event.type ?: event.throwable?.javaClass?.canonicalName
        val errorEvent = ErrorEvent(
            date = event.eventTime.timestamp + serverTimeOffsetInMs,
            error = ErrorEvent.Error(
                message = event.message,
                source = event.source.toSchemaSource(),
                stack = event.stacktrace ?: event.throwable?.loggableStackTrace(),
                isCrash = event.isFatal,
                type = errorType
            ),
            action = context.actionId?.let { ErrorEvent.Action(it) },
            view = ErrorEvent.View(
                id = context.viewId.orEmpty(),
                name = context.viewName,
                url = context.viewUrl.orEmpty()
            ),
            usr = ErrorEvent.Usr(
                id = user.id,
                name = user.name,
                email = user.email
            ),
            connectivity = networkInfo.toErrorConnectivity(),
            application = ErrorEvent.Application(context.applicationId),
            session = ErrorEvent.ErrorEventSession(
                id = context.sessionId,
                type = ErrorEvent.ErrorEventSessionType.USER
            ),
            dd = ErrorEvent.Dd(session = ErrorEvent.DdSession(plan = ErrorEvent.Plan.PLAN_1))
        )
        val rumEvent = RumEvent(
            event = errorEvent,
            globalAttributes = updatedAttributes,
            userExtraAttributes = user.additionalProperties
        )
        writer.write(rumEvent)
        if (event.isFatal) {
            errorCount++
            crashCount++
            sendViewUpdate(event, writer)
        } else {
            pendingErrorCount++
        }
    }

    private fun onAddCustomTiming(
        event: RumRawEvent.AddCustomTiming,
        writer: DataWriter<RumEvent>
    ) {
        customTimings[event.name] = max(event.eventTime.nanoTime - startedNanos, 1L)
        sendViewUpdate(event, writer)
    }

    private fun onKeepAlive(
        event: RumRawEvent.KeepAlive,
        writer: DataWriter<RumEvent>
    ) {
        delegateEventToChildren(event, writer)
        if (stopped) return

        sendViewUpdate(event, writer)
    }

    private fun delegateEventToChildren(
        event: RumRawEvent,
        writer: DataWriter<RumEvent>
    ) {
        delegateEventToResources(event, writer)
        delegateEventToAction(event, writer)
    }

    private fun delegateEventToAction(
        event: RumRawEvent,
        writer: DataWriter<RumEvent>
    ) {
        val currentAction = activeActionScope
        if (currentAction != null) {
            val updatedAction = currentAction.handleEvent(event, writer)
            if (updatedAction == null) {
                activeActionScope = null
            }
        }
    }

    private fun delegateEventToResources(
        event: RumRawEvent,
        writer: DataWriter<RumEvent>
    ) {
        val iterator = activeResourceScopes.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val scope = entry.value.handleEvent(event, writer)
            if (scope == null) {
                iterator.remove()
            }
        }
    }

    private fun onResourceSent(
        event: RumRawEvent.ResourceSent,
        writer: DataWriter<RumEvent>
    ) {
        if (event.viewId == viewId) {
            pendingResourceCount--
            resourceCount++
            sendViewUpdate(event, writer)
        }
    }

    private fun onActionSent(
        event: RumRawEvent.ActionSent,
        writer: DataWriter<RumEvent>
    ) {
        if (event.viewId == viewId) {
            pendingActionCount--
            actionCount++
            sendViewUpdate(event, writer)
        }
    }

    private fun onErrorSent(
        event: RumRawEvent.ErrorSent,
        writer: DataWriter<RumEvent>
    ) {
        if (event.viewId == viewId) {
            pendingErrorCount--
            errorCount++
            sendViewUpdate(event, writer)
        }
    }

    private fun onLongTaskSent(
        event: RumRawEvent.LongTaskSent,
        writer: DataWriter<RumEvent>
    ) {
        if (event.viewId == viewId) {
            pendingLongTaskCount--
            longTaskCount++
            if (event.isFrozenFrame) {
                pendingFrozenFrameCount--
                frozenFrameCount++
            }
            sendViewUpdate(event, writer)
        }
    }

    private fun onResourceDropped(event: RumRawEvent.ResourceDropped) {
        if (event.viewId == viewId) {
            pendingResourceCount--
        }
    }

    private fun onActionDropped(event: RumRawEvent.ActionDropped) {
        if (event.viewId == viewId) {
            pendingActionCount--
        }
    }

    private fun onErrorDropped(event: RumRawEvent.ErrorDropped) {
        if (event.viewId == viewId) {
            pendingErrorCount--
        }
    }

    private fun onLongTaskDropped(event: RumRawEvent.LongTaskDropped) {
        if (event.viewId == viewId) {
            pendingLongTaskCount--
            if (event.isFrozenFrame) {
                pendingFrozenFrameCount--
            }
        }
    }

    @Suppress("LongMethod")
    private fun sendViewUpdate(event: RumRawEvent, writer: DataWriter<RumEvent>) {
        attributes.putAll(GlobalRum.globalAttributes)
        version++
        val updatedDurationNs = event.eventTime.nanoTime - startedNanos
        val context = getRumContext()
        val user = CoreFeature.userInfoProvider.getUserInfo()
        val timings = if (customTimings.isNotEmpty()) {
            ViewEvent.CustomTimings(LinkedHashMap(customTimings))
        } else {
            null
        }

        val memoryInfo = lastMemoryInfo
        val refreshRateInfo = lastFrameRateInfo
        val isSlowRendered = if (refreshRateInfo == null) {
            null
        } else {
            refreshRateInfo.meanValue < SLOW_RENDERED_THRESHOLD_FPS
        }
        val viewEvent = ViewEvent(
            date = eventTimestamp,
            view = ViewEvent.View(
                id = context.viewId.orEmpty(),
                name = context.viewName.orEmpty(),
                url = context.viewUrl.orEmpty(),
                loadingTime = loadingTime,
                loadingType = loadingType,
                timeSpent = updatedDurationNs,
                action = ViewEvent.Action(actionCount),
                resource = ViewEvent.Resource(resourceCount),
                error = ViewEvent.Error(errorCount),
                crash = ViewEvent.Crash(crashCount),
                longTask = ViewEvent.LongTask(longTaskCount),
                frozenFrame = ViewEvent.FrozenFrame(frozenFrameCount),
                customTimings = timings,
                isActive = !stopped,
                cpuTicksCount = cpuTicks,
                cpuTicksPerSecond = cpuTicks?.let { (it * ONE_SECOND_NS) / updatedDurationNs },
                memoryAverage = memoryInfo?.meanValue,
                memoryMax = memoryInfo?.maxValue,
                refreshRateAverage = refreshRateInfo?.meanValue?.let { it * refreshRateScale },
                refreshRateMin = refreshRateInfo?.minValue?.let { it * refreshRateScale },
                isSlowRendered = isSlowRendered
            ),
            usr = ViewEvent.Usr(
                id = user.id,
                name = user.name,
                email = user.email
            ),
            application = ViewEvent.Application(context.applicationId),
            session = ViewEvent.ViewEventSession(
                id = context.sessionId,
                type = ViewEvent.Type.USER
            ),
            dd = ViewEvent.Dd(
                documentVersion = version,
                session = ViewEvent.DdSession(plan = ViewEvent.Plan.PLAN_1)
            )
        )

        val rumEvent = RumEvent(
            event = viewEvent,
            globalAttributes = attributes,
            userExtraAttributes = user.additionalProperties
        )
        writer.write(rumEvent)
    }

    private fun addExtraAttributes(
        attributes: Map<String, Any?>
    ): MutableMap<String, Any?> {
        return attributes.toMutableMap()
            .apply { putAll(GlobalRum.globalAttributes) }
    }

    private fun onUpdateViewLoadingTime(
        event: RumRawEvent.UpdateViewLoadingTime,
        writer: DataWriter<RumEvent>
    ) {
        val startedKey = keyRef.get()
        if (event.key != startedKey) {
            return
        }
        loadingTime = event.loadingTime
        loadingType = event.loadingType
        sendViewUpdate(event, writer)
    }

    private fun onApplicationStarted(
        event: RumRawEvent.ApplicationStarted,
        writer: DataWriter<RumEvent>
    ) {
        pendingActionCount++
        val context = getRumContext()
        val user = CoreFeature.userInfoProvider.getUserInfo()

        val actionEvent = ActionEvent(
            date = eventTimestamp,
            action = ActionEvent.Action(
                type = ActionEvent.ActionType.APPLICATION_START,
                id = UUID.randomUUID().toString(),
                loadingTime = getStartupTime(event)
            ),
            view = ActionEvent.View(
                id = context.viewId.orEmpty(),
                name = context.viewName,
                url = context.viewUrl.orEmpty()
            ),
            usr = ActionEvent.Usr(
                id = user.id,
                name = user.name,
                email = user.email
            ),
            application = ActionEvent.Application(context.applicationId),
            session = ActionEvent.ActionEventSession(
                id = context.sessionId,
                type = ActionEvent.ActionEventSessionType.USER
            ),
            dd = ActionEvent.Dd(session = ActionEvent.DdSession(ActionEvent.Plan.PLAN_1))
        )
        val rumEvent = RumEvent(
            event = actionEvent,
            globalAttributes = GlobalRum.globalAttributes,
            userExtraAttributes = user.additionalProperties
        )
        writer.write(rumEvent)
    }

    private fun getStartupTime(event: RumRawEvent.ApplicationStarted): Long {
        val now = event.eventTime.nanoTime
        val startupTime = event.applicationStartupNanos
        return max(now - startupTime, 1L)
    }

    private fun onAddLongTask(event: RumRawEvent.AddLongTask, writer: DataWriter<RumEvent>) {
        delegateEventToChildren(event, writer)
        if (stopped) return

        val context = getRumContext()
        val user = CoreFeature.userInfoProvider.getUserInfo()
        val updatedAttributes = addExtraAttributes(
            mapOf(RumAttributes.LONG_TASK_TARGET to event.target)
        )
        val networkInfo = CoreFeature.networkInfoProvider.getLatestNetworkInfo()
        val timestamp = event.eventTime.timestamp + serverTimeOffsetInMs
        val isFrozenFrame = event.durationNs > FROZEN_FRAME_THRESHOLD_NS
        val longTaskEvent = LongTaskEvent(
            date = timestamp - TimeUnit.NANOSECONDS.toMillis(event.durationNs),
            longTask = LongTaskEvent.LongTask(
                duration = event.durationNs,
                isFrozenFrame = isFrozenFrame
            ),
            action = context.actionId?.let { LongTaskEvent.Action(it) },
            view = LongTaskEvent.View(
                id = context.viewId.orEmpty(),
                name = context.viewName,
                url = context.viewUrl.orEmpty()
            ),
            usr = LongTaskEvent.Usr(
                id = user.id,
                name = user.name,
                email = user.email
            ),
            connectivity = networkInfo.toLongTaskConnectivity(),
            application = LongTaskEvent.Application(context.applicationId),
            session = LongTaskEvent.LongTaskEventSession(
                id = context.sessionId,
                type = LongTaskEvent.Type.USER
            ),
            dd = LongTaskEvent.Dd(session = LongTaskEvent.DdSession(LongTaskEvent.Plan.PLAN_1))
        )
        val rumEvent = RumEvent(
            event = longTaskEvent,
            globalAttributes = updatedAttributes,
            userExtraAttributes = user.additionalProperties
        )
        writer.write(rumEvent)
        pendingLongTaskCount++
        if (isFrozenFrame) pendingFrozenFrameCount++
    }

    private fun isViewComplete(): Boolean {
        val pending = pendingActionCount +
            pendingResourceCount +
            pendingErrorCount +
            pendingLongTaskCount
        // we use <= 0 for pending counter as a safety measure to make sure this ViewScope will
        // be closed.
        return stopped && activeResourceScopes.isEmpty() && (pending <= 0L)
    }

    /*
     * The refresh rate needs to be computed with each view because:
     * - it requires a context with a UI (we can't get this from the application context);
     * - it can change between different activities (based on window configuration)
     */
    @Suppress("DEPRECATION")
    private fun detectRefreshRateScale(key: Any) {
        val activity = when (key) {
            is Activity -> key
            is Fragment -> key.activity
            is android.app.Fragment -> key.activity
            else -> null
        } ?: return

        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display
        } else {
            (activity.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay
        } ?: return
        refreshRateScale = 60.0 / display.refreshRate
    }

    // endregion

    companion object {
        internal val ONE_SECOND_NS = TimeUnit.SECONDS.toNanos(1)

        internal const val ACTION_DROPPED_WARNING = "RUM Action (%s on %s) was dropped, because" +
            " another action is still active for the same view"
        internal const val RUM_BACKGROUND_VIEW_URL = "com/datadog/background/view"
        internal const val RUM_BACKGROUND_VIEW_NAME = "Background"

        internal val FROZEN_FRAME_THRESHOLD_NS = TimeUnit.MILLISECONDS.toNanos(700)
        internal const val SLOW_RENDERED_THRESHOLD_FPS = 55

        @Suppress("LongParameterList")
        internal fun fromEvent(
            parentScope: RumScope,
            event: RumRawEvent.StartView,
            firstPartyHostDetector: FirstPartyHostDetector,
            cpuVitalMonitor: VitalMonitor,
            memoryVitalMonitor: VitalMonitor,
            frameRateVitalMonitor: VitalMonitor,
            timeProvider: TimeProvider
        ): RumViewScope {
            return RumViewScope(
                parentScope,
                event.key,
                event.name,
                event.eventTime,
                event.attributes,
                firstPartyHostDetector,
                cpuVitalMonitor,
                memoryVitalMonitor,
                frameRateVitalMonitor,
                timeProvider
            )
        }
    }
}
