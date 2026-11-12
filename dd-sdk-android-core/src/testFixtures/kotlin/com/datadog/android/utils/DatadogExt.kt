/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils

import com.datadog.android.Datadog
import com.datadog.android.api.SdkCore

/**
 * Adds [SdkCore] instance to [Datadog].
 */
fun Datadog.registerInstance(name: String?, sdkCore: SdkCore) {
    registry.register(name, sdkCore)
}

/**
 * Removes all [SdkCore] instances registered.
 */
fun Datadog.clearInstances() {
    registry.clear()
}
