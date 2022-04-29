/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.api

/**
 * Interface describing the configuration for an [SDKFeature] instance.
 */
interface SDKFeatureConfiguration {
    /**
     * Registers the feature linked with this configuration to the provided [SDKCore] instance.
     *
     * @param sdkCore the [SDKCore] instance to register against.
     */
    fun register(sdkCore: SDKCore)
}
