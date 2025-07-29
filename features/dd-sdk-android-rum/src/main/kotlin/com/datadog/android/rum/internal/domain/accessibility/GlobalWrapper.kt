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

internal class GlobalWrapper {
    @Suppress("TooGenericExceptionCaught", "UnsafeThirdPartyFunctionCall") // exceptions caught
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
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER),
                { "Setting not found $key" },
                e
            )
            null
        } catch (e: NumberFormatException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER),
                { "Number format exception getting $key" },
                e
            )
            null
        } catch (e: RuntimeException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER),
                { "Runtime exception getting $key" },
                e
            )
            null
        }
    }
}
