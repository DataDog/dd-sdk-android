/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal

import android.app.ApplicationExitInfo
import android.os.Build
import androidx.annotation.RequiresApi
import com.datadog.android.api.storage.DataWriter
import com.google.gson.JsonObject

internal interface LateCrashReporter {

    fun handleNdkCrashEvent(event: Map<*, *>, rumWriter: DataWriter<Any>)

    @RequiresApi(Build.VERSION_CODES.R)
    fun handleAnrCrash(
        anrExitInfo: ApplicationExitInfo,
        lastRumViewEventJson: JsonObject,
        rumWriter: DataWriter<Any>
    )
}
