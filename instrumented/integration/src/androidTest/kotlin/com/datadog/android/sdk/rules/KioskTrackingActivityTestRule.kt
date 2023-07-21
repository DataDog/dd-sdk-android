/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.rules

import android.app.Activity
import android.app.ActivityManager
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.DdRumContentProvider

internal class KioskTrackingActivityTestRule<T : Activity>(
    activityClass: Class<T>,
    keepRequests: Boolean = false,
    trackingConsent: TrackingConsent
) : RumMockServerActivityTestRule<T>(activityClass, keepRequests, trackingConsent) {

    override fun beforeActivityLaunched() {
        super.beforeActivityLaunched()
        DdRumContentProvider::class.java.declaredMethods.firstOrNull() {
            it.name == "overrideProcessImportance"
        }?.apply {
            isAccessible = true
            invoke(null, ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND)
        }
    }
}
