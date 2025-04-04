/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.compose

import android.view.View
import com.datadog.android.rum.tracking.ActionTrackingStrategy
import com.datadog.android.rum.tracking.ViewTarget

/**
 * Implementation of [ActionTrackingStrategy] to track actions in Jetpack Compose.
 */
// TODO RUM-9298: Implement Compose action tracking strategy.
class ComposeActionTrackingStrategy : ActionTrackingStrategy {

    override fun findTargetForTap(decorView: View, x: Float, y: Float): ViewTarget? {
        return null
    }

    override fun findTargetForScroll(decorView: View, x: Float, y: Float): ViewTarget? {
        return null
    }
}
