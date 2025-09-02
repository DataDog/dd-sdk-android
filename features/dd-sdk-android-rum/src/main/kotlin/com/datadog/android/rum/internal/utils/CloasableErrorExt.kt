/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.utils

import com.datadog.android.api.SdkCore
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumErrorSource

internal const val CLOSABLE_ERROR_MESSAGE = "Error while using the closeable"

internal fun handleClosableError(throwable: Throwable, sdkCore: SdkCore) {
    GlobalRumMonitor.get(sdkCore).addError(CLOSABLE_ERROR_MESSAGE, RumErrorSource.SOURCE, throwable)
}
