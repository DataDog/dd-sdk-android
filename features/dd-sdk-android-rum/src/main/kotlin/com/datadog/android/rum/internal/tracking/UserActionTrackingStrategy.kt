/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.internal.tracking

import com.datadog.android.rum.internal.instrumentation.gestures.GesturesTracker
import com.datadog.android.rum.tracking.TrackingStrategy
import com.datadog.tools.annotation.NoOpImplementation

/**
 * A TrackingStrategy dedicated to user actions tracking.
 */
@NoOpImplementation
internal interface UserActionTrackingStrategy : TrackingStrategy {
    fun getGesturesTracker(): GesturesTracker
}
