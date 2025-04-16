/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.compose

import com.datadog.android.compose.internal.ComposeActionTrackingStrategy
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.rum._RumInternalProxy

/**
 * Enable Jetpack Compose automatic actions tracking, such as
 * tap and scroll. Jetpack Compose actions tracking will be disabled if this API is not called,
 * which is the default behavior.
 *
 */
fun RumConfiguration.Builder.enableComposeActionTracking(): RumConfiguration.Builder {
    _RumInternalProxy.setComposeActionTrackingStrategy(
        builder = this,
        composeActionTrackingStrategy = ComposeActionTrackingStrategy()
    )
    return this
}
