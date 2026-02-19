/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.display

import android.content.Context
import android.provider.Settings
import com.datadog.android.api.InternalLogger
import com.datadog.android.rum.internal.generated.DdSdkAndroidRumLogger

internal class SystemSettingsWrapper(
    private val applicationContext: Context,
    private val internalLogger: InternalLogger
) {

    private val logger = DdSdkAndroidRumLogger(internalLogger)

    fun getInt(name: String): Int {
        return try {
            Settings.System.getInt(applicationContext.contentResolver, name)
        } catch (e: Settings.SettingNotFoundException) {
            logger.logSystemSettingNotFound(settingName = name, throwable = e)
            Integer.MIN_VALUE
        }
    }
}
