/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.configuration

internal data class DataUploadConfiguration(internal val frequency: UploadFrequency) {
    internal val minDelayMs = MIN_DELAY_FACTOR * frequency.baseStepMs
    internal val maxDelayMs = MAX_DELAY_FACTOR * frequency.baseStepMs
    internal val defaultDelayMs = DEFAULT_DELAY_FACTOR * frequency.baseStepMs
    companion object {
        internal const val MIN_DELAY_FACTOR = 1
        internal const val MAX_DELAY_FACTOR = 10
        internal const val DEFAULT_DELAY_FACTOR = 5
    }
}
