/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import com.datadog.android.Datadog
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.v2.api.FeatureSdkCore
import com.datadog.android.v2.api.SdkCore

/**
 * An entry point to Datadog RUM feature.
 */
object Rum {

    /**
     * Enables a RUM feature based on the configuration provided.
     *
     * @param rumConfiguration Configuration to use for the feature.
     * @param sdkCore SDK instance to register feature in. If not provided, default SDK instance
     * will be used.
     */
    @JvmOverloads
    @JvmStatic
    fun enable(rumConfiguration: RumConfiguration, sdkCore: SdkCore = Datadog.getInstance()) {
        val rumFeature = RumFeature(
            sdkCore = sdkCore as FeatureSdkCore,
            applicationId = rumConfiguration.applicationId,
            configuration = rumConfiguration.featureConfiguration
        )

        sdkCore.registerFeature(rumFeature)
    }
}