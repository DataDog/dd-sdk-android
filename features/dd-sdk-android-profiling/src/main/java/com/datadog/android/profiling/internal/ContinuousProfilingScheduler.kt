/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.core.internal.utils.scheduleSafe
import com.datadog.android.core.internal.utils.submitSafe
import com.datadog.android.core.sampling.RateBasedSampler
import com.datadog.android.internal.FeatureContextKeys
import com.datadog.android.internal.time.TimeProvider
import java.util.Locale
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.random.Random

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@Suppress("TooManyFunctions")
internal class ContinuousProfilingScheduler(
    private val appContext: Context,
    private val profiler: Profiler,
    private val sdkCore: FeatureSdkCore,
    private val timeProvider: TimeProvider,
    private val sampleRate: Float,
    private val onActiveWindowStarted: () -> Unit = {}
) {

    @Volatile
    internal var isScheduling = false

    // TODO RUM-15315: Read RUM session tracked state instead of using default value.
    @Volatile
    internal var rumSessionSampled = false

    @Volatile
    private var pendingFuture: ScheduledFuture<*>? = null

    @Volatile
    internal var isActive: Boolean = false

    @Volatile
    internal var state: State = State.IDLE

    @Volatile
    private var cooldownWindowEndMs: Long = 0L

    @Volatile
    private var activeWindowEndMs: Long = 0L

    @Volatile
    private var remainingCooldownMs: Long = 0L

    private val schedulerExecutor: ScheduledExecutorService = profiler.scheduledExecutorService

    fun start(launchProfilingActive: Boolean) {
        isScheduling = RateBasedSampler<Unit>(sampleRate).sample(Unit)
        profiler.setExtendLaunchSession(isScheduling)
        if (!isScheduling) {
            logToUser { LOG_DISABLED.format(Locale.US, sampleRate) }
            return
        }
        if (launchProfilingActive) {
            logToUser { LOG_WAITING_FOR_LAUNCH }
            return
        }
        // If app launch profiling is not started, then schedule a cooldown window to replace it,
        // c1 ∈ [0, 1min], otherwise wait for `onAppLaunchProfilingComplete` to be called.
        @Suppress("UnsafeThirdPartyFunctionCall") // range is always valid (0 .. max+1)
        val initialCooldownMs = Random.nextLong(0L, CONTINUOUS_COOLDOWN_DURATION_MS + 1L)
        logToUser { LOG_INITIAL_COOLDOWN.format(Locale.US, initialCooldownMs) }
        scheduleCooldown(initialCooldownMs)
    }

    fun stop() {
        isScheduling = false
        logToUser { LOG_STOPPED }
        pendingFuture?.cancel(false)
        state = State.IDLE
        isActive = false
    }

    fun onActiveWindowEnded() {
        isActive = false
    }

    fun onAppLaunchProfilingComplete() {
        if (!isScheduling) return
        logToUser { LOG_LAUNCH_COMPLETE }
        scheduleCooldown(jitter(CONTINUOUS_COOLDOWN_DURATION_MS))
    }

    fun onRumSessionRenewed(sessionSampled: Boolean) {
        rumSessionSampled = sessionSampled
    }

    /**
     * Called by [ProfilingLifecycleCallback] when the app moves to the background.
     *
     * During an active profiling window, a 10-second grace period is started so brief
     * interruptions (notification shade, quick-settings) do not truncate the session.
     * During a cooldown, the timer is paused and resumed on foreground with the remaining time.
     */
    @SuppressLint("CheckResult")
    fun onBackground() {
        schedulerExecutor.submitSafe(OPERATION_ON_BACKGROUND, sdkCore.internalLogger) {
            if (!isScheduling) return@submitSafe
            when (state) {
                State.ACTIVE -> {
                    pendingFuture?.cancel(false)
                    pendingFuture = schedulerExecutor.scheduleSafe(
                        operationName = OPERATION_GRACE_PERIOD,
                        delay = BACKGROUND_GRACE_PERIOD_MS,
                        unit = TimeUnit.MILLISECONDS,
                        internalLogger = sdkCore.internalLogger,
                        runnable = ::onGracePeriodExpired
                    )
                    state = State.GRACE_PERIOD
                    val remaining = max(0L, activeWindowEndMs - timeProvider.getDeviceTimestampMillis())
                    logToUser { LOG_BACKGROUND_GRACE_PERIOD.format(Locale.US, remaining) }
                }

                State.COOLDOWN -> {
                    remainingCooldownMs =
                        max(0L, cooldownWindowEndMs - timeProvider.getDeviceTimestampMillis())
                    pendingFuture?.cancel(false)
                    state = State.PAUSED_COOLDOWN
                    logToUser {
                        LOG_BACKGROUND_COOLDOWN_PAUSED.format(
                            Locale.US,
                            remainingCooldownMs
                        )
                    }
                }

                else -> {
                    /* IDLE / GRACE_PERIOD / PAUSED_* — ignore */
                }
            }
        }
    }

    /**
     * Called by [ProfilingLifecycleCallback] when the app returns to the foreground.
     *
     * - [State.GRACE_PERIOD]: cancel the grace timer; resume the active window for its remaining time.
     * - [State.PAUSED_COOLDOWN]: resume the cooldown with the remaining time.
     * - [State.PAUSED_AFTER_GRACE]: start a fresh jittered cooldown.
     */
    @Suppress("CheckResult")
    fun onForeground() {
        schedulerExecutor.submitSafe(OPERATION_ON_FOREGROUND, sdkCore.internalLogger) {
            if (!isScheduling) return@submitSafe
            when (state) {
                State.GRACE_PERIOD -> {
                    pendingFuture?.cancel(false)
                    val remaining = max(0L, activeWindowEndMs - timeProvider.getDeviceTimestampMillis())
                    scheduleActiveWindowEnd(remaining)
                    state = State.ACTIVE
                    logToUser { LOG_FOREGROUND_RESUMED_ACTIVE }
                }

                State.PAUSED_COOLDOWN -> {
                    scheduleCooldown(remainingCooldownMs)
                    logToUser {
                        LOG_FOREGROUND_RESUMED_COOLDOWN.format(
                            Locale.US,
                            remainingCooldownMs
                        )
                    }
                }

                State.PAUSED_AFTER_GRACE -> {
                    scheduleCooldown(jitter(CONTINUOUS_COOLDOWN_DURATION_MS))
                    logToUser { LOG_FOREGROUND_AFTER_GRACE }
                }

                else -> {
                    /* IDLE / ACTIVE / COOLDOWN — ignore (rapid flicker) */
                }
            }
        }
    }

    private fun onGracePeriodExpired() {
        state = State.PAUSED_AFTER_GRACE
        isActive = false
        profiler.stop(sdkCore.name)
        logToUser { LOG_GRACE_PERIOD_EXPIRED }
    }

    private fun scheduleNextCycle() {
        val activeMs = jitter(CONTINUOUS_WINDOW_DURATION_MS)
        if (rumSessionSampled) {
            onActiveWindowStarted()
            isActive = true
            activeWindowEndMs = timeProvider.getDeviceTimestampMillis() + activeMs
            state = State.ACTIVE
            logToUser { LOG_ACTIVE_WINDOW_STARTED.format(Locale.US, activeMs) }
            profiler.start(
                appContext = appContext,
                startReason = ProfilingStartReason.CONTINUOUS,
                additionalAttributes = emptyMap(),
                sdkInstanceNames = setOf(sdkCore.name),
                durationMs = activeMs.toInt()
            )
            sdkCore.updateFeatureContext(Feature.PROFILING_FEATURE_NAME) { context ->
                context[FeatureContextKeys.PROFILER_IS_RUNNING] = profiler.isRunning(sdkCore.name)
            }
        } else {
            logToUser { LOG_ACTIVE_WINDOW_SKIPPED }
            // Skipped windows behave like cooldown for lifecycle purposes.
            state = State.COOLDOWN
        }

        scheduleActiveWindowEnd(activeMs)
    }

    private fun scheduleActiveWindowEnd(delayMs: Long) {
        cooldownWindowEndMs = timeProvider.getDeviceTimestampMillis() + delayMs
        pendingFuture = schedulerExecutor.scheduleSafe(
            operationName = OPERATION_ACTIVE_WINDOW_END,
            delay = delayMs,
            unit = TimeUnit.MILLISECONDS,
            internalLogger = sdkCore.internalLogger,
            runnable = {
                val cooldownMs = jitter(CONTINUOUS_COOLDOWN_DURATION_MS)
                logToUser { LOG_ACTIVE_WINDOW_ENDED.format(Locale.US, cooldownMs) }
                scheduleCooldown(cooldownMs)
            }
        )
    }

    private fun scheduleCooldown(cooldownMs: Long) {
        state = State.COOLDOWN
        cooldownWindowEndMs = timeProvider.getDeviceTimestampMillis() + cooldownMs
        pendingFuture = schedulerExecutor.scheduleSafe(
            operationName = OPERATION_COOLDOWN_END,
            delay = cooldownMs,
            unit = TimeUnit.MILLISECONDS,
            internalLogger = sdkCore.internalLogger,
            runnable = ::scheduleNextCycle
        )
    }

    private fun jitter(base: Long): Long {
        val delta = (base * JITTER_FACTOR).toLong()
        @Suppress("UnsafeThirdPartyFunctionCall") // delta is always < base, so range is valid
        return base + Random.nextLong(-delta, delta + 1L)
    }

    private fun logToUser(message: () -> String) {
        sdkCore.internalLogger.log(
            InternalLogger.Level.DEBUG,
            InternalLogger.Target.USER,
            message
        )
    }

    internal enum class State {
        /**
         * Continuous profiler has not started, or it's sampled out. No timer is running.
         */
        IDLE,

        /**
         * Between active windows. The cooldown timer is running in the foreground and will fire
         * [scheduleNextCycle] when it expires. Backgrounding here transitions to [PAUSED_COOLDOWN].
         */
        COOLDOWN,

        /**
         * Profiler is currently running its active window. Backgrounding here transitions to
         * [GRACE_PERIOD]. The active-window-end timer fires when the window completes and
         * schedules the next cooldown.
         */
        ACTIVE,

        /**
         * Was [ACTIVE]; app moved to background. The grace timer is running. Foregrounding
         * within the grace period returns to [ACTIVE] for the active window's remaining time.
         * If the grace timer expires first, transitions to [PAUSED_AFTER_GRACE] and the
         * profiler is stopped.
         */
        GRACE_PERIOD,

        /**
         * Was [COOLDOWN]; app is in background. The cooldown timer was cancelled and the
         * remaining time is saved in [remainingCooldownMs]. Foregrounding resumes [COOLDOWN]
         * with that remainder.
         */
        PAUSED_COOLDOWN,

        /**
         * Grace period expired while still in background. The profiler has been stopped and
         * no timer is running. Foregrounding starts a fresh jittered [COOLDOWN].
         */
        PAUSED_AFTER_GRACE
    }

    companion object {
        // Base active window duration: 1 minute (±20% jitter applied per cycle).
        internal const val CONTINUOUS_WINDOW_DURATION_MS = 60_000L

        // Base cooldown duration: 1 minute (±20% jitter applied per cycle).
        internal const val CONTINUOUS_COOLDOWN_DURATION_MS = 60_000L

        // Grace period before stopping an active session when app moves to background.
        internal const val BACKGROUND_GRACE_PERIOD_MS = 10_000L

        // Fraction of base duration used as jitter range (±20%).
        private const val JITTER_FACTOR = 0.20

        private const val OPERATION_ACTIVE_WINDOW_END = "continuous_profiling_active_window"
        private const val OPERATION_COOLDOWN_END = "continuous_profiling_cooldown"
        private const val OPERATION_GRACE_PERIOD = "continuous_profiling_grace_period"
        private const val OPERATION_ON_BACKGROUND = "continuous_profiling_on_background"
        private const val OPERATION_ON_FOREGROUND = "continuous_profiling_on_foreground"

        internal const val LOG_DISABLED =
            "Continuous profiling disabled (not sampled in at rate=%s)."
        internal const val LOG_WAITING_FOR_LAUNCH =
            "Continuous profiling enabled; waiting for app launch profiling to complete."
        internal const val LOG_INITIAL_COOLDOWN =
            "Continuous profiling enabled; initial cooldown %sms before first active window."
        internal const val LOG_STOPPED =
            "Continuous profiling stopped."
        internal const val LOG_LAUNCH_COMPLETE =
            "App launch profiling complete; starting first continuous cycle."
        internal const val LOG_ACTIVE_WINDOW_STARTED =
            "Continuous profiling active window started (%sms)."
        internal const val LOG_ACTIVE_WINDOW_SKIPPED =
            "Continuous profiling active window skipped (RUM session not sampled); cooldown still scheduled."
        internal const val LOG_ACTIVE_WINDOW_ENDED =
            "Continuous profiling active window ended; cooling down for %sms."
        internal const val LOG_BACKGROUND_GRACE_PERIOD =
            "App backgrounded during active window; grace period started (%sms remaining in active window)."
        internal const val LOG_BACKGROUND_COOLDOWN_PAUSED =
            "App backgrounded during cooldown; cooldown paused (%sms remaining)."
        internal const val LOG_FOREGROUND_RESUMED_ACTIVE =
            "App foregrounded; active window resumed."
        internal const val LOG_FOREGROUND_RESUMED_COOLDOWN =
            "App foregrounded; cooldown resumed (%sms remaining)."
        internal const val LOG_FOREGROUND_AFTER_GRACE =
            "App foregrounded after grace period expired; starting cooldown."
        internal const val LOG_GRACE_PERIOD_EXPIRED =
            "Grace period expired; profiler stopped."
    }
}
