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
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.measureMethodCallPerf
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.feature.event.ThreadDump
import com.datadog.android.core.internal.net.FirstPartyHostHeaderTypeResolver
import com.datadog.android.core.internal.utils.executeSafe
import com.datadog.android.core.metrics.MethodCallSamplingRate
import com.datadog.android.internal.telemetry.InternalTelemetryEvent
import com.datadog.android.internal.thread.NamedRunnable
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
import com.datadog.android.rum._RumInternalProxy
import com.datadog.android.rum.internal.CombinedRumSessionListener
import com.datadog.android.rum.internal.RumErrorSourceType
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.debug.RumDebugListener
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.domain.asTime
import com.datadog.android.rum.internal.domain.event.ResourceTiming
import com.datadog.android.rum.internal.domain.scope.RumApplicationScope
import com.datadog.android.rum.internal.domain.scope.RumRawEvent
import com.datadog.android.rum.internal.domain.scope.RumScope
import com.datadog.android.rum.internal.domain.scope.RumScopeKey
import com.datadog.android.rum.internal.domain.scope.RumSessionScope
import com.datadog.android.rum.internal.domain.scope.RumViewManagerScope
import com.datadog.android.rum.internal.domain.scope.RumViewScope
import com.datadog.android.rum.internal.metric.SessionMetricDispatcher
import com.datadog.android.rum.internal.metric.slowframes.SlowFramesListener
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

@Suppress("LongParameterList", "LargeClass")
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
    slowFramesListener: SlowFramesListener?
) : RumMonitor, AdvancedRumMonitor {

    internal var rootScope: RumScope = RumApplicationScope(
        applicationId,
        sdkCore,
        sampleRate,
        backgroundTrackingEnabled,
        trackFrustrations,
        firstPartyHostHeaderTypeResolver,
        cpuVitalMonitor,
        memoryVitalMonitor,
        frameRateVitalMonitor,
        sessionEndedMetricDispatcher = sessionEndedMetricDispatcher,
        CombinedRumSessionListener(sessionListener, telemetryEventHandler),
        initialResourceIdentifier,
        lastInteractionIdentifier,
        slowFramesListener
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
            val activeSessionId = (rootScope as? RumApplicationScope)
                ?.activeSession
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
                sdkCore.internalLogger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.USER,
                    { RUM_DEBUG_RUM_NOT_ENABLED_WARNING }
                )
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

    @Deprecated(
        "This method is deprecated and will be removed in the future versions." +
            " Use `startResource` method which takes `RumHttpMethod` as `method` parameter instead."
    )
    override fun startResource(
        key: String,
        method: String,
        url: String,
        attributes: Map<String, Any?>
    ) {
        // enum value names may be changed if obfuscation is aggressive
        val rumResourceMethod = when (method.uppercase(Locale.US)) {
            "POST" -> RumResourceMethod.POST
            "GET" -> RumResourceMethod.GET
            "HEAD" -> RumResourceMethod.HEAD
            "PUT" -> RumResourceMethod.PUT
            "DELETE" -> RumResourceMethod.DELETE
            "PATCH" -> RumResourceMethod.PATCH
            "CONNECT" -> RumResourceMethod.CONNECT
            "TRACE" -> RumResourceMethod.TRACE
            "OPTIONS" -> RumResourceMethod.OPTIONS
            else -> {
                sdkCore.internalLogger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.USER,
                    {
                        "Unsupported HTTP method %s reported, using GET instead".format(
                            Locale.US,
                            method
                        )
                    }
                )
                RumResourceMethod.GET
            }
        }
        startResource(key, rumResourceMethod, url, attributes)
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
                @Suppress("ThreadSafety") // Crash handling, can't delegate to another thread
                rootScope.handleEvent(event, writer)
            }
        } else if (event is RumRawEvent.TelemetryEventWrapper) {
            telemetryEventHandler.handleEvent(event, writer)
        } else {
            handler.removeCallbacks(keepAliveRunnable)
            // avoid trowing a RejectedExecutionException
            if (!executorService.isShutdown) {
                executorService.executeSafe(
                    "Rum event handling",
                    sdkCore.internalLogger,
                    NamedRunnable("${event::class.simpleName}") {
                        synchronized(rootScope) {
                            handleEventWithMethodCallPerf(event)
                            notifyDebugListenerWithState()
                        }
                        handler.postDelayed(keepAliveRunnable, KEEP_ALIVE_MS)
                    }
                )
            }
        }
    }

    @WorkerThread
    private fun handleEventWithMethodCallPerf(event: RumRawEvent) {
        sdkCore.internalLogger.measureMethodCallPerf(
            javaClass,
            "RUM event - ${event::class.simpleName ?: "Unknown"}",
            MethodCallSamplingRate.RARE.rate
        ) {
            rootScope.handleEvent(event, writer)
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

    internal fun stopKeepAliveCallback() {
        handler.removeCallbacks(keepAliveRunnable)
    }

    internal fun notifyDebugListenerWithState() {
        debugListener?.let {
            val applicationScope = rootScope as? RumApplicationScope
            val sessionScope = applicationScope?.activeSession as? RumSessionScope
            val viewManagerScope = sessionScope?.childScope as? RumViewManagerScope
            if (viewManagerScope != null) {
                it.onReceiveRumActiveViews(
                    viewManagerScope.childrenScopes
                        .filterIsInstance<RumViewScope>()
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
    }
}
