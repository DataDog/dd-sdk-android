/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.core.internal.utils.scheduleSafe
import com.datadog.android.core.sampling.RateBasedSampler
import com.datadog.android.internal.FeatureContextKeys
import java.util.Locale
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@Suppress("TooManyFunctions")
internal class ContinuousProfilingScheduler(
    private val appContext: Context,
    private val profiler: Profiler,
    private val sdkCore: FeatureSdkCore,
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

    private fun scheduleNextCycle() {
        val activeMs = jitter(CONTINUOUS_WINDOW_DURATION_MS)
        if (rumSessionSampled) {
            onActiveWindowStarted()
            isActive = true
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
        }

        // If the active window is skipped due to RUM session,
        // next cooldown window still needs to be scheduled.
        pendingFuture = schedulerExecutor.scheduleSafe(
            operationName = OPERATION_ACTIVE_WINDOW_END,
            delay = activeMs,
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

    companion object {
        // Base active window duration: 1 minute (±20% jitter applied per cycle).
        internal const val CONTINUOUS_WINDOW_DURATION_MS = 60_000L

        // Base cooldown duration: 1 minute (±20% jitter applied per cycle).
        internal const val CONTINUOUS_COOLDOWN_DURATION_MS = 60_000L

        // Fraction of base duration used as jitter range (±20%).
        private const val JITTER_FACTOR = 0.20

        private const val OPERATION_ACTIVE_WINDOW_END = "continuous_profiling_active_window"
        private const val OPERATION_COOLDOWN_END = "continuous_profiling_cooldown"

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
    }
}
