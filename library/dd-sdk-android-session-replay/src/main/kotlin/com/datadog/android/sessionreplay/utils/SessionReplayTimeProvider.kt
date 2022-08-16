/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.utils

internal class SessionReplayTimeProvider : TimeProvider {

    // TODO: RUMM-2400 Apply the Kronos offset here once will be provided through SDKContext
    override fun getDeviceTimestamp(): Long {
        return System.currentTimeMillis()
    }
}
