/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.internal.net.FirstPartyHostHeaderTypeResolver
import com.datadog.android.core.internal.utils.loggableStackTrace
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumPerformanceMetric
import com.datadog.android.rum.internal.FeaturesContextResolver
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.vitals.VitalInfo
import com.datadog.android.rum.internal.vitals.VitalListener
import com.datadog.android.rum.internal.vitals.VitalMonitor
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.rum.utils.hasUserData
import com.datadog.android.rum.utils.resolveViewUrl
import java.lang.ref.Reference
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

@Suppress("TooManyFunctions", "LargeClass", "LongParameterList")
internal open class RumViewScope(
    private val parentScope: RumScope,
    private val sdkCore: InternalSdkCore,
    key: Any,
    internal val name: String,
    eventTime: Time,
    initialAttributes: Map<String, Any?>,
    private val viewChangedListener: RumViewChangedListener?,
    internal val firstPartyHostHeaderTypeResolver: FirstPartyHostHeaderTypeResolver,
    internal val cpuVitalMonitor: VitalMonitor,
    internal val memoryVitalMonitor: VitalMonitor,
    internal val frameRateVitalMonitor: VitalMonitor,
    private val featuresContextResolver: FeaturesContextResolver = FeaturesContextResolver(),
    internal val type: RumViewType = RumViewType.FOREGROUND,
    private val trackFrustrations: Boolean,
    internal val sampleRate: Float
) : RumScope {

    internal val url = key.resolveViewUrl().replace('.', '/')

    internal val keyRef: Reference<Any> = WeakReference(key)
    internal val attributes: MutableMap<String, Any?> = initialAttributes.toMutableMap()

    private var sessionId: String = parentScope.getRumContext().sessionId
    internal var viewId: String = UUID.randomUUID().toString()
        set(value) {
            oldViewIds += field
            field = value
        }
    private val oldViewIds = mutableSetOf<String>()
    private val startedNanos: Long = eventTime.nanoTime

    internal val serverTimeOffsetInMs = sdkCore.time.serverTimeOffsetMs
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

    // TODO RUMM-0000 We have now access to the event write result through the closure,
    // we probably can drop AdvancedRumMonitor#eventSent/eventDropped usage
    internal var pendingResourceCount: Long = 0
    internal var pendingActionCount: Long = 0
    internal var pendingErrorCount: Long = 0
    internal var pendingLongTaskCount: Long = 0
    internal var pendingFrozenFrameCount: Long = 0

    internal var version: Long = 1
    internal val customTimings: MutableMap<String, Long> = mutableMapOf()
    internal val featureFlags: MutableMap<String, Any?> = mutableMapOf()

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

    private var lastFrameRateInfo: VitalInfo? = null
    private var frameRateVitalListener: VitalListener = object : VitalListener {
        override fun onVitalUpdate(info: VitalInfo) {
            lastFrameRateInfo = info
        }
    }

    private var performanceMetrics: MutableMap<RumPerformanceMetric, VitalInfo> = mutableMapOf()

    // endregion

    init {
        sdkCore.updateFeatureContext(Feature.RUM_FEATURE_NAME) {
            it.putAll(getRumContext().toMap())
            it[RumFeature.VIEW_TIMESTAMP_OFFSET_IN_MS_KEY] = serverTimeOffsetInMs
        }
        attributes.putAll(GlobalRumMonitor.get(sdkCore).getAttributes())
        cpuVitalMonitor.register(cpuVitalListener)
        memoryVitalMonitor.register(memoryVitalListener)
        frameRateVitalMonitor.register(frameRateVitalListener)
    }

    // region RumScope

    @WorkerThread
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
            is RumRawEvent.AddFeatureFlagEvaluation -> onAddFeatureFlagEvaluation(event, writer)

            is RumRawEvent.ApplicationStarted -> onApplicationStarted(event, writer)
            is RumRawEvent.AddCustomTiming -> onAddCustomTiming(event, writer)
            is RumRawEvent.KeepAlive -> onKeepAlive(event, writer)

            is RumRawEvent.StopSession -> onStopSession(event, writer)

            is RumRawEvent.UpdatePerformanceMetric -> onUpdatePerformanceMetric(event)

            else -> delegateEventToChildren(event, writer)
        }

        return if (isViewComplete()) {
            sdkCore.updateFeatureContext(Feature.SESSION_REPLAY_FEATURE_NAME) {
                it.remove(viewId)
            }
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

    @WorkerThread
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
            sendViewChanged()
        }
    }

    @WorkerThread
    private fun onStopView(
        event: RumRawEvent.StopView,
        writer: DataWriter<Any>
    ) {
        delegateEventToChildren(event, writer)
        val startedKey = keyRef.get()
        val shouldStop = (event.key == startedKey) || (startedKey == null)
        if (shouldStop && !stopped) {
            val newRumContext = getRumContext().copy(
                viewType = RumViewType.NONE,
                viewId = null,
                viewName = null,
                viewUrl = null,
                actionId = null
            )
            sdkCore.updateFeatureContext(Feature.RUM_FEATURE_NAME) { currentRumContext ->
                val canUpdate = when {
                    currentRumContext["session_id"] != this.sessionId -> {
                        // we have a new session, so whatever is in the Global context is
                        // not valid anyway
                        true
                    }

                    currentRumContext["view_id"] == this.viewId -> true
                    else -> false
                }
                if (canUpdate) {
                    currentRumContext.clear()
                    currentRumContext.putAll(newRumContext.toMap())
                } else {
                    sdkCore.internalLogger.log(
                        InternalLogger.Level.DEBUG,
                        InternalLogger.Target.MAINTAINER,
                        { RUM_CONTEXT_UPDATE_IGNORED_AT_STOP_VIEW_MESSAGE }
                    )
                }
            }
            attributes.putAll(event.attributes)
            stopped = true
            sendViewUpdate(event, writer)
            sendViewChanged()
        }
    }

    @Suppress("ReturnCount")
    @WorkerThread
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
                    sdkCore,
                    event,
                    serverTimeOffsetInMs,
                    featuresContextResolver,
                    trackFrustrations,
                    sampleRate
                )
                pendingActionCount++
                customActionScope.handleEvent(RumRawEvent.SendCustomActionNow(), writer)
                return
            } else {
                sdkCore.internalLogger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.USER,
                    { ACTION_DROPPED_WARNING.format(Locale.US, event.type, event.name) }
                )
                return
            }
        }

        updateActiveActionScope(
            RumActionScope.fromEvent(
                this,
                sdkCore,
                event,
                serverTimeOffsetInMs,
                featuresContextResolver,
                trackFrustrations,
                sampleRate
            )
        )
        pendingActionCount++
    }

    @WorkerThread
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
            sdkCore,
            updatedEvent,
            firstPartyHostHeaderTypeResolver,
            serverTimeOffsetInMs,
            featuresContextResolver,
            sampleRate
        )
        pendingResourceCount++
    }

    @Suppress("ComplexMethod", "LongMethod")
    @WorkerThread
    private fun onAddError(
        event: RumRawEvent.AddError,
        writer: DataWriter<Any>
    ) {
        delegateEventToChildren(event, writer)
        if (stopped) return

        val rumContext = getRumContext()

        val updatedAttributes = addExtraAttributes(event.attributes)
        val isFatal = updatedAttributes
            .remove(RumAttributes.INTERNAL_ERROR_IS_CRASH) as? Boolean == true || event.isFatal
        // if a cross-platform crash was already reported, do not send its native version
        if (crashCount > 0 && isFatal) return

        val errorType = event.type ?: event.throwable?.javaClass?.canonicalName
        val throwableMessage = event.throwable?.message ?: ""
        val message = if (throwableMessage.isNotBlank() && event.message != throwableMessage) {
            "${event.message}: $throwableMessage"
        } else {
            event.message
        }

        sdkCore.getFeature(Feature.RUM_FEATURE_NAME)
            ?.withWriteContext { datadogContext, eventBatchWriter ->

                val user = datadogContext.userInfo
                val hasReplay = featuresContextResolver.resolveViewHasReplay(
                    datadogContext,
                    rumContext.viewId.orEmpty()
                )
                val errorEvent = ErrorEvent(
                    date = event.eventTime.timestamp + serverTimeOffsetInMs,
                    featureFlags = ErrorEvent.Context(featureFlags),
                    error = ErrorEvent.Error(
                        message = message,
                        source = event.source.toSchemaSource(),
                        stack = event.stacktrace ?: event.throwable?.loggableStackTrace(),
                        isCrash = isFatal,
                        type = errorType,
                        sourceType = event.sourceType.toSchemaSourceType()
                    ),
                    action = rumContext.actionId?.let { ErrorEvent.Action(listOf(it)) },
                    view = ErrorEvent.ErrorEventView(
                        id = rumContext.viewId.orEmpty(),
                        name = rumContext.viewName,
                        url = rumContext.viewUrl.orEmpty()
                    ),
                    usr = if (user.hasUserData()) {
                        ErrorEvent.Usr(
                            id = user.id,
                            name = user.name,
                            email = user.email,
                            additionalProperties = user.additionalProperties.toMutableMap()
                        )
                    } else {
                        null
                    },
                    connectivity = datadogContext.networkInfo.toErrorConnectivity(),
                    application = ErrorEvent.Application(rumContext.applicationId),
                    session = ErrorEvent.ErrorEventSession(
                        id = rumContext.sessionId,
                        type = ErrorEvent.ErrorEventSessionType.USER,
                        hasReplay = hasReplay
                    ),
                    source = ErrorEvent.ErrorEventSource.tryFromSource(
                        datadogContext.source,
                        sdkCore.internalLogger
                    ),
                    os = ErrorEvent.Os(
                        name = datadogContext.deviceInfo.osName,
                        version = datadogContext.deviceInfo.osVersion,
                        versionMajor = datadogContext.deviceInfo.osMajorVersion
                    ),
                    device = ErrorEvent.Device(
                        type = datadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                        name = datadogContext.deviceInfo.deviceName,
                        model = datadogContext.deviceInfo.deviceModel,
                        brand = datadogContext.deviceInfo.deviceBrand,
                        architecture = datadogContext.deviceInfo.architecture
                    ),
                    context = ErrorEvent.Context(additionalProperties = updatedAttributes),
                    dd = ErrorEvent.Dd(
                        session = ErrorEvent.DdSession(plan = ErrorEvent.Plan.PLAN_1),
                        configuration = ErrorEvent.Configuration(sessionSampleRate = sampleRate)
                    ),
                    service = datadogContext.service,
                    version = datadogContext.version
                )

                @Suppress("ThreadSafety") // called in a worker thread context
                writer.write(eventBatchWriter, errorEvent)
            }

        if (isFatal) {
            errorCount++
            crashCount++
            sendViewUpdate(event, writer)
        } else {
            pendingErrorCount++
        }
    }

    @WorkerThread
    private fun onAddCustomTiming(
        event: RumRawEvent.AddCustomTiming,
        writer: DataWriter<Any>
    ) {
        if (stopped) return

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

    @WorkerThread
    private fun onStopSession(event: RumRawEvent.StopSession, writer: DataWriter<Any>) {
        stopped = true

        sendViewUpdate(event, writer)
    }

    @WorkerThread
    private fun onKeepAlive(
        event: RumRawEvent.KeepAlive,
        writer: DataWriter<Any>
    ) {
        delegateEventToChildren(event, writer)
        if (stopped) return

        sendViewUpdate(event, writer)
    }

    @WorkerThread
    private fun delegateEventToChildren(
        event: RumRawEvent,
        writer: DataWriter<Any>
    ) {
        delegateEventToResources(event, writer)
        delegateEventToAction(event, writer)
    }

    @WorkerThread
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
        val newRumContext = getRumContext()

        sdkCore.updateFeatureContext(Feature.RUM_FEATURE_NAME) { currentRumContext ->
            val canUpdate = when {
                currentRumContext["session_id"] != sessionId -> true
                currentRumContext["view_id"] == viewId -> true
                else -> false
            }
            if (canUpdate) {
                currentRumContext.clear()
                currentRumContext.putAll(newRumContext.toMap())
            } else {
                sdkCore.internalLogger.log(
                    InternalLogger.Level.DEBUG,
                    InternalLogger.Target.MAINTAINER,
                    { RUM_CONTEXT_UPDATE_IGNORED_AT_ACTION_UPDATE_MESSAGE }
                )
            }
        }
    }

    @WorkerThread
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

    @WorkerThread
    private fun onResourceSent(
        event: RumRawEvent.ResourceSent,
        writer: DataWriter<Any>
    ) {
        if (event.viewId == viewId || event.viewId in oldViewIds) {
            pendingResourceCount--
            resourceCount++
            sendViewUpdate(event, writer)
        }
    }

    @WorkerThread
    private fun onActionSent(
        event: RumRawEvent.ActionSent,
        writer: DataWriter<Any>
    ) {
        if (event.viewId == viewId || event.viewId in oldViewIds) {
            pendingActionCount--
            actionCount++
            frustrationCount += event.frustrationCount
            sendViewUpdate(event, writer)
        }
    }

    @WorkerThread
    private fun onLongTaskSent(
        event: RumRawEvent.LongTaskSent,
        writer: DataWriter<Any>
    ) {
        if (event.viewId == viewId || event.viewId in oldViewIds) {
            pendingLongTaskCount--
            longTaskCount++
            if (event.isFrozenFrame) {
                pendingFrozenFrameCount--
                frozenFrameCount++
            }
            sendViewUpdate(event, writer)
        }
    }

    @WorkerThread
    private fun onErrorSent(
        event: RumRawEvent.ErrorSent,
        writer: DataWriter<Any>
    ) {
        if (event.viewId == viewId || event.viewId in oldViewIds) {
            pendingErrorCount--
            errorCount++
            sendViewUpdate(event, writer)
        }
    }

    private fun onResourceDropped(event: RumRawEvent.ResourceDropped) {
        if (event.viewId == viewId || event.viewId in oldViewIds) {
            pendingResourceCount--
        }
    }

    private fun onActionDropped(event: RumRawEvent.ActionDropped) {
        if (event.viewId == viewId || event.viewId in oldViewIds) {
            pendingActionCount--
        }
    }

    private fun onErrorDropped(event: RumRawEvent.ErrorDropped) {
        if (event.viewId == viewId || event.viewId in oldViewIds) {
            pendingErrorCount--
        }
    }

    private fun onLongTaskDropped(event: RumRawEvent.LongTaskDropped) {
        if (event.viewId == viewId || event.viewId in oldViewIds) {
            pendingLongTaskCount--
            if (event.isFrozenFrame) {
                pendingFrozenFrameCount--
            }
        }
    }

    @Suppress("LongMethod", "ComplexMethod")
    private fun sendViewUpdate(event: RumRawEvent, writer: DataWriter<Any>) {
        val viewComplete = isViewComplete()
        attributes.putAll(GlobalRumMonitor.get(sdkCore).getAttributes())
        version++

        // make a local copy, so that closure captures the state as of now
        val eventVersion = version

        val eventActionCount = actionCount
        val eventErrorCount = errorCount
        val eventResourceCount = resourceCount
        val eventCrashCount = crashCount
        val eventLongTaskCount = longTaskCount
        val eventFrozenFramesCount = frozenFrameCount

        val eventCpuTicks = cpuTicks

        val eventFrustrationCount = frustrationCount

        val eventFlutterBuildTime = performanceMetrics[RumPerformanceMetric.FLUTTER_BUILD_TIME]
            ?.toPerformanceMetric()
        val eventFlutterRasterTime = performanceMetrics[RumPerformanceMetric.FLUTTER_RASTER_TIME]
            ?.toPerformanceMetric()
        val eventJsRefreshRate = performanceMetrics[RumPerformanceMetric.JS_FRAME_TIME]
            ?.toInversePerformanceMetric()

        val updatedDurationNs = resolveViewDuration(event)
        val rumContext = getRumContext()

        val timings = resolveCustomTimings()
        val memoryInfo = lastMemoryInfo
        val refreshRateInfo = lastFrameRateInfo
        val isSlowRendered = resolveRefreshRateInfo(refreshRateInfo) ?: false

        sdkCore.getFeature(Feature.RUM_FEATURE_NAME)
            ?.withWriteContext { datadogContext, eventBatchWriter ->
                val currentViewId = rumContext.viewId.orEmpty()
                val user = datadogContext.userInfo
                val hasReplay = featuresContextResolver.resolveViewHasReplay(
                    datadogContext,
                    currentViewId
                )
                val sessionReplayRecordsCount = featuresContextResolver.resolveViewRecordsCount(
                    datadogContext,
                    currentViewId
                )
                val replayStats = ViewEvent.ReplayStats(recordsCount = sessionReplayRecordsCount)
                val viewEvent = ViewEvent(
                    date = eventTimestamp,
                    featureFlags = ViewEvent.Context(additionalProperties = featureFlags),
                    view = ViewEvent.ViewEventView(
                        id = currentViewId,
                        name = rumContext.viewName,
                        url = rumContext.viewUrl.orEmpty(),
                        timeSpent = updatedDurationNs,
                        action = ViewEvent.Action(eventActionCount),
                        resource = ViewEvent.Resource(eventResourceCount),
                        error = ViewEvent.Error(eventErrorCount),
                        crash = ViewEvent.Crash(eventCrashCount),
                        longTask = ViewEvent.LongTask(eventLongTaskCount),
                        frozenFrame = ViewEvent.FrozenFrame(eventFrozenFramesCount),
                        customTimings = timings,
                        isActive = !viewComplete,
                        cpuTicksCount = eventCpuTicks,
                        cpuTicksPerSecond = if (updatedDurationNs >= ONE_SECOND_NS) {
                            eventCpuTicks?.let { (it * ONE_SECOND_NS) / updatedDurationNs }
                        } else {
                            null
                        },
                        memoryAverage = memoryInfo?.meanValue,
                        memoryMax = memoryInfo?.maxValue,
                        refreshRateAverage = refreshRateInfo?.meanValue,
                        refreshRateMin = refreshRateInfo?.minValue,
                        isSlowRendered = isSlowRendered,
                        frustration = ViewEvent.Frustration(eventFrustrationCount.toLong()),
                        flutterBuildTime = eventFlutterBuildTime,
                        flutterRasterTime = eventFlutterRasterTime,
                        jsRefreshRate = eventJsRefreshRate
                    ),
                    usr = if (user.hasUserData()) {
                        ViewEvent.Usr(
                            id = user.id,
                            name = user.name,
                            email = user.email,
                            additionalProperties = user.additionalProperties.toMutableMap()
                        )
                    } else {
                        null
                    },
                    application = ViewEvent.Application(rumContext.applicationId),
                    session = ViewEvent.ViewEventSession(
                        id = rumContext.sessionId,
                        type = ViewEvent.ViewEventSessionType.USER,
                        hasReplay = hasReplay,
                        isActive = rumContext.isSessionActive
                    ),
                    source = ViewEvent.ViewEventSource.tryFromSource(
                        datadogContext.source,
                        sdkCore.internalLogger
                    ),
                    os = ViewEvent.Os(
                        name = datadogContext.deviceInfo.osName,
                        version = datadogContext.deviceInfo.osVersion,
                        versionMajor = datadogContext.deviceInfo.osMajorVersion
                    ),
                    device = ViewEvent.Device(
                        type = datadogContext.deviceInfo.deviceType.toViewSchemaType(),
                        name = datadogContext.deviceInfo.deviceName,
                        model = datadogContext.deviceInfo.deviceModel,
                        brand = datadogContext.deviceInfo.deviceBrand,
                        architecture = datadogContext.deviceInfo.architecture
                    ),
                    context = ViewEvent.Context(additionalProperties = attributes),
                    dd = ViewEvent.Dd(
                        documentVersion = eventVersion,
                        session = ViewEvent.DdSession(plan = ViewEvent.Plan.PLAN_1),
                        replayStats = replayStats,
                        configuration = ViewEvent.Configuration(sessionSampleRate = sampleRate)
                    ),
                    connectivity = datadogContext.networkInfo.toViewConnectivity(),
                    service = datadogContext.service,
                    version = datadogContext.version
                )

                @Suppress("ThreadSafety") // called in a worker thread context
                writer.write(eventBatchWriter, viewEvent)
            }
    }

    private fun resolveViewDuration(event: RumRawEvent): Long {
        val duration = event.eventTime.nanoTime - startedNanos
        return if (duration <= 0) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.WARN,
                listOf(
                    InternalLogger.Target.USER,
                    InternalLogger.Target.TELEMETRY
                ),
                { NEGATIVE_DURATION_WARNING_MESSAGE.format(Locale.US, name) }
            )
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
            .apply { putAll(GlobalRumMonitor.get(sdkCore).getAttributes()) }
    }

    @Suppress("LongMethod")
    @WorkerThread
    private fun onApplicationStarted(
        event: RumRawEvent.ApplicationStarted,
        writer: DataWriter<Any>
    ) {
        pendingActionCount++
        val rumContext = getRumContext()

        val globalAttributes = GlobalRumMonitor.get(sdkCore).getAttributes().toMutableMap()

        sdkCore.getFeature(Feature.RUM_FEATURE_NAME)
            ?.withWriteContext { datadogContext, eventBatchWriter ->
                val user = datadogContext.userInfo

                val actionEvent = ActionEvent(
                    date = eventTimestamp,
                    action = ActionEvent.ActionEventAction(
                        type = ActionEvent.ActionEventActionType.APPLICATION_START,
                        id = UUID.randomUUID().toString(),
                        error = ActionEvent.Error(0),
                        crash = ActionEvent.Crash(0),
                        longTask = ActionEvent.LongTask(0),
                        resource = ActionEvent.Resource(0),
                        loadingTime = event.applicationStartupNanos
                    ),
                    view = ActionEvent.ActionEventView(
                        id = rumContext.viewId.orEmpty(),
                        name = rumContext.viewName,
                        url = rumContext.viewUrl.orEmpty()
                    ),
                    usr = if (user.hasUserData()) {
                        ActionEvent.Usr(
                            id = user.id,
                            name = user.name,
                            email = user.email,
                            additionalProperties = user.additionalProperties.toMutableMap()
                        )
                    } else {
                        null
                    },
                    application = ActionEvent.Application(rumContext.applicationId),
                    session = ActionEvent.ActionEventSession(
                        id = rumContext.sessionId,
                        type = ActionEvent.ActionEventSessionType.USER,
                        hasReplay = false
                    ),
                    source = ActionEvent.ActionEventSource.tryFromSource(
                        datadogContext.source,
                        sdkCore.internalLogger
                    ),
                    os = ActionEvent.Os(
                        name = datadogContext.deviceInfo.osName,
                        version = datadogContext.deviceInfo.osVersion,
                        versionMajor = datadogContext.deviceInfo.osMajorVersion
                    ),
                    device = ActionEvent.Device(
                        type = datadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        name = datadogContext.deviceInfo.deviceName,
                        model = datadogContext.deviceInfo.deviceModel,
                        brand = datadogContext.deviceInfo.deviceBrand,
                        architecture = datadogContext.deviceInfo.architecture
                    ),
                    context = ActionEvent.Context(
                        additionalProperties = globalAttributes
                    ),
                    dd = ActionEvent.Dd(
                        session = ActionEvent.DdSession(ActionEvent.Plan.PLAN_1),
                        configuration = ActionEvent.Configuration(sessionSampleRate = sampleRate)
                    ),
                    connectivity = datadogContext.networkInfo.toActionConnectivity(),
                    service = datadogContext.service,
                    version = datadogContext.version
                )

                @Suppress("ThreadSafety") // called in a worker thread context
                writer.write(eventBatchWriter, actionEvent)
            }
    }

    @Suppress("LongMethod")
    @WorkerThread
    private fun onAddLongTask(event: RumRawEvent.AddLongTask, writer: DataWriter<Any>) {
        delegateEventToChildren(event, writer)
        if (stopped) return

        val rumContext = getRumContext()
        val updatedAttributes = addExtraAttributes(
            mapOf(RumAttributes.LONG_TASK_TARGET to event.target)
        )
        val timestamp = event.eventTime.timestamp + serverTimeOffsetInMs
        val isFrozenFrame = event.durationNs > FROZEN_FRAME_THRESHOLD_NS
        sdkCore.getFeature(Feature.RUM_FEATURE_NAME)
            ?.withWriteContext { datadogContext, eventBatchWriter ->

                val user = datadogContext.userInfo
                val hasReplay = featuresContextResolver.resolveViewHasReplay(
                    datadogContext,
                    rumContext.viewId.orEmpty()
                )
                val longTaskEvent = LongTaskEvent(
                    date = timestamp - TimeUnit.NANOSECONDS.toMillis(event.durationNs),
                    longTask = LongTaskEvent.LongTask(
                        duration = event.durationNs,
                        isFrozenFrame = isFrozenFrame
                    ),
                    action = rumContext.actionId?.let { LongTaskEvent.Action(listOf(it)) },
                    view = LongTaskEvent.LongTaskEventView(
                        id = rumContext.viewId.orEmpty(),
                        name = rumContext.viewName,
                        url = rumContext.viewUrl.orEmpty()
                    ),
                    usr = if (user.hasUserData()) {
                        LongTaskEvent.Usr(
                            id = user.id,
                            name = user.name,
                            email = user.email,
                            additionalProperties = user.additionalProperties.toMutableMap()
                        )
                    } else {
                        null
                    },
                    connectivity = datadogContext.networkInfo.toLongTaskConnectivity(),
                    application = LongTaskEvent.Application(rumContext.applicationId),
                    session = LongTaskEvent.LongTaskEventSession(
                        id = rumContext.sessionId,
                        type = LongTaskEvent.LongTaskEventSessionType.USER,
                        hasReplay = hasReplay
                    ),
                    source = LongTaskEvent.LongTaskEventSource.tryFromSource(
                        datadogContext.source,
                        sdkCore.internalLogger
                    ),
                    os = LongTaskEvent.Os(
                        name = datadogContext.deviceInfo.osName,
                        version = datadogContext.deviceInfo.osVersion,
                        versionMajor = datadogContext.deviceInfo.osMajorVersion
                    ),
                    device = LongTaskEvent.Device(
                        type = datadogContext.deviceInfo.deviceType.toLongTaskSchemaType(),
                        name = datadogContext.deviceInfo.deviceName,
                        model = datadogContext.deviceInfo.deviceModel,
                        brand = datadogContext.deviceInfo.deviceBrand,
                        architecture = datadogContext.deviceInfo.architecture
                    ),
                    context = LongTaskEvent.Context(additionalProperties = updatedAttributes),
                    dd = LongTaskEvent.Dd(
                        session = LongTaskEvent.DdSession(LongTaskEvent.Plan.PLAN_1),
                        configuration = LongTaskEvent.Configuration(sessionSampleRate = sampleRate)
                    ),
                    service = datadogContext.service,
                    version = datadogContext.version
                )

                @Suppress("ThreadSafety") // called in a worker thread context
                writer.write(eventBatchWriter, longTaskEvent)
            }

        pendingLongTaskCount++
        if (isFrozenFrame) pendingFrozenFrameCount++
    }

    private fun onAddFeatureFlagEvaluation(
        event: RumRawEvent.AddFeatureFlagEvaluation,
        writer: DataWriter<Any>
    ) {
        if (stopped) return

        featureFlags[event.name] = event.value
        sendViewUpdate(event, writer)
        sendViewChanged()
    }

    private fun sendViewChanged() {
        viewChangedListener?.onViewChanged(
            RumViewInfo(
                keyRef = keyRef,
                name = name,
                attributes = attributes,
                isActive = isActive()
            )
        )
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

    enum class RumViewType(val asString: String) {
        NONE("NONE"),
        FOREGROUND("FOREGROUND"),
        BACKGROUND("BACKGROUND"),
        APPLICATION_LAUNCH("APPLICATION_LAUNCH");

        companion object {
            fun fromString(string: String?): RumViewType? {
                return values().firstOrNull { it.asString == string }
            }
        }
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
        internal const val NEGATIVE_DURATION_WARNING_MESSAGE = "The computed duration for the " +
            "view: %s was 0 or negative. In order to keep the view we forced it to 1ns."

        internal fun fromEvent(
            parentScope: RumScope,
            sdkCore: InternalSdkCore,
            event: RumRawEvent.StartView,
            viewChangedListener: RumViewChangedListener?,
            firstPartyHostHeaderTypeResolver: FirstPartyHostHeaderTypeResolver,
            cpuVitalMonitor: VitalMonitor,
            memoryVitalMonitor: VitalMonitor,
            frameRateVitalMonitor: VitalMonitor,
            trackFrustrations: Boolean,
            sampleRate: Float
        ): RumViewScope {
            return RumViewScope(
                parentScope,
                sdkCore,
                event.key,
                event.name,
                event.eventTime,
                event.attributes,
                viewChangedListener,
                firstPartyHostHeaderTypeResolver,
                cpuVitalMonitor,
                memoryVitalMonitor,
                frameRateVitalMonitor,
                trackFrustrations = trackFrustrations,
                sampleRate = sampleRate
            )
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
    }
}
