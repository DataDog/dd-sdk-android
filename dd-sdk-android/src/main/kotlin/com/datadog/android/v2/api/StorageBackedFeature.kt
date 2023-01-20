/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.api

/**
 * Interface to be implemented by the feature, which requires storage, to be
 * registered with [SdkCore].
 */
interface StorageBackedFeature : Feature {

    /**
     * Provides an instance of [RequestFactory] for the given feature. Will be
     * called before [Feature.onInitialize].
     */
    val requestFactory: RequestFactory

    /**
     * Provides storage configuration for the given feature. Will be
     * called before [Feature.onInitialize].
     */
    val storageConfiguration: FeatureStorageConfiguration
}
