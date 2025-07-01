/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.accessibility

import android.content.Context
import android.provider.Settings
import com.datadog.android.api.InternalLogger

internal class GlobalWrapper {
    internal fun isReducedAnimationsEnabled(internalLogger: InternalLogger, applicationContext: Context): Boolean? {
        return try {
            Settings.Global.getFloat(
                applicationContext.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE, 1.0f
            ) == 0.0f
        } catch (e: Exception) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER),
                { "Error retrieving animation duration" },
                e
            )
            null
        }
    }
}
