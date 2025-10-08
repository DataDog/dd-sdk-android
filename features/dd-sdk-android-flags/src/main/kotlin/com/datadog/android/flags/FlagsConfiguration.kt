/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

/**
 * Describes configuration to be used for the Flags feature.
 */
data class FlagsConfiguration internal constructor(
    internal val trackExposures: Boolean
) {
    /**
     * A Builder class for a [FlagsConfiguration].
     */
    class Builder {
        private var trackExposures: Boolean = true

        /**
         * Sets whether exposures should be logged to the dedicated exposures intake endpoint. This is enabled by default.
         * @param enabled Whether to enable exposure logging.
         */
        fun setTrackExposures(enabled: Boolean): Builder {
            trackExposures = enabled
            return this
        }

        /**
         * Builds a [FlagsConfiguration] based on the current state of this Builder.
         * @return a new [FlagsConfiguration] instance.
         */
        fun build(): FlagsConfiguration = FlagsConfiguration(
            trackExposures = trackExposures
        )
    }
}
