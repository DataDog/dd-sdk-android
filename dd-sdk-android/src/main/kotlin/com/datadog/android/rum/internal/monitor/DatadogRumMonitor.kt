/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.monitor

import android.os.Handler
import com.datadog.android.core.internal.net.FirstPartyHostDetector
import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.internal.system.AndroidInfoProvider
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.core.internal.utils.loggableStackTrace
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.RumPerformanceMetric
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.RumSessionListener
import com.datadog.android.rum._RumInternalProxy
import com.datadog.android.rum.internal.CombinedRumSessionListener
import com.datadog.android.rum.internal.RumErrorSourceType
import com.datadog.android.rum.internal.debug.RumDebugListener
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.domain.asTime
import com.datadog.android.rum.internal.domain.event.ResourceTiming
import com.datadog.android.rum.internal.domain.scope.RumApplicationScope
import com.datadog.android.rum.internal.domain.scope.RumRawEvent
import com.datadog.android.rum.internal.domain.scope.RumScope
import com.datadog.android.rum.internal.domain.scope.RumSessionScope
import com.datadog.android.rum.internal.domain.scope.RumViewManagerScope
import com.datadog.android.rum.internal.domain.scope.RumViewScope
import com.datadog.android.rum.internal.vitals.VitalMonitor
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.telemetry.internal.TelemetryEventHandler
import com.datadog.android.telemetry.internal.TelemetryType
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

