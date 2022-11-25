/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.api

/**
 * Interface describing the configuration for an [FeatureScope] instance.
 */
interface FeatureConfiguration {
    /**
     * Registers the feature linked with this configuration to the provided [SdkCore] instance.
     *
     * @param sdkCore the [SdkCore] instance to register against.
     */
    fun register(sdkCore: SdkCore)
}
