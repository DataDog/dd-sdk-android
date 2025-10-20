/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.monitor

import android.app.Activity
import android.app.ActivityManager
import android.os.Handler
import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.EventWriteScope
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.measureMethodCallPerf
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.feature.event.ThreadDump
import com.datadog.android.core.internal.net.FirstPartyHostHeaderTypeResolver
import com.datadog.android.core.internal.utils.executeSafe
import com.datadog.android.core.internal.utils.getSafe
import com.datadog.android.core.internal.utils.submitSafe
import com.datadog.android.core.metrics.MethodCallSamplingRate
import com.datadog.android.internal.telemetry.InternalTelemetryEvent
import com.datadog.android.internal.telemetry.InternalTelemetryEvent.ApiUsage.AddOperationStepVital.ActionType
import com.datadog.android.internal.thread.NamedCallable
import com.datadog.android.rum.DdRumContentProvider
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.RumPerformanceMetric
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.RumResourceMethod
import com.datadog.android.rum.RumSessionListener
import com.datadog.android.rum.RumSessionType
import com.datadog.android.rum._RumInternalProxy
import com.datadog.android.rum.featureoperations.FailureReason
import com.datadog.android.rum.internal.CombinedRumSessionListener
import com.datadog.android.rum.internal.RumErrorSourceType
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.debug.RumDebugListener
import com.datadog.android.rum.internal.domain.InfoProvider
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.domain.accessibility.AccessibilitySnapshotManager
import com.datadog.android.rum.internal.domain.asTime
import com.datadog.android.rum.internal.domain.battery.BatteryInfo
import com.datadog.android.rum.internal.domain.display.DisplayInfo
import com.datadog.android.rum.internal.domain.event.ResourceTiming
import com.datadog.android.rum.internal.domain.scope.RumApplicationScope
import com.datadog.android.rum.internal.domain.scope.RumRawEvent
import com.datadog.android.rum.internal.domain.scope.RumScopeKey
import com.datadog.android.rum.internal.domain.scope.RumSessionScope
import com.datadog.android.rum.internal.domain.scope.RumVitalEventHelper
import com.datadog.android.rum.internal.metric.SessionMetricDispatcher
import com.datadog.android.rum.internal.metric.slowframes.SlowFramesListener
import com.datadog.android.rum.internal.startup.RumSessionScopeStartupManager
import com.datadog.android.rum.internal.startup.RumStartupScenario
import com.datadog.android.rum.internal.startup.RumTTIDInfo
import com.datadog.android.rum.internal.vitals.VitalMonitor
import com.datadog.android.rum.metric.interactiontonextview.LastInteractionIdentifier
import com.datadog.android.rum.metric.networksettled.InitialResourceIdentifier
import com.datadog.android.rum.resource.ResourceId
import com.datadog.android.telemetry.internal.TelemetryEventHandler
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("LongParameterList", "LargeClass", "TooManyFunctions")
internal class DatadogRumMonitor(
    applicationId: String,
    private val sdkCore: InternalSdkCore,
    internal val sampleRate: Float,
    internal val backgroundTrackingEnabled: Boolean,
    internal val trackFrustrations: Boolean,
    private val writer: DataWriter<Any>,
    internal val handler: Handler,
    internal val telemetryEventHandler: TelemetryEventHandler,
    private val sessionEndedMetricDispatcher: SessionMetricDispatcher,
    firstPartyHostHeaderTypeResolver: FirstPartyHostHeaderTypeResolver,
    cpuVitalMonitor: VitalMonitor,
    memoryVitalMonitor: VitalMonitor,
    frameRateVitalMonitor: VitalMonitor,
    sessionListener: RumSessionListener,
    internal val executorService: ExecutorService,
    initialResourceIdentifier: InitialResourceIdentifier,
    lastInteractionIdentifier: LastInteractionIdentifier?,
    slowFramesListener: SlowFramesListener?,
    rumSessionTypeOverride: RumSessionType?,
    accessibilitySnapshotManager: AccessibilitySnapshotManager,
    batteryInfoProvider: InfoProvider<BatteryInfo>,
    displayInfoProvider: InfoProvider<DisplayInfo>,
    rumVitalEventHelper: RumVitalEventHelper,
    private val rumSessionScopeStartupManagerFactory: () -> RumSessionScopeStartupManager
) : RumMonitor, AdvancedRumMonitor {

    internal var rootScope = RumApplicationScope(
        applicationId = applicationId,
        sdkCore = sdkCore,
        sampleRate = sampleRate,
        backgroundTrackingEnabled = backgroundTrackingEnabled,
        trackFrustrations = trackFrustrations,
        firstPartyHostHeaderTypeResolver = firstPartyHostHeaderTypeResolver,
        cpuVitalMonitor = cpuVitalMonitor,
        memoryVitalMonitor = memoryVitalMonitor,
        frameRateVitalMonitor = frameRateVitalMonitor,
        sessionEndedMetricDispatcher = sessionEndedMetricDispatcher,
        sessionListener = CombinedRumSessionListener(sessionListener, telemetryEventHandler),
        initialResourceIdentifier = initialResourceIdentifier,
        lastInteractionIdentifier = lastInteractionIdentifier,
        slowFramesListener = slowFramesListener,
        rumSessionTypeOverride = rumSessionTypeOverride,
        accessibilitySnapshotManager = accessibilitySnapshotManager,
        batteryInfoProvider = batteryInfoProvider,
        displayInfoProvider = displayInfoProvider,
        rumVitalEventHelper = rumVitalEventHelper,
        rumSessionScopeStartupManagerFactory = rumSessionScopeStartupManagerFactory
    )

    internal val keepAliveRunnable = Runnable {
        handleEvent(RumRawEvent.KeepAlive())
    }

    internal var debugListener: RumDebugListener? = null

    private val internalProxy = _RumInternalProxy(this)

    init {
        handler.postDelayed(keepAliveRunnable, KEEP_ALIVE_MS)
    }

    private val globalAttributes: MutableMap<String, Any?> = ConcurrentHashMap()

    private val isDebugEnabled = AtomicBoolean(false)

    // region RumMonitor

    override fun getCurrentSessionId(callback: (String?) -> Unit) {
        executorService.executeSafe(
            "Get current session ID",
            sdkCore.internalLogger
        ) {
            val activeSessionId = rootScope.activeSession
                ?.getRumContext()
                ?.let {
                    val sessionId = it.sessionId
                    if (it.sessionState == RumSessionScope.State.NOT_TRACKED ||
                        sessionId == RumContext.NULL_UUID
                    ) {
                        null
                    } else {
                        sessionId
                    }
                }
            callback(activeSessionId)
        }
    }

    override var debug: Boolean
        get() = isDebugEnabled.get()
        set(value) {
            val isEnabled = isDebugEnabled.get()
            if (value == isEnabled) return

            val rumFeatureScope = sdkCore.getFeature(Feature.RUM_FEATURE_NAME)
                ?.unwrap<RumFeature>()
            if (rumFeatureScope == null) {
                sdkCore.internalLogger.logToUser(InternalLogger.Level.WARN) { RUM_DEBUG_RUM_NOT_ENABLED_WARNING }
                return
            }

            if (value) {
                rumFeatureScope.enableDebugging(this)
            } else {
                rumFeatureScope.disableDebugging()
            }
            isDebugEnabled.set(value)
        }

    override fun startView(key: Any, name: String, attributes: Map<String, Any?>) {
        val eventTime = getEventTime(attributes)
        handleEvent(
            RumRawEvent.StartView(RumScopeKey.from(key, name), attributes.toMap(), eventTime)
        )
    }

    override fun stopView(key: Any, attributes: Map<String, Any?>) {
        val eventTime = getEventTime(attributes)
        handleEvent(
            RumRawEvent.StopView(RumScopeKey.from(key), attributes.toMap(), eventTime)
        )
    }

    override fun addAction(type: RumActionType, name: String, attributes: Map<String, Any?>) {
        val eventTime = getEventTime(attributes)
        handleEvent(
            RumRawEvent.StartAction(type, name, false, attributes.toMap(), eventTime)
        )
    }

    override fun startAction(type: RumActionType, name: String, attributes: Map<String, Any?>) {
        val eventTime = getEventTime(attributes)
        handleEvent(
            RumRawEvent.StartAction(type, name, true, attributes.toMap(), eventTime)
        )
    }

    override fun stopAction(
        type: RumActionType,
        name: String,
        attributes: Map<String, Any?>
    ) {
        val eventTime = getEventTime(attributes)
        handleEvent(
            RumRawEvent.StopAction(type, name, attributes.toMap(), eventTime)
        )
    }

    override fun startResource(
        key: String,
        method: RumResourceMethod,
        url: String,
        attributes: Map<String, Any?>
    ) {
        val eventTime = getEventTime(attributes)
        handleEvent(
            RumRawEvent.StartResource(key, url, method, attributes.toMap(), eventTime)
        )
    }

    override fun stopResource(
        key: String,
        statusCode: Int?,
        size: Long?,
        kind: RumResourceKind,
        attributes: Map<String, Any?>
    ) {
        val eventTime = getEventTime(attributes)
        handleEvent(
            RumRawEvent.StopResource(
                key,
                statusCode?.toLong(),
                size,
                kind,
                attributes.toMap(),
                eventTime
            )
        )
    }

    override fun stopResourceWithError(
        key: String,
        statusCode: Int?,
        message: String,
        source: RumErrorSource,
        throwable: Throwable,
        attributes: Map<String, Any?>
    ) {
        handleEvent(
            RumRawEvent.StopResourceWithError(
                key,
                statusCode?.toLong(),
                message,
                source,
                throwable,
                attributes.toMap()
            )
        )
    }

    override fun stopResourceWithError(
        key: String,
        statusCode: Int?,
        message: String,
        source: RumErrorSource,
        stackTrace: String,
        errorType: String?,
        attributes: Map<String, Any?>
    ) {
        handleEvent(
            RumRawEvent.StopResourceWithStackTrace(
                key,
                statusCode?.toLong(),
                message,
                source,
                stackTrace,
                errorType,
                attributes.toMap()
            )
        )
    }

    override fun startResource(
        key: ResourceId,
        method: RumResourceMethod,
        url: String,
        attributes: Map<String, Any?>
    ) {
        val eventTime = getEventTime(attributes)
        handleEvent(
            RumRawEvent.StartResource(key, url, method, attributes.toMap(), eventTime)
        )
    }

    override fun stopResource(
        key: ResourceId,
        statusCode: Int?,
        size: Long?,
        kind: RumResourceKind,
        attributes: Map<String, Any?>
    ) {
        val eventTime = getEventTime(attributes)
        handleEvent(
            RumRawEvent.StopResource(
                key,
                statusCode?.toLong(),
                size,
                kind,
                attributes.toMap(),
                eventTime
            )
        )
    }

    override fun stopResourceWithError(
        key: ResourceId,
        statusCode: Int?,
        message: String,
        source: RumErrorSource,
        throwable: Throwable,
        attributes: Map<String, Any?>
    ) {
        handleEvent(
            RumRawEvent.StopResourceWithError(
                key,
                statusCode?.toLong(),
                message,
                source,
                throwable,
                attributes.toMap()
            )
        )
    }

    override fun stopResourceWithError(
        key: ResourceId,
        statusCode: Int?,
        message: String,
        source: RumErrorSource,
        stackTrace: String,
        errorType: String?,
        attributes: Map<String, Any?>
    ) {
        handleEvent(
            RumRawEvent.StopResourceWithStackTrace(
                key,
                statusCode?.toLong(),
                message,
                source,
                stackTrace,
                errorType,
                attributes.toMap()
            )
        )
    }

    override fun addError(
        message: String,
        source: RumErrorSource,
        throwable: Throwable?,
        attributes: Map<String, Any?>
    ) {
        val eventTime = getEventTime(attributes)
        val errorType = getErrorType(attributes)
        val mutableAttributes = attributes.toMutableMap()

        @Suppress("UNCHECKED_CAST")
        val threads = mutableAttributes.remove(RumAttributes.INTERNAL_ALL_THREADS) as? List<ThreadDump>
        handleEvent(
            RumRawEvent.AddError(
                message,
                source,
                throwable,
                null,
                false,
                mutableAttributes,
                eventTime,
                errorType,
                threads = threads.orEmpty()
            )
        )
    }

    override fun addErrorWithStacktrace(
        message: String,
        source: RumErrorSource,
        stacktrace: String?,
        attributes: Map<String, Any?>
    ) {
        val eventTime = getEventTime(attributes)
        val errorType = getErrorType(attributes)
        val errorSourceType = getErrorSourceType(attributes)
        handleEvent(
            RumRawEvent.AddError(
                message,
                source,
                null,
                stacktrace,
                false,
                attributes.toMap(),
                eventTime,
                errorType,
                errorSourceType,
                threads = emptyList()
            )
        )
    }

    override fun addFeatureFlagEvaluation(name: String, value: Any) {
        handleEvent(
            RumRawEvent.AddFeatureFlagEvaluation(
                name,
                value
            )
        )
    }

    override fun addFeatureFlagEvaluations(featureFlags: Map<String, Any>) {
        handleEvent(
            RumRawEvent.AddFeatureFlagEvaluations(featureFlags)
        )
    }

    override fun stopSession() {
        handleEvent(
            RumRawEvent.StopSession()
        )
    }

    // endregion

    // region RumMonitor/Attributes

    override fun addAttribute(key: String, value: Any?) {
        if (value == null) {
            globalAttributes.remove(key)
        } else {
            globalAttributes[key] = value
        }
    }

    override fun removeAttribute(key: String) {
        globalAttributes.remove(key)
    }

    override fun getAttributes(): Map<String, Any?> {
        return globalAttributes
    }

    override fun clearAttributes() {
        globalAttributes.clear()
    }

    // endregion

    // region AdvancedRumMonitor

    override fun sendWebViewEvent() {
        handleEvent(RumRawEvent.WebViewEvent())
    }

    override fun resetSession() {
        handleEvent(
            RumRawEvent.ResetSession()
        )
    }

    override fun start() {
        val processImportance = DdRumContentProvider.processImportance
        val isAppInForeground = processImportance ==
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        handleEvent(
            RumRawEvent.SdkInit(isAppInForeground)
        )
    }

    override fun waitForResourceTiming(key: Any) {
        handleEvent(
            RumRawEvent.WaitForResourceTiming(key)
        )
    }

    override fun addResourceTiming(key: Any, timing: ResourceTiming) {
        handleEvent(
            RumRawEvent.AddResourceTiming(key, timing)
        )
    }

    override fun addCrash(
        message: String,
        source: RumErrorSource,
        throwable: Throwable,
        threads: List<ThreadDump>
    ) {
        val now = Time()
        val timeSinceAppStartNs = now.nanoTime - sdkCore.appStartTimeNs
        handleEvent(
            RumRawEvent.AddError(
                message,
                source,
                throwable,
                stacktrace = null,
                isFatal = true,
                threads = threads,
                timeSinceAppStartNs = timeSinceAppStartNs,
                eventTime = now,
                attributes = emptyMap()
            )
        )
    }

    override fun addTiming(name: String) {
        handleEvent(
            RumRawEvent.AddCustomTiming(name)
        )
    }

    @ExperimentalRumApi
    override fun addViewLoadingTime(overwrite: Boolean) {
        handleEvent(RumRawEvent.AddViewLoadingTime(overwrite = overwrite))
    }

    override fun addViewAttributes(attributes: Map<String, Any?>) {
        handleEvent(
            RumRawEvent.AddViewAttributes(attributes)
        )
    }

    override fun removeViewAttributes(attributes: Collection<String>) {
        handleEvent(
            RumRawEvent.RemoveViewAttributes(attributes)
        )
    }

    override fun addLongTask(durationNs: Long, target: String) {
        handleEvent(
            RumRawEvent.AddLongTask(durationNs, target)
        )
    }

    override fun eventSent(viewId: String, event: StorageEvent) {
        when (event) {
            is StorageEvent.Action -> handleEvent(
                RumRawEvent.ActionSent(
                    viewId,
                    event.frustrationCount,
                    event.type,
                    event.eventEndTimestampInNanos
                )
            )

            is StorageEvent.Resource -> handleEvent(
                RumRawEvent.ResourceSent(
                    viewId,
                    event.resourceId,
                    event.resourceStopTimestampInNanos
                )
            )

            is StorageEvent.Error -> handleEvent(
                RumRawEvent.ErrorSent(
                    viewId,
                    event.resourceId,
                    event.resourceStopTimestampInNanos
                )
            )

            is StorageEvent.LongTask -> handleEvent(RumRawEvent.LongTaskSent(viewId, false))
            is StorageEvent.FrozenFrame -> handleEvent(RumRawEvent.LongTaskSent(viewId, true))
            is StorageEvent.View -> {
                // Nothing to do
            }
        }
    }

    override fun eventDropped(viewId: String, event: StorageEvent) {
        when (event) {
            is StorageEvent.Action -> handleEvent(RumRawEvent.ActionDropped(viewId))
            is StorageEvent.Resource -> handleEvent(RumRawEvent.ResourceDropped(viewId, event.resourceId))
            is StorageEvent.Error -> handleEvent(RumRawEvent.ErrorDropped(viewId, event.resourceId))
            is StorageEvent.LongTask -> handleEvent(RumRawEvent.LongTaskDropped(viewId, false))
            is StorageEvent.FrozenFrame -> handleEvent(RumRawEvent.LongTaskDropped(viewId, true))
            is StorageEvent.View -> {
                // Nothing to do
            }
        }
    }

    override fun setDebugListener(listener: RumDebugListener?) {
        debugListener = listener
    }

    override fun addSessionReplaySkippedFrame() {
        getCurrentSessionId { sessionId ->
            sessionId?.let {
                sessionEndedMetricDispatcher.onSessionReplaySkippedFrameTracked(it)
            }
        }
    }

    override fun notifyInterceptorInstantiated() {
        handleEvent(
            RumRawEvent.TelemetryEventWrapper(InternalTelemetryEvent.InterceptorInstantiated)
        )
    }

    override fun updatePerformanceMetric(metric: RumPerformanceMetric, value: Double) {
        handleEvent(RumRawEvent.UpdatePerformanceMetric(metric, value))
    }

    override fun updateExternalRefreshRate(frameTimeSeconds: Double) {
        handleEvent(RumRawEvent.UpdateExternalRefreshRate(frameTimeSeconds))
    }

    override fun setInternalViewAttribute(key: String, value: Any?) {
        handleEvent(RumRawEvent.SetInternalViewAttribute(key, value))
    }

    override fun setSyntheticsAttribute(
        testId: String,
        resultId: String
    ) {
        handleEvent(RumRawEvent.SetSyntheticsTestAttribute(testId, resultId))
    }

    override fun _getInternal(): _RumInternalProxy {
        return internalProxy
    }

    override fun sendTelemetryEvent(telemetryEvent: InternalTelemetryEvent) {
        handleEvent(RumRawEvent.TelemetryEventWrapper(telemetryEvent))
    }

    override fun enableJankStatsTracking(activity: Activity) {
        sdkCore.getFeature(Feature.RUM_FEATURE_NAME)
            ?.unwrap<RumFeature>()
            ?.enableJankStatsTracking(activity)
    }

    override fun sendTTIDEvent(
        info: RumTTIDInfo
    ) {
        handleEvent(
            RumRawEvent.AppStartTTIDEvent(
                info = info
            )
        )
    }

    override fun sendAppStartEvent(scenario: RumStartupScenario) {
        handleEvent(
            RumRawEvent.AppStartEvent(
                scenario = scenario
            )
        )
    }

    // endregion

    // region Feature Operations

    @ExperimentalRumApi
    override fun startFeatureOperation(name: String, operationKey: String?, attributes: Map<String, Any?>) {
        if (!featureOperationArgumentsValid(name, operationKey)) return

        handleEvent(
            RumRawEvent.StartFeatureOperation(
                name,
                operationKey,
                attributes.toMap(),
                eventTime = getEventTime(attributes)
            )
        )
        sdkCore.internalLogger.logToUser(InternalLogger.Level.DEBUG) {
            "Feature Operation `$name` (operationKey `$operationKey`) started."
        }
        sdkCore.internalLogger.reportFeatureOperationApiUsage(ActionType.START)
    }

    @ExperimentalRumApi
    override fun succeedFeatureOperation(name: String, operationKey: String?, attributes: Map<String, Any?>) {
        if (!featureOperationArgumentsValid(name, operationKey)) return

        handleEvent(
            RumRawEvent.StopFeatureOperation(
                name,
                operationKey,
                attributes.toMap(),
                failureReason = null,
                eventTime = getEventTime(attributes)
            )
        )
        sdkCore.internalLogger.logToUser(InternalLogger.Level.DEBUG) {
            "Feature Operation `$name` (operationKey `$operationKey`) successfully ended."
        }
        sdkCore.internalLogger.reportFeatureOperationApiUsage(ActionType.SUCCEED)
    }

    @ExperimentalRumApi
    override fun failFeatureOperation(
        name: String,
        operationKey: String?,
        failureReason: FailureReason,
        attributes: Map<String, Any?>
    ) {
        if (!featureOperationArgumentsValid(name, operationKey)) return

        handleEvent(
            RumRawEvent.StopFeatureOperation(
                name,
                operationKey,
                attributes.toMap(),
                failureReason = failureReason,
                eventTime = getEventTime(attributes)
            )
        )
        sdkCore.internalLogger.logToUser(InternalLogger.Level.DEBUG) {
            "Feature Operation `$name` (operationKey `$operationKey`) unsuccessfully ended" +
                " with the following failure reason: $failureReason."
        }
        sdkCore.internalLogger.reportFeatureOperationApiUsage(ActionType.FAIL)
    }

    @ExperimentalRumApi
    override fun reportAppFullyDisplayed() {
        handleEvent(
            RumRawEvent.AppStartTTFDEvent()
        )
    }

    private fun featureOperationArgumentsValid(name: String, operationKey: String?) = when {
        name.isBlank() -> {
            sdkCore.internalLogger.logToUser(InternalLogger.Level.WARN) {
                FO_ERROR_INVALID_NAME.format(Locale.US, name)
            }
            false
        }

        operationKey?.isBlank() == true -> {
            sdkCore.internalLogger.logToUser(InternalLogger.Level.WARN) {
                FO_ERROR_INVALID_OPERATION_KEY.format(Locale.US, operationKey)
            }
            false
        }

        else -> true
    }

    // endregion

    // region Internal

    @Throws(UnsupportedOperationException::class, InterruptedException::class)
    @Suppress("UnsafeThirdPartyFunctionCall") // Used in Nightly tests only
    internal fun drainExecutorService() {
        val tasks = arrayListOf<Runnable>()
        (executorService as? ThreadPoolExecutor)
            ?.queue
            ?.drainTo(tasks)
        executorService.shutdown()
        executorService.awaitTermination(DRAIN_WAIT_SECONDS, TimeUnit.SECONDS)
        tasks.forEach {
            it.run()
        }
    }

    internal fun handleEvent(event: RumRawEvent) {
        if (event is RumRawEvent.AddError && event.isFatal) {
            synchronized(rootScope) {
                // TODO RUM-9852 Implement better passthrough mechanism for the JVM crash scenario
                val writeContext = sdkCore.getFeature(Feature.RUM_FEATURE_NAME)
                    ?.getWriteContextSync(withFeatureContexts = setOf(Feature.SESSION_REPLAY_FEATURE_NAME))
                if (writeContext != null) {
                    val (datadogContext, eventWriteScope) = writeContext
                    @Suppress("ThreadSafety") // Crash handling, can't delegate to another thread
                    rootScope.handleEvent(event, datadogContext, eventWriteScope, writer)
                    val currentFeatureContext = currentRumContext()
                    sdkCore.updateFeatureContext(Feature.RUM_FEATURE_NAME) {
                        it.putAll(currentFeatureContext.toMap())
                    }
                } else {
                    sdkCore.internalLogger.log(
                        InternalLogger.Level.WARN,
                        InternalLogger.Target.USER,
                        { CANNOT_WRITE_CRASH_WRITE_CONTEXT_IS_NOT_AVAILABLE }
                    )
                }
            }
        } else if (event is RumRawEvent.TelemetryEventWrapper) {
            telemetryEventHandler.handleEvent(event, writer)
        } else {
            handler.removeCallbacks(keepAliveRunnable)
            sdkCore.getFeature(Feature.RUM_FEATURE_NAME)
                ?.withWriteContext(
                    withFeatureContexts = setOf(Feature.SESSION_REPLAY_FEATURE_NAME)
                ) { datadogContext, writeScope ->
                    // avoid trowing a RejectedExecutionException
                    if (!executorService.isShutdown) {
                        // we are already on the context thread, which is single and shared between the features, but we
                        // need still to delegate processing to the RUM-specific thread since it supports
                        // backpressure handling
                        val future = executorService.submitSafe(
                            "Rum event handling",
                            sdkCore.internalLogger,
                            NamedCallable<RumContext>("${event::class.simpleName}") {
                                synchronized(rootScope) {
                                    handleEventWithMethodCallPerf(event, datadogContext, writeScope)
                                    notifyDebugListenerWithState()
                                }
                                handler.postDelayed(keepAliveRunnable, KEEP_ALIVE_MS)
                                currentRumContext()
                            }
                        )
                        val rumContext = future.getSafe("Rum get context", sdkCore.internalLogger)
                        if (rumContext != null) {
                            // we are on the context thread already, so useContextThread=false
                            sdkCore.updateFeatureContext(Feature.RUM_FEATURE_NAME, useContextThread = false) {
                                it.putAll(rumContext.toMap())
                            }
                        }
                    }
                }
        }
    }

    @WorkerThread
    private fun handleEventWithMethodCallPerf(
        event: RumRawEvent,
        datadogContext: DatadogContext,
        writeScope: EventWriteScope
    ) {
        sdkCore.internalLogger.measureMethodCallPerf(
            javaClass,
            "RUM event - ${event::class.simpleName ?: "Unknown"}",
            MethodCallSamplingRate.RARE.rate
        ) {
            rootScope.handleEvent(event, datadogContext, writeScope, writer)
        }
    }

    /**
     * Wait for any pending events. This is mostly for integration tests to ensure that the
     * RUM context is in the correct state before proceeding.
     */
    @Suppress("unused")
    private fun waitForPendingEvents() {
        if (!executorService.isShutdown) {
            @Suppress("UnsafeThirdPartyFunctionCall") // 1 cannot be negative
            val latch = CountDownLatch(1)
            executorService.executeSafe("pending event waiting", sdkCore.internalLogger) {
                latch.countDown()
            }
            try {
                latch.await(1, TimeUnit.SECONDS)
            } catch (_: InterruptedException) {
                sdkCore.internalLogger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.MAINTAINER,
                    { "Waiting for pending RUM events was interrupted" }
                )
            }
        }
    }

    private fun currentRumContext(): RumContext {
        val activeSession = rootScope.activeSession
        val context = activeSession?.activeView?.getRumContext()
            ?: activeSession?.getRumContext()
            ?: rootScope.getRumContext()
        return context
    }

    internal fun stopKeepAliveCallback() {
        handler.removeCallbacks(keepAliveRunnable)
    }

    internal fun notifyDebugListenerWithState() {
        debugListener?.let {
            val sessionScope = rootScope.activeSession
            val viewManagerScope = sessionScope?.childScope
            if (viewManagerScope != null) {
                it.onReceiveRumActiveViews(
                    viewManagerScope.childrenScopes
                        .filter { viewScope -> viewScope.isActive() }
                        .mapNotNull { viewScope -> viewScope.getRumContext().viewName }
                )
            }
        }
    }

    private fun getEventTime(attributes: Map<String, Any?>): Time {
        return (attributes[RumAttributes.INTERNAL_TIMESTAMP] as? Long)?.asTime() ?: Time()
    }

    private fun getErrorType(attributes: Map<String, Any?>): String? {
        return attributes[RumAttributes.INTERNAL_ERROR_TYPE] as? String
    }

    private fun getErrorSourceType(attributes: Map<String, Any?>): RumErrorSourceType {
        val sourceType = attributes[RumAttributes.INTERNAL_ERROR_SOURCE_TYPE] as? String

        return when (sourceType?.lowercase(Locale.US)) {
            "android" -> RumErrorSourceType.ANDROID
            "react-native" -> RumErrorSourceType.REACT_NATIVE
            "browser" -> RumErrorSourceType.BROWSER
            "flutter" -> RumErrorSourceType.FLUTTER
            "ndk" -> RumErrorSourceType.NDK
            "ndk+il2cpp" -> RumErrorSourceType.NDK_IL2CPP
            else -> RumErrorSourceType.ANDROID
        }
    }

    // endregion

    companion object {
        internal val KEEP_ALIVE_MS = TimeUnit.MINUTES.toMillis(5)

        // should be aligned with CoreFeature#DRAIN_WAIT_SECONDS, but not a requirement
        internal const val DRAIN_WAIT_SECONDS = 10L

        internal const val RUM_DEBUG_RUM_NOT_ENABLED_WARNING =
            "Cannot switch RUM debugging, because RUM feature is not enabled."

        internal const val CANNOT_WRITE_CRASH_WRITE_CONTEXT_IS_NOT_AVAILABLE =
            "Cannot write JVM crash, because write context is not available."

        internal const val FO_ERROR_INVALID_NAME =
            "Feature operation name cannot be an empty or blank string but was \"%s\". Vital event won't be sent."

        internal const val FO_ERROR_INVALID_OPERATION_KEY =
            "Feature operation key cannot be an empty or blank string but was \"%s\". Vital event won't be sent."

        private fun InternalLogger.logToUser(
            level: InternalLogger.Level,
            messageProvider: () -> String
        ) = log(
            level = level,
            target = InternalLogger.Target.USER,
            messageBuilder = messageProvider
        )

        private fun InternalLogger.reportFeatureOperationApiUsage(actionType: ActionType) = logApiUsage {
            InternalTelemetryEvent.ApiUsage.AddOperationStepVital(actionType)
        }
    }
}