@Suppress("LongParameterList")
internal class DatadogRumMonitor(
    applicationId: String,
    internal val samplingRate: Float,
    internal val backgroundTrackingEnabled: Boolean,
    private val writer: DataWriter<Any>,
    internal val handler: Handler,
    internal val telemetryEventHandler: TelemetryEventHandler,
    firstPartyHostDetector: FirstPartyHostDetector,
    cpuVitalMonitor: VitalMonitor,
    memoryVitalMonitor: VitalMonitor,
    frameRateVitalMonitor: VitalMonitor,
    timeProvider: TimeProvider,
    sessionListener: RumSessionListener?,
    private val executorService: ExecutorService = Executors.newSingleThreadExecutor(),
    androidInfoProvider: AndroidInfoProvider
) : RumMonitor, AdvancedRumMonitor {

    internal var rootScope: RumScope = RumApplicationScope(
        applicationId,
        samplingRate,
        backgroundTrackingEnabled,
        firstPartyHostDetector,
        cpuVitalMonitor,
        memoryVitalMonitor,
        frameRateVitalMonitor,
        timeProvider,
        if (sessionListener != null) {
            CombinedRumSessionListener(sessionListener, telemetryEventHandler)
        } else {
            telemetryEventHandler
        },
        androidInfoProvider
    )

    internal val keepAliveRunnable = Runnable {
        handleEvent(RumRawEvent.KeepAlive())
    }

    internal var debugListener: RumDebugListener? = null

    private val internalProxy = _RumInternalProxy(this)

    init {
        handler.postDelayed(keepAliveRunnable, KEEP_ALIVE_MS)
    }

    // region RumMonitor

    override fun startView(key: Any, name: String, attributes: Map<String, Any?>) {
        val eventTime = getEventTime(attributes)
        handleEvent(
            RumRawEvent.StartView(key, name, attributes, eventTime)
        )
    }

    override fun stopView(key: Any, attributes: Map<String, Any?>) {
        val eventTime = getEventTime(attributes)
        handleEvent(
            RumRawEvent.StopView(key, attributes, eventTime)
        )
    }

    override fun addUserAction(type: RumActionType, name: String, attributes: Map<String, Any?>) {
        val eventTime = getEventTime(attributes)
        handleEvent(
            RumRawEvent.StartAction(type, name, false, attributes, eventTime)
        )
    }

    override fun startUserAction(type: RumActionType, name: String, attributes: Map<String, Any?>) {
        val eventTime = getEventTime(attributes)
        handleEvent(
            RumRawEvent.StartAction(type, name, true, attributes, eventTime)
        )
    }

    override fun stopUserAction(
        type: RumActionType,
        name: String,
        attributes: Map<String, Any?>
    ) {
        val eventTime = getEventTime(attributes)
        handleEvent(
            RumRawEvent.StopAction(type, name, attributes, eventTime)
        )
    }

    override fun startResource(
        key: String,
        method: String,
        url: String,
        attributes: Map<String, Any?>
    ) {
        val eventTime = getEventTime(attributes)
        handleEvent(
            RumRawEvent.StartResource(key, url, method, attributes, eventTime)
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
            RumRawEvent.StopResource(key, statusCode?.toLong(), size, kind, attributes, eventTime)
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
                attributes
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
                attributes
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
        handleEvent(
            RumRawEvent.AddError(
                message,
                source,
                throwable,
                null,
                false,
                attributes,
                eventTime,
                errorType
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
                attributes,
                eventTime,
                errorType,
                errorSourceType
            )
        )
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

    override fun waitForResourceTiming(key: String) {
        handleEvent(
            RumRawEvent.WaitForResourceTiming(key)
        )
    }

    override fun addResourceTiming(key: String, timing: ResourceTiming) {
        handleEvent(
            RumRawEvent.AddResourceTiming(key, timing)
        )
    }

    override fun addCrash(message: String, source: RumErrorSource, throwable: Throwable) {
        handleEvent(
            RumRawEvent.AddError(message, source, throwable, null, true, emptyMap())
        )
    }

    override fun updateViewLoadingTime(
        key: Any,
        loadingTimeInNs: Long,
        type: ViewEvent.LoadingType
    ) {
        handleEvent(
            RumRawEvent.UpdateViewLoadingTime(key, loadingTimeInNs, type)
        )
    }

    override fun addTiming(name: String) {
        handleEvent(
            RumRawEvent.AddCustomTiming(name)
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
                    event.frustrationCount
                )
            )
            is StorageEvent.Resource -> handleEvent(RumRawEvent.ResourceSent(viewId))
            is StorageEvent.Error -> handleEvent(RumRawEvent.ErrorSent(viewId))
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
            is StorageEvent.Resource -> handleEvent(RumRawEvent.ResourceDropped(viewId))
            is StorageEvent.Error -> handleEvent(RumRawEvent.ErrorDropped(viewId))
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

    override fun sendDebugTelemetryEvent(message: String) {
        handleEvent(RumRawEvent.SendTelemetry(TelemetryType.DEBUG, message, null, null))
    }

    override fun sendErrorTelemetryEvent(message: String, throwable: Throwable?) {
        val stack: String? = throwable?.loggableStackTrace()
        val kind: String? = throwable?.javaClass?.canonicalName ?: throwable?.javaClass?.simpleName
        handleEvent(RumRawEvent.SendTelemetry(TelemetryType.ERROR, message, stack, kind))
    }

    override fun sendErrorTelemetryEvent(message: String, stack: String?, kind: String?) {
        handleEvent(RumRawEvent.SendTelemetry(TelemetryType.ERROR, message, stack, kind))
    }

    override fun updatePerformanceMetric(metric: RumPerformanceMetric, value: Double) {
        handleEvent(RumRawEvent.UpdatePerformanceMetric(metric, value))
    }

    override fun _getInternal(): _RumInternalProxy? {
        return internalProxy
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
        executorService.awaitTermination(10, TimeUnit.SECONDS)
        tasks.forEach {
            it.run()
        }
    }

    internal fun handleEvent(event: RumRawEvent) {
        if (event is RumRawEvent.AddError && event.isFatal) {
            rootScope.handleEvent(event, writer)
        } else if (event is RumRawEvent.SendTelemetry) {
            telemetryEventHandler.handleEvent(event, writer)
        } else {
            handler.removeCallbacks(keepAliveRunnable)
            // avoid trowing a RejectedExecutionException
            if (!executorService.isShutdown) {
                try {
                    @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here
                    executorService.submit {
                        synchronized(rootScope) {
                            rootScope.handleEvent(event, writer)
                            notifyDebugListenerWithState()
                        }
                        handler.postDelayed(keepAliveRunnable, KEEP_ALIVE_MS)
                    }
                } catch (e: RejectedExecutionException) {
                    devLogger.e("Unable to handle a RUM event, the ", e)
                }
            }
        }
    }

    internal fun stopKeepAliveCallback() {
        handler.removeCallbacks(keepAliveRunnable)
    }

    internal fun notifyDebugListenerWithState() {
        debugListener?.let {
            val applicationScope = rootScope as? RumApplicationScope
            val sessionScope = applicationScope?.childScope as? RumSessionScope
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
            else -> RumErrorSourceType.ANDROID
        }
    }

    // endregion

    companion object {
        internal val KEEP_ALIVE_MS = TimeUnit.MINUTES.toMillis(5)
    }
}
