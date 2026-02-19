/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.accessibility

import android.content.Context
import android.provider.Settings
import android.provider.Settings.SettingNotFoundException
import com.datadog.android.api.InternalLogger
import com.datadog.android.rum.internal.generated.DdSdkAndroidRumLogger

internal class GlobalWrapper {
    @Suppress("UnsafeThirdPartyFunctionCall")
    internal fun getFloat(
        internalLogger: InternalLogger,
        applicationContext: Context,
        key: String
    ): Float? {
        return try {
            Settings.Global.getFloat(
                applicationContext.contentResolver,
                key
            )
        } catch (e: SettingNotFoundException) {
            DdSdkAndroidRumLogger(internalLogger).logGlobalSettingNotFound(key = key, throwable = e)
            null
        }
    }
}
