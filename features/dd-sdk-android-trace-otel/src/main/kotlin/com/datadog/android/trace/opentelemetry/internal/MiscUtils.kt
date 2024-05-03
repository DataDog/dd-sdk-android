/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.opentelemetry.internal

import android.os.Build
import com.datadog.android.api.InternalLogger

internal const val NEEDS_DESUGARING_ERROR_MESSAGE =
    "Trying to use OpenTelemetry SDK support for Android 23 and below. " +
        "In order for this to properly work you will need to enable coreDesugaring " +
        "in your compileOptions"

internal fun <T : Any?> executeIfJavaFunctionPackageExists(
    internalLogger: InternalLogger? = null,
    defaultActionReturnValue: T,
    action: () -> T
): T {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        return action()
    } else {
        return try {
            // we are forcing here the checkup as it could be triggered only at runtime and will be too late
            @Suppress("UnsafeThirdPartyFunctionCall")
            Class.forName("java.util.function.Function")
            action()
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            // generic catch to avoid any crash
            internalLogger?.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                { NEEDS_DESUGARING_ERROR_MESSAGE },
                e
            )
            defaultActionReturnValue
        }
    }
}
