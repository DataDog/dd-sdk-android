/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.openfeature

/**
 * OpenFeature Provider implementation backed by Datadog Feature Flags.
 */
class DatadogFlagsProvider {

    /**
     * Simple hello world method so the module is not empty.
     *
     * @return A greeting message
     */
    fun helloWorld(): String = GREETING

    companion object {
        private const val GREETING = "Hello from DatadogFlagsProvider!"
    }
}
