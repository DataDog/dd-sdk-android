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
import com.datadog.android.profiling.internal.trigger.NoOpPendingTriggerProfiles
import com.datadog.android.profiling.internal.trigger.PendingTriggerProfileStorage
import com.datadog.android.profiling.internal.trigger.PendingTriggerProfiles
import com.datadog.android.profiling.internal.utils.fileDeleteSafe
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalProfilingApi::class)
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@Suppress("TooManyFunctions")
internal class ProfilingFeature(
    private val sdkCore: FeatureSdkCore,
    private val configuration: ProfilingConfiguration,
    private val profiler: Profiler
) : StorageBackedFeature,
    FeatureEventReceiver,
    FeatureContextUpdateReceiver,
    ProfilerCallback,
    ProfilingStatusListener {

    @Volatile
    internal var lastSeenRumSessionId: String? = null

    // RUM session the app launch profile belongs to: the first session seen while it is recorded.
    // Null until that session is seen. Its state and quota decision are kept apart from later
    // sessions so the launch profile is judged by its own session only. Guarded by quotaSessionLock.
    private var launchSessionId: String? = null
    private var isLaunchSessionTracked: Boolean = false
    private var launchSessionQuotaResult: QuotaResult? = null

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

    // Trigger profiles matched before a quota decision exists for their session.
    private val triggerProfilesAwaitingQuota = mutableListOf<TriggerProfile>()

    // Guards [lastSeenRumSessionId] updates against the quota check and trigger profile queueing
    // that depend on it being the current session.
    private val quotaSessionLock = Any()

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
            registerProfilerStatusListener(this@ProfilingFeature)
        }
        ProfilingStorage.setSampleRate(appContext, configuration.applicationLaunchSampleRate)
        // Set the profiling flag in SharedPreferences to profile for the next app launch
        ProfilingStorage.addProfilingFlag(appContext)
        isLaunchProfilingActive = profiler.isRunning()
        sdkCore.setEventReceiver(name, this)
        sdkCore.updateFeatureContext(Feature.PROFILING_FEATURE_NAME) { context ->
            context[FeatureContextKeys.PROFILING_SAMPLE_RATE] = configuration.continuousSampleRate
            context[FeatureContextKeys.PROFILING_APPLICATION_LAUNCH_SAMPLE_RATE] =
                configuration.applicationLaunchSampleRate
            context[FeatureContextKeys.PROFILING_ANR_ENABLED] = configuration.anrTriggerEnabled
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
            unregisterProfilerStatusListener(this@ProfilingFeature)
        }
        sdkCore.removeEventReceiver(name)
        sdkCore.removeContextUpdateReceiver(this)
        quotaChecker.reset()
        quotaChecker = NoOpQuotaChecker()
        quotaExecutor?.shutdownNow()
        quotaExecutor = null
        pendingTriggerProfiles.stop()
        pendingTriggerProfiles = NoOpPendingTriggerProfiles()
        synchronized(quotaSessionLock) {
            lastQuotaResult = null
            lastSeenRumSessionId = null
            launchSessionId = null
            isLaunchSessionTracked = false
            launchSessionQuotaResult = null
        }
        dropTriggerProfilesAwaitingQuota()
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
    }

    override fun onAnrDetected(event: ProfilingAnrDetectedEvent, result: PerfettoResult) {
        sdkCore.getFeature(Feature.RUM_FEATURE_NAME)?.sendEvent(event)
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
        if (sessionId == null ||
            sessionId == RumSessionConstants.EMPTY_RUM_SESSION_ID ||
            sessionId == lastSeenRumSessionId
        ) {
            return
        }
        val sampleRate = (context[FeatureContextKeys.RUM_SESSION_SAMPLE_RATE] as? Number)?.toFloat()
            ?: DEFAULT_RUM_SESSION_SAMPLE_RATE
        val isTracked =
            (context[FeatureContextKeys.RUM_SESSION_STATE] as? String) == RumSessionConstants.TRACKED_SESSION_STATE
        // Publish the new session and retire the previous session's quota check and queued trigger
        // profiles atomically: a trigger profile of the new session can't be queued, nor its quota
        // check started, until the previous session's state is gone.
        val (isLaunchSession, previousSessionTriggerProfiles) = synchronized(quotaSessionLock) {
            lastSeenRumSessionId = sessionId
            quotaChecker.reset()
            lastQuotaResult = null
            continuousProfilingScheduler?.lastQuotaResult = null
            if (launchSessionId == null && isLaunchProfilingActive) {
                launchSessionId = sessionId
                isLaunchSessionTracked = isTracked
            }
            (launchSessionId == sessionId) to drainTriggerProfilesAwaitingQuota()
        }
        continuousProfilingScheduler?.onRumSessionRenewed(
            sessionId = sessionId,
            rumSessionSampleRate = sampleRate
        )
        previousSessionTriggerProfiles.forEach {
            dropTriggerProfile(it, LOG_TRIGGER_PROFILING_DROPPED_SESSION_ENDED)
        }
        // Only spend a quota check on tracked sessions sampled for continuous profiling or owning the
        // app launch profile. Trigger profiles request their own check when they are matched.
        if (isTracked && (continuousProfilingScheduler?.currentSessionSampled == true || isLaunchSession)) {
            checkQuotaIfCurrentSession(sessionId)
        }
        // A held launch profile can be decided now: its session is untracked, or has just ended.
        tryWritePendingLaunchProfile()
    }

    override fun onProfilingStatusChange(isRunning: Boolean) {
        sdkCore.updateFeatureContext(Feature.PROFILING_FEATURE_NAME) { context ->
            context[FeatureContextKeys.PROFILER_IS_RUNNING] = isRunning
        }
    }

    @Suppress("ReturnCount")
    private fun tryWriteProfilingEvent() {
        val result = perfettoResult ?: return
        when (result.startReason) {
            ProfilingStartReason.APPLICATION_LAUNCH -> tryWriteLaunchProfile(result)

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

    private fun tryWritePendingLaunchProfile() {
        perfettoResult
            ?.takeIf { it.startReason == ProfilingStartReason.APPLICATION_LAUNCH }
            ?.let(::tryWriteLaunchProfile)
    }

    private fun tryWriteLaunchProfile(result: PerfettoResult) {
        // The launch profile is judged by its own RUM session only: it waits for that session and
        // its quota decision (re-triggered by the session renewal or the quota callback), and is
        // dropped if the session is untracked or ends before the decision lands. The TTID event is
        // required as well.
        val dropMessage = synchronized(quotaSessionLock) {
            val quotaResult = launchSessionQuotaResult
            when {
                launchSessionId == null -> return
                !isLaunchSessionTracked -> LOG_LAUNCH_PROFILING_DROPPED_SESSION_NOT_TRACKED
                quotaResult != null -> if (quotaResult.decision == QuotaResult.Decision.DENIED) {
                    LOG_LAUNCH_PROFILING_DROPPED_QUOTA_DENIED.format(Locale.US, quotaResult.reason.rawValue)
                } else {
                    null
                }
                launchSessionId != lastSeenRumSessionId -> LOG_LAUNCH_PROFILING_DROPPED_SESSION_ENDED
                else -> return
            }
        }
        if (isTtidVitalReceived.get() && !isTtidProfileSent.getAndSet(true)) {
            isLaunchProfilingActive = false
            if (dropMessage != null) {
                logToUser(dropMessage)
                fileDeleteSafe(result.resultFilePath, sdkCore.internalLogger)
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
                    is ProfilerEvent.RumAnrEvent -> onTriggerProfileMatched(
                        TriggerProfile(perfettoResult, profilerEvent)
                    )

                    else -> {
                        // Not a currently supported trigger-match type: nothing to write.
                    }
                }
            }
        )
    }

    internal fun propagateQuotaResult(sessionId: String, result: QuotaResult) {
        // The session check, storing the result and taking the trigger profiles waiting for it happen
        // atomically: a result for a past session is discarded, and a session renewal can't slip in
        // between and have its own profiles resolved with this result.
        val triggerProfiles = synchronized(quotaSessionLock) {
            if (sessionId != lastSeenRumSessionId) return
            lastQuotaResult = result
            continuousProfilingScheduler?.lastQuotaResult = result
            if (sessionId == launchSessionId) {
                launchSessionQuotaResult = result
            }
            drainTriggerProfilesAwaitingQuota()
        }
        sdkCore.updateFeatureContext(Feature.PROFILING_FEATURE_NAME) { context ->
            if (result.decision == QuotaResult.Decision.DENIED) {
                context[FeatureContextKeys.PROFILING_QUOTA_REASON] = result.reason.rawValue
                context[FeatureContextKeys.PROFILING_QUOTA_SESSION_ID] = sessionId
            } else {
                context.remove(FeatureContextKeys.PROFILING_QUOTA_REASON)
                context.remove(FeatureContextKeys.PROFILING_QUOTA_SESSION_ID)
            }
        }
        tryWritePendingLaunchProfile()
        triggerProfiles.forEach { writeOrDropTriggerProfile(it, result) }
    }

    private fun onTriggerProfileMatched(triggerProfile: TriggerProfile) {
        val sessionId = triggerProfile.event.rumContext.sessionId
        // Read under the lock so the session and its quota decision belong together and a session
        // renewal can't slip in before the profile is queued: the queue only ever holds profiles of
        // the current session.
        val (isCurrentSession, quotaResult) = synchronized(quotaSessionLock) {
            val isCurrent = sessionId == lastSeenRumSessionId
            val result = lastQuotaResult
            if (isCurrent && result == null) {
                triggerProfilesAwaitingQuota.add(triggerProfile)
            }
            isCurrent to result
        }
        when {
            // The current session's decision doesn't apply to a past session's profile, and
            // checking the past session would cancel the current session's check.
            !isCurrentSession -> dropTriggerProfile(triggerProfile, LOG_TRIGGER_PROFILING_DROPPED_SESSION_ENDED)

            quotaResult != null -> writeOrDropTriggerProfile(triggerProfile, quotaResult)

            else -> {
                // The profile is resolved when the decision lands: it was queued while the decision
                // was missing, under the same lock propagateQuotaResult takes it from the queue.
                checkQuotaIfCurrentSession(sessionId)
            }
        }
    }

    private fun checkQuotaIfCurrentSession(sessionId: String) {
        sdkCore.getFeature(Feature.PROFILING_FEATURE_NAME)?.withContext { datadogContext ->
            // withContext runs asynchronously: the session may have been renewed in the meantime,
            // and checking a past session would cancel the current session's check or leak its
            // result into the current session.
            synchronized(quotaSessionLock) {
                if (sessionId == lastSeenRumSessionId) {
                    quotaChecker.checkAsync(sessionId, datadogContext)
                }
            }
        }
    }

    private fun dropTriggerProfilesAwaitingQuota() {
        drainTriggerProfilesAwaitingQuota().forEach {
            dropTriggerProfile(it, LOG_TRIGGER_PROFILING_DROPPED_SESSION_ENDED)
        }
    }

    private fun drainTriggerProfilesAwaitingQuota(): List<TriggerProfile> {
        return synchronized(quotaSessionLock) {
            triggerProfilesAwaitingQuota.toList().also { triggerProfilesAwaitingQuota.clear() }
        }
    }

    private fun writeOrDropTriggerProfile(triggerProfile: TriggerProfile, quotaResult: QuotaResult) {
        if (quotaResult.decision == QuotaResult.Decision.DENIED) {
            dropTriggerProfile(
                triggerProfile,
                LOG_TRIGGER_PROFILING_DROPPED_QUOTA_DENIED.format(Locale.US, quotaResult.reason.rawValue)
            )
        } else {
            dataWriter.writeTriggerProfile(
                perfettoResult = triggerProfile.result,
                rumErrorId = triggerProfile.event.id,
                rumContext = triggerProfile.event.rumContext
            )
        }
    }

    private fun dropTriggerProfile(triggerProfile: TriggerProfile, message: String) {
        logToUser(message)
        fileDeleteSafe(triggerProfile.result.resultFilePath, sdkCore.internalLogger)
    }

    private fun isRecordingProfile(): Boolean {
        return isLaunchProfilingActive || continuousProfilingScheduler?.isActive == true
    }

    private data class TriggerProfile(
        val result: PerfettoResult,
        val event: ProfilerEvent.RumAnrEvent
    )

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
        internal const val LOG_LAUNCH_PROFILING_DROPPED_SESSION_NOT_TRACKED =
            "Launch profiling dropped: RUM session not tracked."
        internal const val LOG_LAUNCH_PROFILING_DROPPED_SESSION_ENDED =
            "Launch profiling dropped: RUM session ended before quota decision."
        internal const val LOG_TRIGGER_PROFILING_DROPPED_QUOTA_DENIED =
            "ANR trigger profile dropped: quota denied (reason=%s)."
        internal const val LOG_TRIGGER_PROFILING_DROPPED_SESSION_ENDED =
            "ANR trigger profile dropped: RUM session ended before quota decision."
    }
}
