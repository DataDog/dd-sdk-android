/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling

import android.os.ProfilingTrigger
import androidx.annotation.FloatRange

/**
 * Describes configuration to be used for the Profiling feature.
 */
@ExposedCopyVisibility
@ExperimentalProfilingApi
data class ProfilingConfiguration internal constructor(
    internal val customEndpointUrl: String?,
    internal val applicationLaunchSampleRate: Float,
    internal val continuousSampleRate: Float,
    internal val anrTriggerEnabled: Boolean = DEFAULT_ANR_TRIGGER_ENABLED
) {

    /**
     * A Builder class for a [ProfilingConfiguration].
     */
    class Builder {

        private var customEndpointUrl: String? = null
        private var applicationLaunchSampleRate: Float = DEFAULT_APPLICATION_LAUNCH_SAMPLE_RATE
        private var continuousSampleRate: Float = DEFAULT_CONTINUOUS_SAMPLE_RATE
        private var anrTriggerEnabled: Boolean = DEFAULT_ANR_TRIGGER_ENABLED

        /**
         * Sets the sampling rate for Application Launch profiling. It will be applied on the next application launch.
         *
         * @param sampleRate The sample rate, expressed as a percentage between 0 and 100 (inclusive).
         * A value of 0 disables Application Launch profiling entirely. A value of 100 enables Application Launch
         * profiling for all eligible requests, subject to rate limiting enforced by [android.os.ProfilingManager],
         * see [profiling limitations doc](https://developer.android.com/topic/performance/tracing/profiling-manager/will-my-profile-always-be-collected).
         * Default value is 15%. Effective rate for the ingested profiles is also a subject to RUM session sample rate.
         */
        fun setApplicationLaunchSampleRate(@FloatRange(from = 0.0, to = 100.0) sampleRate: Float): Builder {
            this.applicationLaunchSampleRate = sampleRate
            return this
        }

        /**
         * Sets the sampling rate for Continuous Profiling.
         *
         * @param sampleRate The sample rate, expressed as a percentage between 0 and 100 (inclusive).
         * A value of 0 disables Continuous Profiling entirely. A value of 100 enables
         * Continuous Profiling for all eligible RUM sessions, subject to rate limiting enforced by
         * [android.os.ProfilingManager].
         */
        fun setContinuousSampleRate(
            @FloatRange(from = 0.0, to = 100.0) sampleRate: Float
        ): Builder {
            this.continuousSampleRate = sampleRate
            return this
        }

        /**
         * Let the Profiling feature target a custom server.
         * The provided url should be the full endpoint url.
         */
        fun useCustomEndpoint(endpoint: String): Builder {
            customEndpointUrl = endpoint
            return this
        }

        /**
         * Enables or disables the ANR triggered profiling.
         *
         * When enabled, the SDK registers [ProfilingTrigger.TRIGGER_TYPE_ANR] so that a
         * profile is captured automatically when an ANR occurs.
         *
         * @param enabled `true` to enable ANR-triggered profiling (default), `false` to disable it.
         */
        fun setAnrTriggerEnabled(enabled: Boolean): Builder {
            this.anrTriggerEnabled = enabled
            return this
        }

        /**
         * Builds a [ProfilingConfiguration] based on the current state of this Builder.
         */
        fun build(): ProfilingConfiguration {
            return ProfilingConfiguration(
                customEndpointUrl = customEndpointUrl,
                applicationLaunchSampleRate = applicationLaunchSampleRate,
                continuousSampleRate = continuousSampleRate,
                anrTriggerEnabled = anrTriggerEnabled
            )
        }
    }

    companion object {

        private const val DEFAULT_APPLICATION_LAUNCH_SAMPLE_RATE = 15f

        /**
         * Default sampling rate for Continuous Profiling.
         */
        internal const val DEFAULT_CONTINUOUS_SAMPLE_RATE: Float = 15f

        /**
         * ANR-triggered profiling is enabled by default to preserve the existing behavior,
         * making this an opt-out capability.
         */
        internal const val DEFAULT_ANR_TRIGGER_ENABLED: Boolean = true

        /**
         * A default configuration for the Profiling feature.
         */
        val DEFAULT: ProfilingConfiguration = Builder().build()
    }
}
