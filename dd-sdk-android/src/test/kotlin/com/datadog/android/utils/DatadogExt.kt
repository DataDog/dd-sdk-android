/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils

import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.log.Logger
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.tools.unit.getFieldValue
import com.datadog.tools.unit.setFieldValue
import com.nhaarman.mockitokotlin2.mock

internal fun mockSdkLogHandler(mockHandler: LogHandler): LogHandler {
    val originalHandler = sdkLogger.getFieldValue<LogHandler, Logger>("handler")
    sdkLogger.setFieldValue("handler", mockHandler)
    return originalHandler
}

internal fun restoreSdkLogHandler(originalHandler: LogHandler) {
    sdkLogger.setFieldValue("handler", originalHandler)
}

internal fun mockDevLogHandler(): LogHandler {
    val mockHandler: LogHandler = mock()

    devLogger.setFieldValue("handler", mockHandler)

    return mockHandler
}
