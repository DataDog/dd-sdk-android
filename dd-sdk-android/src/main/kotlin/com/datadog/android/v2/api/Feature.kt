/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.api

import android.content.Context

/**
 * Interface to be implemented by the feature, which doesn't require any storage, to be
 * registered with [SdkCore].
 */
interface Feature {
    /**
     * Name of the feature.
     */
    val name: String

    /**
     * This method is called during feature initialization. At this stage feature should setup itself.
     *
     * @param sdkCore Instance of [SdkCore] this feature is registering with.
     * @param appContext Application context.
     */
    fun onInitialize(sdkCore: SdkCore, appContext: Context)

    /**
     * This method is called during feature de-initialization. At this stage feature should stop
     * itself and release resources held.
     */
    fun onStop()
}
