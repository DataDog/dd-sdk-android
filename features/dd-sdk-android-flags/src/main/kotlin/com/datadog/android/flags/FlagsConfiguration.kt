/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

/**
 * Describes configuration to be used for the Flags feature.
 * @param enableExposureLogging Log exposure events to RUM.
 */
data class FlagsConfiguration(
    val enableExposureLogging: Boolean
) {
    /**
     * A Builder class for a [FlagsConfiguration].
     */
    class Builder {
        private var enableExposureLogging: Boolean = false

        /**
         * Sets whether exposures should be logged to RUM. This is disabled by default.
         * @param enabled Whether to enable exposure logging.
         */
        fun setEnableExposureLogging(enabled: Boolean): Builder {
            enableExposureLogging = enabled
            return this
        }

        /**
         * Builds a [FlagsConfiguration] based on the current state of this Builder.
         * @return a new [FlagsConfiguration] instance.
         */
        fun build(): FlagsConfiguration = FlagsConfiguration(
            enableExposureLogging = enableExposureLogging
        )
    }
}
