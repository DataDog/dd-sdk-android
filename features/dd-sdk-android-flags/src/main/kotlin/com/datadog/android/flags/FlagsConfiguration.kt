/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

data class FlagsConfiguration(
    val enableExposureLogging: Boolean
) {
    class Builder {
        private var flagsConfig = DEFAULT_FEATURE_FLAGS_CONFIG

        fun setEnableExposureLogging(enabled: Boolean): Builder {
            flagsConfig = flagsConfig.copy(enableExposureLogging = enabled)
            return this
        }

        fun build(): FlagsConfiguration {
            return FlagsConfiguration(
                enableExposureLogging = flagsConfig.enableExposureLogging
            )
        }
    }

    internal companion object {
        internal val DEFAULT_FEATURE_FLAGS_CONFIG = FlagsConfiguration(
            enableExposureLogging = false
        )
    }
}
