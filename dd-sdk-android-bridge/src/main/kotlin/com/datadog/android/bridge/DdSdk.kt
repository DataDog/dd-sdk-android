/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.bridge

/**
 * The entry point to initialize Datadog's features.
 */
interface DdSdk {

    /**
     * Initializes Datadog's features.
     */
    fun initialize(configuration: DdSdkConfiguration): Unit
}
