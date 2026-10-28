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
import com.datadog.android.internal.profiling.ProfilingRumContext
import com.datadog.android.internal.rum.RumSessionConstants
import com.datadog.android.internal.time.DefaultTimeProvider
import com.datadog.android.profiling.ExperimentalProfilingApi
import com.datadog.android.profiling.ProfilingConfiguration
import com.datadog.android.profiling.internal.perfetto.PerfettoResult
import com.datadog.android.profiling.internal.perfetto.ProfileType
import com.datadog.android.profiling.internal.quota.NoOpQuotaChecker
import com.datadog.android.profiling.internal.quota.ProfilingQuotaChecker
import com.datadog.android.profiling.internal.quota.QuotaChecker
import com.datadog.android.profiling.internal.quota.QuotaResult
import com.datadog.android.profiling.internal.trigger.NoOpPendingTriggerProfiles
import com.datadog.android.profiling.internal.trigger.OomProfileOutcomeMarker
import com.datadog.android.profiling.internal.trigger.PendingOomProfile
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
@Suppress("TooManyFunctions", "LargeClass")
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

    // Held error whose OOM profile batch write is running in this process.
    @Volatile
    private var oomProfileWriteInProgressErrorId: String? = null

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
    private var triggersEnabled: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            profiler.setTriggersEnabled(appContext, value)
            val isOomTriggerRegistered = profiler.isOomTriggerRegistered
            sdkCore.updateFeatureContext(Feature.PROFILING_FEATURE_NAME) { context ->
                context[FeatureContextKeys.PROFILING_OOM_TRIGGER_ACTIVE] = isOomTriggerRegistered
            }
        }

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
            setEnabledTriggers(
                configuration.anrTriggerEnabled,
                configuration.oomTriggerEnabled,
                configuration.anomalyTriggerEnabled
            )
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

        startOomProfileHandshake()
    }

    override fun onStop() {
        processLifecycleMonitor?.let { monitor ->
            (appContext as? Application)?.unregisterActivityLifecycleCallbacks(monitor)
        }
        processLifecycleMonitor = null
        continuousProfilingScheduler?.stop()
        profiler.apply {
            stop()
            triggersEnabled = false
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

            is ProfilerEvent.RumOomErrorEvent -> {
                // No trigger result can arrive to match this event while the OOM trigger is
                // inactive: persisting it would leave it stranded until it expires.
                if (profiler.isOomTriggerRegistered) {
                    pendingTriggerProfiles.setRumGatingEvent(event)
                }
            }

            is ProfilerEvent.RumAnomalyErrorEvent -> {
                pendingTriggerProfiles.setRumGatingEvent(event)
            }

            is ProfilerEvent.RumHeldOomErrorEvent -> onOomExecutor { onHeldOomError(event.id) }

            is ProfilerEvent.RumOomProfileAttachReply -> onOomExecutor {
                onOomProfileAttachReply(event.rumErrorId, event.granted)
            }

            is ProfilerEvent.RumOomProfileResolutionAck -> onOomExecutor { onOomProfileResolutionAck(event.rumErrorId) }

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
            pendingRumEvents.clear()
        }
    }

    override fun onAnrDetected(event: ProfilingAnrDetectedEvent, result: PerfettoResult) {
        sdkCore.getFeature(Feature.RUM_FEATURE_NAME)?.sendEvent(event)
        pendingTriggerProfiles.setProfilingResult(result)
    }

    override fun onOutOfMemoryDetected(result: PerfettoResult) {
        // RUM already generates its own OOM error event, so the profiling feature
        // does not forward a separate OOM event.
        pendingTriggerProfiles.setProfilingResult(result)
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
                triggersEnabled = false
            }

            sessionId == lastSeenRumSessionId -> {
                triggersEnabled = isRumSessionTracked(context)
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
        val sampleRate = (context[FeatureContextKeys.RUM_SESSION_SAMPLE_RATE] as? Number)?.toFloat()
            ?: DEFAULT_RUM_SESSION_SAMPLE_RATE
        val isTracked = isRumSessionTracked(context)
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
        triggersEnabled = isTracked
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
            appContext = appContext,
            executor = executor,
            timeProvider = sdkCore.timeProvider,
            internalLogger = sdkCore.internalLogger,
            callback = object : PendingTriggerProfileStorage.Callback {
                override fun onMatch(result: PerfettoResult, event: ProfilerEvent) {
                    when (event) {
                        is ProfilerEvent.RumAnrEvent -> onTriggerProfileMatched(
                            TriggerProfile(result, event.id, event.rumContext)
                        )

                        is ProfilerEvent.RumAnomalyErrorEvent -> onTriggerProfileMatched(
                            TriggerProfile(result, event.id, event.rumContext)
                        )

                        else -> {
                            // OOM matches are persisted by PendingTriggerProfileStorage and uploaded on the next launch.
                        }
                    }
                }

                override fun onDeferredOomProfileReady() {
                    // Matched right away instead of waiting for yet another launch to upload it.
                    dispatchPendingOomProfileUpload()
                }

                override fun isSessionQuotaDenied(sessionId: String): Boolean {
                    return this@ProfilingFeature.isSessionQuotaDenied(sessionId)
                }
            }
        )
    }

    private fun isSessionQuotaDenied(sessionId: String): Boolean {
        return synchronized(quotaSessionLock) {
            sessionId == lastSeenRumSessionId && lastQuotaResult?.decision == QuotaResult.Decision.DENIED
        }
    }

    private fun startOomProfileHandshake() {
        onOomExecutor {
            // A held RUM error announced before this feature was registered is announced again.
            sdkCore.getFeature(Feature.RUM_FEATURE_NAME)?.sendEvent(ProfilerEvent.OomProfilingReady)
            uploadPendingOomProfile()
        }
    }

    private fun dispatchPendingOomProfileUpload() {
        onOomExecutor { uploadPendingOomProfile() }
    }

    private fun onOomExecutor(block: () -> Unit) {
        profiler.scheduledExecutorService.executeSafe(
            OPERATION_OOM_PROFILE_HANDSHAKE,
            sdkCore.internalLogger,
            block
        )
    }

    /**
     * Asks RUM whether the OOM profile left behind by a previous process may be attached to its held
     * RUM error. The trace file is still where the platform profiler wrote it; only the few values
     * needed to describe it were persisted, because the process that captured it had no memory to
     * spare. It is written only once RUM has granted it, so that the error's profiling status matches.
     */
    @Suppress("ReturnCount")
    private fun uploadPendingOomProfile() {
        val pending = ProfilingStorage.getPendingOomProfile(appContext) ?: return
        if (ProfilingStorage.getOomProfileOutcome(appContext)?.rumErrorId == pending.rumErrorId) {
            // Already handled: the marker owns the outcome, the profile must never be written again.
            ProfilingStorage.removePendingOomProfile(appContext)
            return
        }
        val ageMs = sdkCore.timeProvider.getDeviceTimestampMillis() - pending.startMs
        if (ageMs >= PendingTriggerProfileStorage.PENDING_OOM_PROFILE_MAX_AGE_MS) {
            ProfilingStorage.removePendingOomProfile(appContext)
            logToUser(LOG_PENDING_OOM_PROFILE_EXPIRED)
            fileDeleteSafe(pending.resultFilePath, sdkCore.internalLogger)
            return
        }
        // Without RUM, the profile stays persisted: RUM's held error announcement retries it.
        sdkCore.getFeature(Feature.RUM_FEATURE_NAME)?.sendEvent(
            ProfilerEvent.OomProfileAttachRequest(pending.rumErrorId)
        )
    }

    private fun onOomProfileAttachReply(rumErrorId: String, granted: Boolean) {
        val pending = ProfilingStorage.getPendingOomProfile(appContext)?.takeIf { it.rumErrorId == rumErrorId }
        if (!granted) {
            if (pending != null) {
                ProfilingStorage.removePendingOomProfile(appContext)
                fileDeleteSafe(pending.resultFilePath, sdkCore.internalLogger)
                logToUser(LOG_PENDING_OOM_PROFILE_NOT_ATTACHABLE)
            }
            return
        }
        val marker = ProfilingStorage.getOomProfileOutcome(appContext)?.takeIf { it.rumErrorId == rumErrorId }
        when {
            // Repeated grant: never write twice.
            marker != null -> resolveFromMarker(marker)
            pending == null -> resolveOomProfile(rumErrorId, OomProfileOutcomeMarker.State.NOT_ATTACHED)
            else -> writeGrantedOomProfile(pending)
        }
    }

    private fun writeGrantedOomProfile(pending: PendingOomProfile) {
        val inFlight = OomProfileOutcomeMarker(
            rumErrorId = pending.rumErrorId,
            state = OomProfileOutcomeMarker.State.IN_FLIGHT,
            resultFilePath = pending.resultFilePath
        )
        // Recorded before the write, together with removing the pending profile: a death past this
        // point resolves from the marker, never by writing the profile again.
        if (!ProfilingStorage.startOomProfileWrite(appContext, inFlight)) {
            // Nothing written: RUM keeps the error reserved, and its next announcement retries.
            logToUser(LOG_OOM_PROFILE_WRITE_NOT_STARTED)
            return
        }
        oomProfileWriteInProgressErrorId = pending.rumErrorId
        dataWriter.writeTriggerProfile(
            perfettoResult = PerfettoResult(
                start = pending.startMs,
                startReason = ProfilingStartReason.OUT_OF_MEMORY,
                end = pending.endMs,
                resultFilePath = pending.resultFilePath,
                profileTypes = listOf(ProfileType.HEAP_HISTOGRAM),
                bootNtpOffsetNs = pending.bootNtpOffsetNs
            ),
            rumErrorId = pending.rumErrorId,
            rumContext = pending.rumContext
        ) { isWritten ->
            // Synchronously on the writer thread, right after the batch write returned.
            val state = if (isWritten) {
                OomProfileOutcomeMarker.State.ATTACHED
            } else {
                OomProfileOutcomeMarker.State.NOT_ATTACHED
            }
            val marker = inFlight.copy(state = state, resultFilePath = null)
            if (!ProfilingStorage.setOomProfileOutcome(appContext, marker)) {
                // Still reported from memory: RUM persists the outcome before acknowledging it.
                logToUser(LOG_OOM_PROFILE_OUTCOME_NOT_PERSISTED)
            }
            oomProfileWriteInProgressErrorId = null
            onOomExecutor { sendOomProfileResolution(marker) }
        }
    }

    @Suppress("ReturnCount")
    private fun onHeldOomError(rumErrorId: String) {
        ProfilingStorage.getOomProfileOutcome(appContext)?.let { marker ->
            if (marker.rumErrorId == rumErrorId) {
                resolveFromMarker(marker)
                return
            }
        }
        val pending = ProfilingStorage.getPendingOomProfile(appContext)
        if (pending != null) {
            // Another error's profile is asked for anyway: RUM turns it down, and it is dropped.
            uploadPendingOomProfile()
            if (pending.rumErrorId == rumErrorId) return
        }
        val gatingEvent = ProfilingStorage.getPendingOomGatingEvent(appContext)
        if (gatingEvent?.rumErrorId == rumErrorId) {
            val ageMs = sdkCore.timeProvider.getServerTimestampMillis() - gatingEvent.timestampMs
            if (configuration.oomTriggerEnabled &&
                ageMs < PendingTriggerProfileStorage.PENDING_OOM_PROFILE_MAX_AGE_MS
            ) {
                // The OS may still deliver the trigger result: its match leads to an attach request.
                return
            }
            ProfilingStorage.removePendingOomGatingEvent(appContext)
        }
        resolveOomProfile(rumErrorId, OomProfileOutcomeMarker.State.NOT_ATTACHED)
    }

    private fun resolveFromMarker(marker: OomProfileOutcomeMarker) {
        when (marker.state) {
            OomProfileOutcomeMarker.State.IN_FLIGHT -> {
                // The write running in this process reports its outcome when it returns.
                if (oomProfileWriteInProgressErrorId == marker.rumErrorId) return
                // Abandoned by a dead process before the batch write returned.
                marker.resultFilePath?.let { fileDeleteSafe(it, sdkCore.internalLogger) }
                resolveOomProfile(marker.rumErrorId, OomProfileOutcomeMarker.State.NOT_ATTACHED)
            }

            OomProfileOutcomeMarker.State.ATTACHED,
            OomProfileOutcomeMarker.State.NOT_ATTACHED -> sendOomProfileResolution(marker)
        }
    }

    /**
     * Only for outcomes where no profile was or will be written: a later profile for this error is
     * turned down by RUM, so the outcome holds even if the marker can't be persisted.
     */
    private fun resolveOomProfile(rumErrorId: String, state: OomProfileOutcomeMarker.State) {
        val marker = OomProfileOutcomeMarker(rumErrorId = rumErrorId, state = state, resultFilePath = null)
        if (!ProfilingStorage.setOomProfileOutcome(appContext, marker)) {
            logToUser(LOG_OOM_PROFILE_OUTCOME_NOT_PERSISTED)
        }
        sendOomProfileResolution(marker)
    }

    private fun sendOomProfileResolution(marker: OomProfileOutcomeMarker) {
        val outcome = if (marker.state == OomProfileOutcomeMarker.State.ATTACHED) {
            ProfilerEvent.OomProfileResolution.Outcome.ATTACHED
        } else {
            ProfilerEvent.OomProfileResolution.Outcome.NOT_ATTACHED
        }
        sdkCore.getFeature(Feature.RUM_FEATURE_NAME)?.sendEvent(
            ProfilerEvent.OomProfileResolution(rumErrorId = marker.rumErrorId, outcome = outcome)
        )
    }

    private fun onOomProfileResolutionAck(rumErrorId: String) {
        if (ProfilingStorage.getOomProfileOutcome(appContext)?.rumErrorId != rumErrorId) return
        ProfilingStorage.removeOomProfileOutcome(appContext)
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
        val sessionId = triggerProfile.rumContext.sessionId
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

    private fun writeOrDropTriggerProfile(triggerProfile: TriggerProfile, quotaResult: QuotaResult?) {
        if (quotaResult?.decision == QuotaResult.Decision.DENIED) {
            dropTriggerProfile(
                triggerProfile,
                LOG_TRIGGER_PROFILING_DROPPED_QUOTA_DENIED.format(Locale.US, quotaResult.reason.rawValue)
            )
        } else {
            dataWriter.writeTriggerProfile(
                perfettoResult = triggerProfile.result,
                rumErrorId = triggerProfile.rumErrorId,
                rumContext = triggerProfile.rumContext,
                onWritten = {}
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
        val rumErrorId: String,
        val rumContext: ProfilingRumContext
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
            "Trigger profile dropped: quota denied (reason=%s)."
        internal const val LOG_TRIGGER_PROFILING_DROPPED_SESSION_ENDED =
            "Trigger profile dropped: RUM session ended before quota decision."
        internal const val LOG_PENDING_OOM_PROFILE_EXPIRED =
            "Out of memory profile from a previous run dropped: too old to upload."
        internal const val LOG_PENDING_OOM_PROFILE_NOT_ATTACHABLE =
            "Out of memory profile from a previous run dropped: its RUM error was already sent."
        internal const val LOG_OOM_PROFILE_WRITE_NOT_STARTED =
            "Out of memory profile not written yet: its outcome marker could not be persisted."
        internal const val LOG_OOM_PROFILE_OUTCOME_NOT_PERSISTED =
            "Out of memory profile outcome could not be persisted."
        private const val OPERATION_OOM_PROFILE_HANDSHAKE = "oom_profile_handshake"
    }
}
