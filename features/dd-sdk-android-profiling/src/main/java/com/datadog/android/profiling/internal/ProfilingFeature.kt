/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureContextUpdateReceiver
import com.datadog.android.api.feature.FeatureEventReceiver
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.feature.StorageBackedFeature
import com.datadog.android.api.net.RequestFactory
import com.datadog.android.api.storage.FeatureStorageConfiguration
import com.datadog.android.internal.FeatureContextKeys
import com.datadog.android.internal.lifecycle.ProcessLifecycleMonitor
import com.datadog.android.internal.profiling.ProfilerEvent
import com.datadog.android.internal.profiling.ProfilingAnrDetectedEvent
import com.datadog.android.internal.rum.RumSessionConstants
import com.datadog.android.internal.time.DefaultTimeProvider
import com.datadog.android.profiling.ExperimentalProfilingApi
import com.datadog.android.profiling.ProfilingConfiguration
import com.datadog.android.profiling.internal.perfetto.PerfettoResult
import com.datadog.android.profiling.internal.quota.NoOpQuotaChecker
import com.datadog.android.profiling.internal.quota.ProfilingQuotaChecker
import com.datadog.android.profiling.internal.quota.QuotaChecker
import com.datadog.android.profiling.internal.quota.QuotaResult
import com.datadog.android.profiling.internal.utils.getProfilingModuleLongVersionCode
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalProfilingApi::class)
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
internal class ProfilingFeature(
    private val sdkCore: FeatureSdkCore,
    private val configuration: ProfilingConfiguration,
    private val profiler: Profiler
) : StorageBackedFeature, FeatureEventReceiver, FeatureContextUpdateReceiver, ProfilerCallback {

    @Volatile
    internal var lastSeenRumSessionId: String? = null

    internal var dataWriter: ProfilingWriter = NoOpProfilingWriter()

    internal val pendingRumEvents = PendingRumEventsBuffer()

    @Volatile
    private var isLaunchProfilingActive: Boolean = false

    @Volatile
    private var perfettoResult: PerfettoResult? = null

    private val isTtidVitalReceived: AtomicBoolean = AtomicBoolean(false)
    private val isTtidProfileSent: AtomicBoolean = AtomicBoolean(false)

    @Volatile
    internal var quotaChecker: QuotaChecker = NoOpQuotaChecker()

    @Volatile
    private var quotaExecutor: ExecutorService? = null

    private lateinit var appContext: Context

    @Volatile
    internal var continuousProfilingScheduler: ContinuousProfilingScheduler? = null

    private var processLifecycleMonitor: ProcessLifecycleMonitor? = null

    @Volatile
    private var lastQuotaResult: QuotaResult? = null

    override val requestFactory: RequestFactory = ProfilingRequestFactory(
        customEndpointUrl = configuration.customEndpointUrl,
        internalLogger = sdkCore.internalLogger
    )

    override val storageConfiguration: FeatureStorageConfiguration
        get() = FeatureStorageConfiguration.DEFAULT.copy(
            maxItemsPerBatch = 1
        )

    override val name: String
        get() = Feature.PROFILING_FEATURE_NAME

    override fun onInitialize(appContext: Context) {
        this.appContext = appContext
        profiler.apply {
            this.internalLogger = sdkCore.internalLogger
            this.timeProvider.delegate = sdkCore.timeProvider
            setProfilingPackageVersionCode(
                appContext.packageManager.getProfilingModuleLongVersionCode(sdkCore.internalLogger)
            )
            registerProfilingCallback(appContext, this@ProfilingFeature)
        }
        ProfilingStorage.setSampleRate(appContext, configuration.applicationLaunchSampleRate)
        // Set the profiling flag in SharedPreferences to profile for the next app launch
        ProfilingStorage.addProfilingFlag(appContext)
        isLaunchProfilingActive = profiler.isRunning()
        sdkCore.setEventReceiver(name, this)
        sdkCore.updateFeatureContext(Feature.PROFILING_FEATURE_NAME) { context ->
            context[FeatureContextKeys.PROFILER_IS_RUNNING] = profiler.isRunning()
        }
        dataWriter = createDataWriter(sdkCore)

        val quotaCallFactory = sdkCore.createOkHttpCallFactory {
            callTimeout(QUOTA_CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        }
        val qExecutor = sdkCore.createSingleThreadExecutorService(QUOTA_EXECUTOR_CONTEXT)
        quotaExecutor = qExecutor
        quotaChecker = ProfilingQuotaChecker(
            callFactory = quotaCallFactory,
            executor = qExecutor,
            internalLogger = sdkCore.internalLogger,
            onResult = ::propagateQuotaResult
        )

        val scheduler = ContinuousProfilingScheduler(
            appContext = appContext,
            profiler = profiler,
            sdkCore = sdkCore,
            timeProvider = DefaultTimeProvider(),
            sampleRate = configuration.continuousSampleRate,
            onActiveWindowStarted = pendingRumEvents::clear
        ).apply {
            start(launchProfilingActive = profiler.isRunning())
        }
        continuousProfilingScheduler = scheduler

        sdkCore.setContextUpdateReceiver(this)

        if (appContext is Application) {
            processLifecycleMonitor = ProcessLifecycleMonitor(ProfilingLifecycleCallback(scheduler)).apply {
                appContext.registerActivityLifecycleCallbacks(this)
            }
        }
    }

    override fun onStop() {
        processLifecycleMonitor?.let { monitor ->
            (appContext as? Application)?.unregisterActivityLifecycleCallbacks(monitor)
        }
        processLifecycleMonitor = null
        continuousProfilingScheduler?.stop()
        profiler.apply {
            stop()
            unregisterProfilingCallback(appContext)
        }
        sdkCore.removeEventReceiver(name)
        sdkCore.removeContextUpdateReceiver(this)
        quotaChecker.reset()
        quotaChecker = NoOpQuotaChecker()
        quotaExecutor?.shutdownNow()
        quotaExecutor = null
        lastQuotaResult = null
        lastSeenRumSessionId = null
        pendingRumEvents.clear()
    }

    override fun onReceive(event: Any) {
        when (event) {
            is ProfilerEvent.TTIDNotTracked -> onTtidEvent()

            is ProfilerEvent.RumVitalEvent -> {
                if (isRecordingProfile()) {
                    pendingRumEvents.add(event)
                }

                if (event.type == ProfilerEvent.RumVitalEvent.Type.TTID) {
                    onTtidEvent()
                }
            }

            is ProfilerEvent.RumLongTaskEvent -> {
                if (isRecordingProfile()) {
                    pendingRumEvents.add(event)
                }
            }

            is ProfilerEvent.RumAnrEvent -> {
                if (isRecordingProfile()) {
                    pendingRumEvents.add(event)
                }
            }

            else -> sdkCore.internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.MAINTAINER,
                {
                    UNSUPPORTED_EVENT_TYPE.format(
                        Locale.US,
                        event::class.java.canonicalName
                    )
                }
            )
        }
    }

    override fun onSuccess(result: PerfettoResult) {
        perfettoResult = result
        tryWriteProfilingEvent()
        sdkCore.updateFeatureContext(Feature.PROFILING_FEATURE_NAME) { context ->
            context[FeatureContextKeys.PROFILER_IS_RUNNING] = profiler.isRunning()
        }
    }

    override fun onFailure(startReason: ProfilingStartReason) {
        if (startReason == ProfilingStartReason.APPLICATION_LAUNCH) {
            // Launch profiling ended with error such as rate limiting error.
            // Unblock the continuous scheduler so it doesn't wait forever.
            isLaunchProfilingActive = false
            pendingRumEvents.clear()
            continuousProfilingScheduler?.onAppLaunchProfilingComplete()
        } else if (startReason == ProfilingStartReason.CONTINUOUS) {
            continuousProfilingScheduler?.onActiveWindowEnded()
        }
        sdkCore.updateFeatureContext(Feature.PROFILING_FEATURE_NAME) { context ->
            context[FeatureContextKeys.PROFILER_IS_RUNNING] = profiler.isRunning()
        }
    }

    override fun onAnrDetected(event: ProfilingAnrDetectedEvent) {
        // The ANR event should be forwarded to RUM only when profiling is actually running.
        if (isLaunchProfilingActive || continuousProfilingScheduler?.isActive == true) {
            sdkCore.getFeature(Feature.RUM_FEATURE_NAME)?.sendEvent(event)
        }
    }

    private fun onTtidEvent() {
        if (isTtidVitalReceived.getAndSet(true)) return

        if (continuousProfilingScheduler?.currentSessionSampled != true) {
            profiler.stop()
            tryWriteProfilingEvent()
            sdkCore.internalLogger.log(
                InternalLogger.Level.INFO,
                InternalLogger.Target.USER,
                { LOG_LAUNCH_PROFILING_STOPPED_AT_TTID }
            )
        }
    }

    override fun onContextUpdate(featureName: String, context: Map<String, Any?>) {
        if (featureName != Feature.RUM_FEATURE_NAME) return
        val sessionId = context[FeatureContextKeys.RUM_SESSION_ID] as? String
        if (sessionId == null ||
            sessionId == RumSessionConstants.EMPTY_RUM_SESSION_ID ||
            sessionId == lastSeenRumSessionId
        ) {
            return
        }
        this.lastQuotaResult = null
        continuousProfilingScheduler?.lastQuotaResult = null
        val sampleRate = (context[FeatureContextKeys.RUM_SESSION_SAMPLE_RATE] as? Number)?.toFloat()
            ?: DEFAULT_RUM_SESSION_SAMPLE_RATE
        lastSeenRumSessionId = sessionId
        sdkCore.getFeature(Feature.PROFILING_FEATURE_NAME)?.withContext { datadogContext ->
            quotaChecker.checkAsync(sessionId, datadogContext)
        }
        continuousProfilingScheduler?.onRumSessionRenewed(
            sessionId = sessionId,
            rumSessionSampleRate = sampleRate
        )
    }

    @Suppress("ReturnCount")
    private fun tryWriteProfilingEvent() {
        val result = perfettoResult ?: return
        when (result.startReason) {
            ProfilingStartReason.APPLICATION_LAUNCH -> {
                // Wait until both the TTID event and the quota decision have been received before
                // proceeding — the profiler result, the TTID event and the quota result are all
                // required. If the quota decision has not arrived yet, hold the buffered result and
                // return; the quota callback (or the timeout fallback) will re-trigger this write
                // once it lands. Capture the result once to avoid a re-read race with a concurrent
                // session renewal resetting it to null.
                val quotaResult = this.lastQuotaResult ?: return
                if (isTtidVitalReceived.get() && !isTtidProfileSent.getAndSet(true)) {
                    isLaunchProfilingActive = false
                    if (quotaResult.decision == QuotaResult.Decision.DENIED) {
                        logToUser(
                            LOG_LAUNCH_PROFILING_DROPPED_QUOTA_DENIED.format(
                                Locale.US,
                                quotaResult.reason.rawValue
                            )
                        )
                        dataWriter.discard(result)
                        pendingRumEvents.clear()
                    } else {
                        val (longTasks, anrEvents, vitalEvents) = pendingRumEvents.drain()
                        dataWriter.write(
                            profilingResult = result,
                            longTasks = longTasks,
                            anrEvents = anrEvents,
                            vitalEvents = vitalEvents
                        )
                    }
                    // Clear the consumed result so a later quota callback can't re-trigger a write.
                    perfettoResult = null
                    continuousProfilingScheduler?.onAppLaunchProfilingComplete()
                }
            }

            ProfilingStartReason.CONTINUOUS -> {
                val scheduler = continuousProfilingScheduler ?: return
                scheduler.onActiveWindowEnded()
                val (longTasks, anrEvents, vitalEvents) = pendingRumEvents.drain()
                dataWriter.write(
                    profilingResult = result,
                    longTasks = longTasks,
                    anrEvents = anrEvents,
                    vitalEvents = vitalEvents
                )
                perfettoResult = null
                if (longTasks.isEmpty() && anrEvents.isEmpty() && vitalEvents.isEmpty()) {
                    logToUser(LOG_CONTINUOUS_PROFILING_NOT_UPLOADED_NO_RUM_EVENTS)
                } else {
                    logToUser(
                        LOG_CONTINUOUS_PROFILING_WRITTEN.format(
                            Locale.US,
                            longTasks.size,
                            anrEvents.size
                        )
                    )
                }
            }

            else -> {
                // do nothing for the moment
            }
        }
    }

    private fun logToUser(message: String) {
        sdkCore.internalLogger.log(
            level = InternalLogger.Level.DEBUG,
            target = InternalLogger.Target.USER,
            messageBuilder = {
                message
            }
        )
    }

    private fun createDataWriter(sdkCore: FeatureSdkCore): ProfilingDataWriter {
        return ProfilingDataWriter(sdkCore)
    }

    internal fun propagateQuotaResult(result: QuotaResult) {
        this.lastQuotaResult = result
        continuousProfilingScheduler?.lastQuotaResult = result
        sdkCore.updateFeatureContext(Feature.PROFILING_FEATURE_NAME) { context ->
            if (result.decision == QuotaResult.Decision.DENIED) {
                context[FeatureContextKeys.PROFILING_QUOTA_REASON] = result.reason.rawValue
                context[FeatureContextKeys.PROFILING_QUOTA_SESSION_ID] = lastSeenRumSessionId
            } else {
                context.remove(FeatureContextKeys.PROFILING_QUOTA_REASON)
                context.remove(FeatureContextKeys.PROFILING_QUOTA_SESSION_ID)
            }
        }
        tryWriteProfilingEvent()
    }

    private fun isRecordingProfile(): Boolean {
        return isLaunchProfilingActive || continuousProfilingScheduler?.isActive == true
    }

    companion object {

        private const val DEFAULT_RUM_SESSION_SAMPLE_RATE = 0f
        private const val UNSUPPORTED_EVENT_TYPE =
            "Profiling feature received an event of unsupported type=%s."
        private const val LOG_LAUNCH_PROFILING_STOPPED_AT_TTID =
            "Launch profiling stopped at TTID."
        private const val LOG_CONTINUOUS_PROFILING_NOT_UPLOADED_NO_RUM_EVENTS =
            "Continuous profiling result not uploaded: no pending RUM events."
        private const val LOG_CONTINUOUS_PROFILING_WRITTEN =
            "Continuous profiling result written: %d long task(s), %d ANR event(s)."
        internal const val QUOTA_CHECK_TIMEOUT_MS = 5_000L
        private const val QUOTA_EXECUTOR_CONTEXT = "dd-profiling-quota"
        internal const val LOG_LAUNCH_PROFILING_DROPPED_QUOTA_DENIED =
            "Launch profiling dropped: quota denied (reason=%s)."
    }
}
