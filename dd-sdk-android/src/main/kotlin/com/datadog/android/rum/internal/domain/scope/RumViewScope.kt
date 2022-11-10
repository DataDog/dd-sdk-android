/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.fragment.app.Fragment
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.net.FirstPartyHostDetector
import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.internal.system.AndroidInfoProvider
import com.datadog.android.core.internal.system.BuildSdkVersionProvider
import com.datadog.android.core.internal.system.DefaultBuildSdkVersionProvider
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.core.internal.utils.hasUserData
import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.core.internal.utils.loggableStackTrace
import com.datadog.android.core.internal.utils.resolveViewUrl
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.log.internal.utils.debugWithTelemetry
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumPerformanceMetric
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.domain.event.RumEventSourceProvider
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
import kotlin.math.min

@Suppress("LargeClass", "LongParameterList")
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
    internal val timeProvider: TimeProvider,
    private val rumEventSourceProvider: RumEventSourceProvider,
    private val buildSdkVersionProvider: BuildSdkVersionProvider = DefaultBuildSdkVersionProvider(),
    private val viewUpdatePredicate: ViewUpdatePredicate = DefaultViewUpdatePredicate(),
    internal val type: RumViewType = RumViewType.FOREGROUND,
    private val androidInfoProvider: AndroidInfoProvider,
    private val trackFrustrations: Boolean
) : RumScope {

    internal val url = key.resolveViewUrl().replace('.', '/')

    internal val keyRef: Reference<Any> = WeakReference(key)
    internal val attributes: MutableMap<String, Any?> = initialAttributes.toMutableMap().apply {
        putAll(GlobalRum.globalAttributes)
    }

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
    private var frustrationCount: Int = 0
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
    private var frameRateVitalListener: VitalListener = object : VitalListener {
        override fun onVitalUpdate(info: VitalInfo) {
            lastFrameRateInfo = info
        }
    }

    private var performanceMetrics: MutableMap<RumPerformanceMetric, VitalInfo> = HashMap()

    // endregion

    init {
        GlobalRum.updateRumContext(getRumContext())
        attributes.putAll(GlobalRum.globalAttributes)
        cpuVitalMonitor.register(cpuVitalListener)
        memoryVitalMonitor.register(memoryVitalListener)
        frameRateVitalMonitor.register(frameRateVitalListener)

        detectRefreshRateScale(key)
    }

    // region RumScope

    override fun handleEvent(
        event: RumRawEvent,
        writer: DataWriter<Any>
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

            is RumRawEvent.UpdatePerformanceMetric -> onUpdatePerformanceMetric(event)

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
                actionId = (activeActionScope as? RumActionScope)?.actionId,
                viewType = type
            )
    }

    override fun isActive(): Boolean {
        return !stopped
    }

    // endregion

    // region Internal

    private fun onStartView(
        event: RumRawEvent.StartView,
        writer: DataWriter<Any>
    ) {
        if (!stopped) {
            // no need to update RUM Context here erasing current view, because this is called
            // only with event starting a new view, which itself will update a context
            // at the construction time
            stopped = true
            sendViewUpdate(event, writer)
            delegateEventToChildren(event, writer)
        }
    }

    private fun onStopView(
        event: RumRawEvent.StopView,
        writer: DataWriter<Any>
    ) {
        delegateEventToChildren(event, writer)
        val startedKey = keyRef.get()
        val shouldStop = (event.key == startedKey) || (startedKey == null)
        if (shouldStop && !stopped) {
            GlobalRum.updateRumContext(
                getRumContext().copy(
                    viewType = RumViewType.NONE,
                    viewId = null,
                    viewName = null,
                    viewUrl = null,
                    actionId = null
                ),
                applyOnlyIf = { currentContext ->
                    when {
                        currentContext.sessionId != this.sessionId -> {
                            // we have a new session, so whatever is in the Global context is
                            // not valid anyway
                            true
                        }
                        currentContext.viewId == this.viewId -> {
                            true
                        }
                        else -> {
                            sdkLogger.debugWithTelemetry(
                                RUM_CONTEXT_UPDATE_IGNORED_AT_STOP_VIEW_MESSAGE
                            )
                            false
                        }
                    }
                }
            )
            attributes.putAll(event.attributes)
            stopped = true
            sendViewUpdate(event, writer)
        }
    }

    private fun onStartAction(
        event: RumRawEvent.StartAction,
        writer: DataWriter<Any>
    ) {
        delegateEventToChildren(event, writer)

        if (stopped) return

        if (activeActionScope != null) {
            if (event.type == RumActionType.CUSTOM && !event.waitForStop) {
                // deliver it anyway, even if there is active action ongoing
                val customActionScope = RumActionScope.fromEvent(
                    this,
                    event,
                    serverTimeOffsetInMs,
                    rumEventSourceProvider,
                    androidInfoProvider,
                    trackFrustrations
                )
                pendingActionCount++
                customActionScope.handleEvent(RumRawEvent.SendCustomActionNow(), writer)
                return
            } else {
                devLogger.w(ACTION_DROPPED_WARNING.format(Locale.US, event.type, event.name))
                return
            }
        }

        updateActiveActionScope(
            RumActionScope.fromEvent(
                this,
                event,
                serverTimeOffsetInMs,
                rumEventSourceProvider,
                androidInfoProvider,
                trackFrustrations
            )
        )
        pendingActionCount++
    }

    private fun onStartResource(
        event: RumRawEvent.StartResource,
        writer: DataWriter<Any>
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
            serverTimeOffsetInMs,
            rumEventSourceProvider,
            androidInfoProvider
        )
        pendingResourceCount++
    }

    @Suppress("ComplexMethod", "LongMethod")
    private fun onAddError(
        event: RumRawEvent.AddError,
        writer: DataWriter<Any>
    ) {
        delegateEventToChildren(event, writer)
        if (stopped) return

        val context = getRumContext()
        val user = CoreFeature.userInfoProvider.getUserInfo()
        val updatedAttributes = addExtraAttributes(event.attributes)
        val isFatal = updatedAttributes.remove(RumAttributes.INTERNAL_ERROR_IS_CRASH) as? Boolean
        val networkInfo = CoreFeature.networkInfoProvider.getLatestNetworkInfo()
        val errorType = event.type ?: event.throwable?.javaClass?.canonicalName
        val throwableMessage = event.throwable?.message ?: ""
        val message = if (throwableMessage.isNotBlank() && event.message != throwableMessage) {
            "${event.message}: $throwableMessage"
        } else {
            event.message
        }
        val errorEvent = ErrorEvent(
            date = event.eventTime.timestamp + serverTimeOffsetInMs,
            error = ErrorEvent.Error(
                message = message,
                source = event.source.toSchemaSource(),
                stack = event.stacktrace ?: event.throwable?.loggableStackTrace(),
                isCrash = event.isFatal || (isFatal ?: false),
                type = errorType,
                sourceType = event.sourceType.toSchemaSourceType()
            ),
            action = context.actionId?.let { ErrorEvent.Action(listOf(it)) },
            view = ErrorEvent.View(
                id = context.viewId.orEmpty(),
                name = context.viewName,
                url = context.viewUrl.orEmpty()
            ),
            usr = if (!user.hasUserData()) {
                null
            } else {
                ErrorEvent.Usr(
                    id = user.id,
                    name = user.name,
                    email = user.email,
                    additionalProperties = user.additionalProperties
                )
            },
            connectivity = networkInfo.toErrorConnectivity(),
            application = ErrorEvent.Application(context.applicationId),
            session = ErrorEvent.ErrorEventSession(
                id = context.sessionId,
                type = ErrorEvent.ErrorEventSessionType.USER
            ),
            source = rumEventSourceProvider.errorEventSource,
            os = ErrorEvent.Os(
                name = androidInfoProvider.osName,
                version = androidInfoProvider.osVersion,
                versionMajor = androidInfoProvider.osMajorVersion
            ),
            device = ErrorEvent.Device(
                type = androidInfoProvider.deviceType.toErrorSchemaType(),
                name = androidInfoProvider.deviceName,
                model = androidInfoProvider.deviceModel,
                brand = androidInfoProvider.deviceBrand,
                architecture = androidInfoProvider.architecture
            ),
            context = ErrorEvent.Context(additionalProperties = updatedAttributes),
            dd = ErrorEvent.Dd(session = ErrorEvent.DdSession(plan = ErrorEvent.Plan.PLAN_1)),
            service = CoreFeature.serviceName,
            version = CoreFeature.packageVersionProvider.version
        )
        writer.write(errorEvent)

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
        writer: DataWriter<Any>
    ) {
        customTimings[event.name] = max(event.eventTime.nanoTime - startedNanos, 1L)
        sendViewUpdate(event, writer)
    }

    private fun onUpdatePerformanceMetric(
        event: RumRawEvent.UpdatePerformanceMetric
    ) {
        if (stopped) return

        val value = event.value
        val vitalInfo = performanceMetrics[event.metric] ?: VitalInfo.EMPTY
        val newSampleCount = vitalInfo.sampleCount + 1

        // Assuming M(n) is the mean value of the first n samples
        // M(n) = ∑ sample(n) / n
        // n⨉M(n) = ∑ sample(n)
        // M(n+1) = ∑ sample(n+1) / (n+1)
        //        = [ sample(n+1) + ∑ sample(n) ] / (n+1)
        //        = (sample(n+1) + n⨉M(n)) / (n+1)
        val meanValue = (value + (vitalInfo.sampleCount * vitalInfo.meanValue)) / newSampleCount
        performanceMetrics[event.metric] = VitalInfo(
            newSampleCount,
            min(value, vitalInfo.minValue),
            max(value, vitalInfo.maxValue),
            meanValue
        )
    }

    private fun onKeepAlive(
        event: RumRawEvent.KeepAlive,
        writer: DataWriter<Any>
    ) {
        delegateEventToChildren(event, writer)
        if (stopped) return

        sendViewUpdate(event, writer)
    }

    private fun delegateEventToChildren(
        event: RumRawEvent,
        writer: DataWriter<Any>
    ) {
        delegateEventToResources(event, writer)
        delegateEventToAction(event, writer)
    }

    private fun delegateEventToAction(
        event: RumRawEvent,
        writer: DataWriter<Any>
    ) {
        val currentAction = activeActionScope
        if (currentAction != null) {
            val updatedAction = currentAction.handleEvent(event, writer)
            if (updatedAction == null) {
                updateActiveActionScope(null)
            }
        }
    }

    private fun updateActiveActionScope(scope: RumScope?) {
        activeActionScope = scope
        // update the Rum Context to make it available for Logs/Trace bundling
        GlobalRum.updateRumContext(getRumContext(), applyOnlyIf = { currentContext ->
            when {
                currentContext.sessionId != this.sessionId -> {
                    true
                }
                currentContext.viewId == this.viewId -> {
                    true
                }
                else -> {
                    sdkLogger.debugWithTelemetry(
                        RUM_CONTEXT_UPDATE_IGNORED_AT_ACTION_UPDATE_MESSAGE
                    )
                    false
                }
            }
        })
    }

    private fun delegateEventToResources(
        event: RumRawEvent,
        writer: DataWriter<Any>
    ) {
        val iterator = activeResourceScopes.iterator()
        @Suppress("UnsafeThirdPartyFunctionCall") // next/remove can't fail: we checked hasNext
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
        writer: DataWriter<Any>
    ) {
        if (event.viewId == viewId) {
            pendingResourceCount--
            resourceCount++
            sendViewUpdate(event, writer)
        }
    }

    private fun onActionSent(
        event: RumRawEvent.ActionSent,
        writer: DataWriter<Any>
    ) {
        if (event.viewId == viewId) {
            pendingActionCount--
            actionCount++
            frustrationCount += event.frustrationCount
            sendViewUpdate(event, writer)
        }
    }

    private fun onErrorSent(
        event: RumRawEvent.ErrorSent,
        writer: DataWriter<Any>
    ) {
        if (event.viewId == viewId) {
            pendingErrorCount--
            errorCount++
            sendViewUpdate(event, writer)
        }
    }

    private fun onLongTaskSent(
        event: RumRawEvent.LongTaskSent,
        writer: DataWriter<Any>
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

    @Suppress("LongMethod", "ComplexMethod")
    private fun sendViewUpdate(event: RumRawEvent, writer: DataWriter<Any>) {
        val viewComplete = isViewComplete()
        if (!viewUpdatePredicate.canUpdateView(viewComplete, event)) {
            return
        }
        attributes.putAll(GlobalRum.globalAttributes)
        version++
        val updatedDurationNs = resolveViewDuration(event)
        val context = getRumContext()
        val user = CoreFeature.userInfoProvider.getUserInfo()
        val timings = resolveCustomTimings()
        val memoryInfo = lastMemoryInfo
        val refreshRateInfo = lastFrameRateInfo
        val isSlowRendered = resolveRefreshRateInfo(refreshRateInfo) ?: false
        val viewEvent = ViewEvent(
            date = eventTimestamp,
            view = ViewEvent.View(
                id = context.viewId.orEmpty(),
                name = context.viewName,
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
                isActive = !viewComplete,
                cpuTicksCount = cpuTicks,
                cpuTicksPerSecond = cpuTicks?.let { (it * ONE_SECOND_NS) / updatedDurationNs },
                memoryAverage = memoryInfo?.meanValue,
                memoryMax = memoryInfo?.maxValue,
                refreshRateAverage = refreshRateInfo?.meanValue?.let { it * refreshRateScale },
                refreshRateMin = refreshRateInfo?.minValue?.let { it * refreshRateScale },
                isSlowRendered = isSlowRendered,
                frustration = ViewEvent.Frustration(frustrationCount.toLong()),
                flutterBuildTime = performanceMetrics[RumPerformanceMetric.FLUTTER_BUILD_TIME]
                    ?.let { it.toPerformanceMetric() },
                flutterRasterTime = performanceMetrics[RumPerformanceMetric.FLUTTER_RASTER_TIME]
                    ?.let { it.toPerformanceMetric() },
                jsRefreshRate = performanceMetrics[RumPerformanceMetric.JS_FRAME_TIME]
                    ?.let { it.toInversePerformanceMetric() }
            ),
            usr = if (!user.hasUserData()) {
                null
            } else {
                ViewEvent.Usr(
                    id = user.id,
                    name = user.name,
                    email = user.email,
                    additionalProperties = user.additionalProperties
                )
            },
            application = ViewEvent.Application(context.applicationId),
            session = ViewEvent.ViewEventSession(
                id = context.sessionId,
                type = ViewEvent.ViewEventSessionType.USER
            ),
            source = rumEventSourceProvider.viewEventSource,
            os = ViewEvent.Os(
                name = androidInfoProvider.osName,
                version = androidInfoProvider.osVersion,
                versionMajor = androidInfoProvider.osMajorVersion
            ),
            device = ViewEvent.Device(
                type = androidInfoProvider.deviceType.toViewSchemaType(),
                name = androidInfoProvider.deviceName,
                model = androidInfoProvider.deviceModel,
                brand = androidInfoProvider.deviceBrand,
                architecture = androidInfoProvider.architecture
            ),
            context = ViewEvent.Context(additionalProperties = attributes),
            dd = ViewEvent.Dd(
                documentVersion = version,
                session = ViewEvent.DdSession(plan = ViewEvent.Plan.PLAN_1)
            ),
            service = CoreFeature.serviceName,
            version = CoreFeature.packageVersionProvider.version
        )

        writer.write(viewEvent)
    }

    private fun resolveViewDuration(event: RumRawEvent): Long {
        val duration = event.eventTime.nanoTime - startedNanos
        return if (duration <= 0) {
            devLogger.w(NEGATIVE_DURATION_WARNING_MESSAGE.format(Locale.US, name))
            1
        } else {
            duration
        }
    }

    private fun resolveRefreshRateInfo(refreshRateInfo: VitalInfo?) =
        if (refreshRateInfo == null) {
            null
        } else {
            refreshRateInfo.meanValue < SLOW_RENDERED_THRESHOLD_FPS
        }

    private fun resolveCustomTimings() = if (customTimings.isNotEmpty()) {
        ViewEvent.CustomTimings(LinkedHashMap(customTimings))
    } else {
        null
    }

    private fun addExtraAttributes(
        attributes: Map<String, Any?>
    ): MutableMap<String, Any?> {
        return attributes.toMutableMap()
            .apply { putAll(GlobalRum.globalAttributes) }
    }

    private fun onUpdateViewLoadingTime(
        event: RumRawEvent.UpdateViewLoadingTime,
        writer: DataWriter<Any>
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
        writer: DataWriter<Any>
    ) {
        pendingActionCount++
        val context = getRumContext()
        val user = CoreFeature.userInfoProvider.getUserInfo()

        val networkInfo = CoreFeature.networkInfoProvider.getLatestNetworkInfo()

        val actionEvent = ActionEvent(
            date = eventTimestamp,
            action = ActionEvent.ActionEventAction(
                type = ActionEvent.ActionEventActionType.APPLICATION_START,
                id = UUID.randomUUID().toString(),
                error = ActionEvent.Error(0),
                crash = ActionEvent.Crash(0),
                longTask = ActionEvent.LongTask(0),
                resource = ActionEvent.Resource(0),
                loadingTime = getStartupTime(event)
            ),
            view = ActionEvent.View(
                id = context.viewId.orEmpty(),
                name = context.viewName,
                url = context.viewUrl.orEmpty()
            ),
            usr = if (!user.hasUserData()) {
                null
            } else {
                ActionEvent.Usr(
                    id = user.id,
                    name = user.name,
                    email = user.email,
                    additionalProperties = user.additionalProperties
                )
            },
            application = ActionEvent.Application(context.applicationId),
            session = ActionEvent.ActionEventSession(
                id = context.sessionId,
                type = ActionEvent.ActionEventSessionType.USER
            ),
            source = rumEventSourceProvider.actionEventSource,
            os = ActionEvent.Os(
                name = androidInfoProvider.osName,
                version = androidInfoProvider.osVersion,
                versionMajor = androidInfoProvider.osMajorVersion
            ),
            device = ActionEvent.Device(
                type = androidInfoProvider.deviceType.toActionSchemaType(),
                name = androidInfoProvider.deviceName,
                model = androidInfoProvider.deviceModel,
                brand = androidInfoProvider.deviceBrand,
                architecture = androidInfoProvider.architecture
            ),
            context = ActionEvent.Context(additionalProperties = GlobalRum.globalAttributes),
            dd = ActionEvent.Dd(session = ActionEvent.DdSession(ActionEvent.Plan.PLAN_1)),
            connectivity = networkInfo.toActionConnectivity(),
            service = CoreFeature.serviceName,
            version = CoreFeature.packageVersionProvider.version
        )
        writer.write(actionEvent)
    }

    private fun getStartupTime(event: RumRawEvent.ApplicationStarted): Long {
        val now = event.eventTime.nanoTime
        val startupTime = event.applicationStartupNanos
        return max(now - startupTime, 1L)
    }

    @Suppress("LongMethod")
    private fun onAddLongTask(event: RumRawEvent.AddLongTask, writer: DataWriter<Any>) {
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
            action = context.actionId?.let { LongTaskEvent.Action(listOf(it)) },
            view = LongTaskEvent.View(
                id = context.viewId.orEmpty(),
                name = context.viewName,
                url = context.viewUrl.orEmpty()
            ),
            usr = if (!user.hasUserData()) {
                null
            } else {
                LongTaskEvent.Usr(
                    id = user.id,
                    name = user.name,
                    email = user.email,
                    additionalProperties = user.additionalProperties
                )
            },
            connectivity = networkInfo.toLongTaskConnectivity(),
            application = LongTaskEvent.Application(context.applicationId),
            session = LongTaskEvent.LongTaskEventSession(
                id = context.sessionId,
                type = LongTaskEvent.LongTaskEventSessionType.USER
            ),
            source = rumEventSourceProvider.longTaskEventSource,
            os = LongTaskEvent.Os(
                name = androidInfoProvider.osName,
                version = androidInfoProvider.osVersion,
                versionMajor = androidInfoProvider.osMajorVersion
            ),
            device = LongTaskEvent.Device(
                type = androidInfoProvider.deviceType.toLongTaskSchemaType(),
                name = androidInfoProvider.deviceName,
                model = androidInfoProvider.deviceModel,
                brand = androidInfoProvider.deviceBrand,
                architecture = androidInfoProvider.architecture
            ),
            context = LongTaskEvent.Context(additionalProperties = updatedAttributes),
            dd = LongTaskEvent.Dd(session = LongTaskEvent.DdSession(LongTaskEvent.Plan.PLAN_1)),
            service = CoreFeature.serviceName,
            version = CoreFeature.packageVersionProvider.version
        )
        writer.write(longTaskEvent)
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
    @SuppressLint("NewApi")
    @Suppress("DEPRECATION")
    private fun detectRefreshRateScale(key: Any) {
        val activity = when (key) {
            is Activity -> key
            is Fragment -> key.activity
            is android.app.Fragment -> key.activity
            else -> null
        } ?: return

        val display = if (buildSdkVersionProvider.version() >= Build.VERSION_CODES.R) {
            activity.display
        } else {
            (activity.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay
        } ?: return
        refreshRateScale = 60.0 / display.refreshRate
    }

    enum class RumViewType {
        NONE,
        FOREGROUND,
        BACKGROUND,
        APPLICATION_LAUNCH
    }

    // endregion

    companion object {
        internal val ONE_SECOND_NS = TimeUnit.SECONDS.toNanos(1)

        internal const val ACTION_DROPPED_WARNING = "RUM Action (%s on %s) was dropped, because" +
            " another action is still active for the same view"

        internal const val RUM_CONTEXT_UPDATE_IGNORED_AT_STOP_VIEW_MESSAGE =
            "Trying to update global RUM context when StopView event arrived, but the context" +
                " doesn't reference this view."
        internal const val RUM_CONTEXT_UPDATE_IGNORED_AT_ACTION_UPDATE_MESSAGE =
            "Trying to update active action in the global RUM context, but the context" +
                " doesn't reference this view."

        internal val FROZEN_FRAME_THRESHOLD_NS = TimeUnit.MILLISECONDS.toNanos(700)
        internal const val SLOW_RENDERED_THRESHOLD_FPS = 55
        internal const val NEGATIVE_DURATION_WARNING_MESSAGE = "The computed duration for your " +
            "view: %s was 0 or negative. In order to keep the view we forced it to 1ns."

        @Suppress("LongParameterList")
        internal fun fromEvent(
            parentScope: RumScope,
            event: RumRawEvent.StartView,
            firstPartyHostDetector: FirstPartyHostDetector,
            cpuVitalMonitor: VitalMonitor,
            memoryVitalMonitor: VitalMonitor,
            frameRateVitalMonitor: VitalMonitor,
            timeProvider: TimeProvider,
            rumEventSourceProvider: RumEventSourceProvider,
            androidInfoProvider: AndroidInfoProvider,
            trackFrustrations: Boolean
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
                timeProvider,
                rumEventSourceProvider,
                androidInfoProvider = androidInfoProvider,
                trackFrustrations = trackFrustrations
            )
        }
    }
}

private fun VitalInfo.toPerformanceMetric(): ViewEvent.FlutterBuildTime {
    return ViewEvent.FlutterBuildTime(
        min = minValue,
        max = maxValue,
        average = meanValue
    )
}

@Suppress("CommentOverPrivateFunction")
/**
 * This function is used to inverse frame times metrics into frame rates.
 *
 * As we take the inverse, the min of the inverse is the inverse of the max and
 * vice-versa.
 * For instance, if the the min frame time is 20ms (50 fps) and the max is 500ms (2 fps),
 * the max frame rate is 50 fps (1/minValue) and the min is 2 fps (1/maxValue).
 *
 * As the frame times are reported in nanoseconds, we need to add a multiplier.
 */
private fun VitalInfo.toInversePerformanceMetric(): ViewEvent.FlutterBuildTime {
    return ViewEvent.FlutterBuildTime(
        min = invertValue(maxValue) * TimeUnit.SECONDS.toNanos(1),
        max = invertValue(minValue) * TimeUnit.SECONDS.toNanos(1),
        average = invertValue(meanValue) * TimeUnit.SECONDS.toNanos(1)
    )
}

private fun invertValue(value: Double): Double {
    return if (value == 0.0) 0.0 else 1.0 / value
}
