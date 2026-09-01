/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

// These types are public only so that :features:dd-sdk-android-rum and
// :features:dd-sdk-android-rum-prelaunch can share them across module boundaries. They are not
// part of the SDK's public API and carry no KDoc for that reason.
@file:Suppress(
    "PackageNameVisibility",
    "UndocumentedPublicClass",
    "UndocumentedPublicFunction",
    "UndocumentedPublicProperty"
)

package com.datadog.android.rum.internal.startup

interface RumAppStartupDetector {
    interface Listener {
        /**
         * Called when a startup scenario is detected (first qualifying Activity onCreate).
         */
        fun onAppStartupDetected(scenario: RumStartupScenario)

        /**
         * Called when the TTID duration has been measured (first frame drawn).
         */
        fun onTTIDComputed(scenario: RumStartupScenario, durationNs: Long, wasForwarded: Boolean = false)
    }

    fun destroy()
}
