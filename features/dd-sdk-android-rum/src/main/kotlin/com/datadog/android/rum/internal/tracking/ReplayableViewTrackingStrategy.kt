/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.tracking

import android.app.Activity

/**
 * Opt-in interface for [com.datadog.android.rum.tracking.ViewTrackingStrategy] implementations
 * that can handle the late-init scenario: the RUM SDK initialised after the first Activity had
 * already started (e.g. a cross-platform bridge delay), so the normal lifecycle callbacks were
 * never delivered for that Activity.
 *
 * Implementing this interface lets [com.datadog.android.rum.internal.RumFeature] replay the
 * relevant lifecycle event without coupling to concrete strategy types.
 */
internal interface ReplayableViewTrackingStrategy {
    /**
     * Called when the SDK initialises after [activity] has already started/resumed and no RUM
     * view has been opened for it yet. Implementations should start a view as if the normal
     * lifecycle callback had fired.
     */
    fun onLateActivityReady(activity: Activity)
}
