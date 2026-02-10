/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

import com.datadog.android.Datadog
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.flags.internal.EvaluationsFeature
import com.datadog.android.flags.internal.FlagsFeature

/**
 * Entry point for the Flags feature.
 */
object Flags {

    /**
     * Enables the Flags feature.
     *
     * @param configuration configuration to use with feature flags and experiments. If not provided, the default
     * configuration will be used.
     * @param sdkCore SDK instance to register feature in. If not provided, a default SDK instance
     * will be used.
     */
    @JvmOverloads
    @JvmStatic
    fun enable(
        configuration: FlagsConfiguration = FlagsConfiguration.default,
        sdkCore: SdkCore = Datadog.getInstance()
    ) {
        if (configuration.trackEvaluations) {
            val evaluationsFeature = EvaluationsFeature(
                sdkCore = sdkCore as FeatureSdkCore,
                flagsConfiguration = configuration
            )
            sdkCore.registerFeature(evaluationsFeature)
        }

        val flagsFeature = FlagsFeature(
            sdkCore = sdkCore as FeatureSdkCore,
            flagsConfiguration = configuration
        )
        sdkCore.registerFeature(flagsFeature)
    }
}
