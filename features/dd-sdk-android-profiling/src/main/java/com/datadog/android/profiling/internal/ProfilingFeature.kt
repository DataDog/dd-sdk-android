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
import com.datadog.android.core.internal.utils.executeSafe
import com.datadog.android.internal.FeatureContextKeys
import com.datadog.android.internal.lifecycle.ProcessLifecycleMonitor
import com.datadog.android.internal.profiling.ProfilerEvent
import com.datadog.android.internal.profiling.ProfilingAnomalyDetectedEvent
import com.datadog.android.internal.profiling.ProfilingAnrDetectedEvent
import com.datadog.android.internal.rum.RumSessionConstants
import com.datadog.android.internal.time.DefaultTimeProvider
import com.datadog.android.internal.utils.bootNtpOffsetNs
import com.datadog.android.profiling.ExperimentalProfilingApi
import com.datadog.android.profiling.ProfilingConfiguration
import com.datadog.android.profiling.internal.perfetto.PerfettoResult
import com.datadog.android.profiling.internal.quota.NoOpQuotaChecker
import com.datadog.android.profiling.internal.quota.ProfilingQuotaChecker
import com.datadog.android.profiling.internal.quota.QuotaChecker
import com.datadog.android.profiling.internal.quota.QuotaResult
import com.datadog.android.profiling.internal.trigger.NoOpPendingTriggerProfiles
import com.datadog.android.profiling.internal.trigger.PendingOomGatingEvent
import com.datadog.android.profiling.internal.trigger.PendingOomProfile
import com.datadog.android.profiling.internal.trigger.PendingTriggerProfileStorage
import com.datadog.android.profiling.internal.trigger.PendingTriggerProfiles
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledExecutorService
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
    internal var pendingTriggerProfiles: PendingTriggerProfiles = NoOpPendingTriggerProfiles()

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
        dataWriter = ProfilingDataWriter(sdkCore)
        pendingTriggerProfiles = createPendingTriggerProfileStorage(
            executor = profiler.scheduledExecutorService
        )
        profiler.apply {
            this.timeProvider.delegate = sdkCore.timeProvider
            resolveProfilingPackageVersionCode(appContext)
            this.internalLogger = sdkCore.internalLogger
            setAnrTriggerEnabled(configuration.anrTriggerEnabled)
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

        profiler.scheduledExecutorService.executeSafe(
            OPERATION_UPLOAD_PENDING_OOM_PROFILE,
            sdkCore.internalLogger
        ) {
            uploadPendingOomProfile()
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
            setTriggersEnabled(appContext, false)
            unregisterProfilingCallback(appContext)
        }
        sdkCore.removeEventReceiver(name)
        sdkCore.removeContextUpdateReceiver(this)
        quotaChecker.reset()
        quotaChecker = NoOpQuotaChecker()
        quotaExecutor?.shutdownNow()
        quotaExecutor = null
        pendingTriggerProfiles.stop()
        pendingTriggerProfiles = NoOpPendingTriggerProfiles()
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
                pendingTriggerProfiles.setRumGatingEvent(event)
            }

            is ProfilerEvent.RumOomErrorEvent -> {
                // Persisted immediately: the OS trigger result may not arrive before this
                // process dies, in which case it is only delivered on a later launch.
                ProfilingStorage.setPendingOomGatingEvent(
                    appContext,
                    PendingOomGatingEvent(
                        rumErrorId = event.id,
                        timestampMs = event.timestamp,
                        rumContext = event.rumContext
                    )
                )
                pendingTriggerProfiles.setRumGatingEvent(event)
            }

            is ProfilerEvent.RumAnomalyErrorEvent -> {
                pendingTriggerProfiles.setRumGatingEvent(event)
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

    override fun onAnrDetected(event: ProfilingAnrDetectedEvent, result: PerfettoResult) {
        sdkCore.getFeature(Feature.RUM_FEATURE_NAME)?.sendEvent(event)
        pendingTriggerProfiles.setProfilingResult(result)
    }

    override fun onOutOfMemoryDetected(result: PerfettoResult) {
        // RUM already generates its own OOM error event, so the profiling feature
        // does not forward a separate OOM event. The gating signal (RumOomErrorEvent)
        // will arrive from RUM and complete the pair.
        pendingTriggerProfiles.setProfilingResult(result)
        // A pending marker here means a previous launch received the RUM gating event but died
        // before this trigger result arrived: match them now instead of waiting for yet another
        // launch. If setProfilingResult() above already found an in-process match, the marker was
        // already cleared by persistPendingOomProfile() and this is a no-op.
        val deferredGatingEvent = ProfilingStorage.getPendingOomGatingEvent(appContext) ?: return
        val ageMs = sdkCore.timeProvider.getServerTimestampMillis() - deferredGatingEvent.timestampMs
        if (ageMs >= PENDING_OOM_PROFILE_MAX_AGE_MS) {
            ProfilingStorage.removePendingOomGatingEvent(appContext)
            return
        }
        persistPendingOomProfile(
            result,
            ProfilerEvent.RumOomErrorEvent(
                id = deferredGatingEvent.rumErrorId,
                timestamp = deferredGatingEvent.timestampMs,
                rumContext = deferredGatingEvent.rumContext
            )
        )
        profiler.scheduledExecutorService.executeSafe(
            OPERATION_UPLOAD_PENDING_OOM_PROFILE,
            sdkCore.internalLogger
        ) {
            uploadPendingOomProfile()
        }
    }

    override fun onMemoryAnomalyDetected(result: PerfettoResult) {
        sdkCore.getFeature(Feature.RUM_FEATURE_NAME)?.sendEvent(
            ProfilingAnomalyDetectedEvent(result.start)
        )
        pendingTriggerProfiles.setProfilingResult(result)
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
        when {
            sessionId == null || sessionId == RumSessionConstants.EMPTY_RUM_SESSION_ID -> {
                profiler.setTriggersEnabled(appContext, false)
            }

            sessionId == lastSeenRumSessionId -> {
                profiler.setTriggersEnabled(appContext, isRumSessionTracked(context))
            }

            else -> {
                onNewRumSession(sessionId, context)
            }
        }
    }

    private fun isRumSessionTracked(context: Map<String, Any?>): Boolean {
        return context[FeatureContextKeys.RUM_SESSION_STATE] ==
            RumSessionConstants.SESSION_STATE_TRACKED
    }

    private fun onNewRumSession(sessionId: String, context: Map<String, Any?>) {
        this.lastQuotaResult = null
        continuousProfilingScheduler?.lastQuotaResult = null
        val sampleRate = (context[FeatureContextKeys.RUM_SESSION_SAMPLE_RATE] as? Number)?.toFloat()
            ?: DEFAULT_RUM_SESSION_SAMPLE_RATE
        lastSeenRumSessionId = sessionId
        profiler.setTriggersEnabled(appContext, isRumSessionTracked(context))
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
                        dataWriter.writeManualProfile(
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
                dataWriter.writeManualProfile(
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

    private fun createPendingTriggerProfileStorage(
        executor: ScheduledExecutorService
    ): PendingTriggerProfiles {
        return PendingTriggerProfileStorage(
            executor = executor,
            timeProvider = sdkCore.timeProvider,
            internalLogger = sdkCore.internalLogger,
            onMatch = { perfettoResult, profilerEvent ->
                when (profilerEvent) {
                    is ProfilerEvent.RumAnrEvent -> {
                        val quotaResult = lastQuotaResult
                        if (quotaResult?.decision == QuotaResult.Decision.DENIED) {
                            logToUser(
                                LOG_TRIGGER_PROFILING_DROPPED_QUOTA_DENIED.format(
                                    Locale.US,
                                    quotaResult.reason.rawValue
                                )
                            )
                            dataWriter.discard(perfettoResult)
                        } else {
                            dataWriter.writeTriggerProfile(
                                perfettoResult = perfettoResult,
                                rumErrorId = profilerEvent.id,
                                rumContext = profilerEvent.rumContext
                            )
                        }
                    }

                    is ProfilerEvent.RumOomErrorEvent ->
                        // The OOM is about to take the process down, so the match is recorded
                        // for the next launch instead of written now: the batch write would not
                        // complete, and reading the trace would allocate on a heap that has just
                        // been exhausted.
                        persistPendingOomProfile(perfettoResult, profilerEvent)

                    is ProfilerEvent.RumAnomalyErrorEvent ->
                        dataWriter.writeTriggerProfile(
                            perfettoResult = perfettoResult,
                            rumErrorId = profilerEvent.id,
                            rumContext = profilerEvent.rumContext
                        )

                    else -> {
                        // Not a currently supported trigger-match type: nothing to write.
                    }
                }
            }
        )
    }

    private fun persistPendingOomProfile(
        perfettoResult: PerfettoResult,
        event: ProfilerEvent.RumOomErrorEvent
    ) {
        ProfilingStorage.setPendingOomProfile(
            appContext,
            PendingOomProfile(
                resultFilePath = perfettoResult.resultFilePath,
                startMs = perfettoResult.start,
                endMs = perfettoResult.end,
                bootNtpNs = sdkCore.timeProvider.bootNtpOffsetNs(),
                rumErrorId = event.id,
                rumContext = event.rumContext
            )
        )
        // The pair is now matched, so the standalone gating marker (if any) is no longer needed.
        ProfilingStorage.removePendingOomGatingEvent(appContext)
    }

    /**
     * Uploads the OOM profile left behind by a previous process, if there is one. The trace file
     * is still where the platform profiler wrote it; only the few values needed to describe it
     * were persisted, because the process that captured it had no memory to spare.
     */
    private fun uploadPendingOomProfile() {
        val pending = ProfilingStorage.getPendingOomProfile(appContext) ?: return
        // Clear before uploading: if the upload itself fails, this costs one profile rather than
        // retrying the same marker on every subsequent launch.
        ProfilingStorage.removePendingOomProfile(appContext)
        val ageMs = sdkCore.timeProvider.getDeviceTimestampMillis() - pending.startMs
        val perfettoResult = PerfettoResult(
            start = pending.startMs,
            startReason = ProfilingStartReason.OUT_OF_MEMORY,
            end = pending.endMs,
            resultFilePath = pending.resultFilePath,
            bootNtpNs = pending.bootNtpNs
        )
        if (ageMs >= PENDING_OOM_PROFILE_MAX_AGE_MS) {
            logToUser(LOG_PENDING_OOM_PROFILE_EXPIRED)
            dataWriter.discard(perfettoResult)
            return
        }
        dataWriter.writeTriggerProfile(
            perfettoResult = perfettoResult,
            rumErrorId = pending.rumErrorId,
            rumContext = pending.rumContext
        )
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
        private const val QUOTA_EXECUTOR_CONTEXT = "profiling-quota"
        internal const val LOG_LAUNCH_PROFILING_DROPPED_QUOTA_DENIED =
            "Launch profiling dropped: quota denied (reason=%s)."
        internal const val LOG_TRIGGER_PROFILING_DROPPED_QUOTA_DENIED =
            "ANR trigger profile dropped: quota denied (reason=%s)."
        internal const val LOG_PENDING_OOM_PROFILE_EXPIRED =
            "Out of memory profile from a previous run dropped: too old to upload."
        private const val OPERATION_UPLOAD_PENDING_OOM_PROFILE = "upload_pending_oom_profile"

        /**
         * How long an OOM profile persisted by a previous process stays worth uploading. Matches
         * the window the late-crash reporter uses to decide a stored RUM view event is still
         * relevant, so both cross-launch paths age out together.
         */
        internal val PENDING_OOM_PROFILE_MAX_AGE_MS = TimeUnit.HOURS.toMillis(4)
    }
}
