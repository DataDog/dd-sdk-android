/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace

import com.datadog.android.Datadog
import com.datadog.android.trace.internal.TracingFeature
import com.datadog.android.v2.api.FeatureSdkCore
import com.datadog.android.v2.api.SdkCore

/**
 * An entry point to Datadog Traces feature.
 */
object Traces {

    /**
     * Enables a Traces feature based on the configuration provided.
     *
     * @param tracesConfiguration Configuration to use for the feature.
     * @param sdkCore SDK instance to register feature in. If not provided, default SDK instance
     * will be used.
     */
    @JvmOverloads
    @JvmStatic
    fun enable(tracesConfiguration: TracesConfiguration, sdkCore: SdkCore = Datadog.getInstance()) {
        val tracingFeature = TracingFeature(
            sdkCore = sdkCore as FeatureSdkCore,
            customEndpointUrl = tracesConfiguration.customEndpointUrl,
            spanEventMapper = tracesConfiguration.eventMapper
        )

        sdkCore.registerFeature(tracingFeature)
    }
}
