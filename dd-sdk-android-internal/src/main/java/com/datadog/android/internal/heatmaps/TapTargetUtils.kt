/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.heatmaps

import android.view.View

/**
 * Single canonical tap-target predicate shared by the RUM gesture layer and Session Replay,
 * so both subsystems stay in sync automatically.
 *
 * Note: [View.isEnabled] is intentionally not checked. Disabled views retain [View.isClickable]
 * = true on Android but never receive touch events, so the RUM gesture layer never fires for
 * them. Excluding `isEnabled` here keeps the predicate identical to RUM's existing behaviour.
 */
fun View.isValidTapTarget(): Boolean {
    return isClickable && visibility == View.VISIBLE
}
