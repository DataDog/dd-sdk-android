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

internal class SecureWrapper {
    @Suppress("TooGenericExceptionCaught", "UnsafeThirdPartyFunctionCall") // exceptions caught
    internal fun getInt(
        internalLogger: InternalLogger,
        applicationContext: Context,
        key: String
    ): Boolean? {
        return try {
            Settings.Secure.getInt(
                applicationContext.contentResolver,
                key,
                0
            ) != 0
        } catch (e: SettingNotFoundException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER),
                { "Setting cannot be found $key" },
                e
            )
            null
        } catch (e: SecurityException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER),
                { "Security exception accessing $key" },
                e
            )
            null
        } catch (e: RuntimeException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER),
                { "Runtime exception $key" },
                e
            )
            null
        }
    }
}
