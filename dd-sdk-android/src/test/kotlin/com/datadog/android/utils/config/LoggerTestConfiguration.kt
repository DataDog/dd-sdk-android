/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.config

import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.nhaarman.mockitokotlin2.mock
import fr.xgouchet.elmyr.Forge

internal class LoggerTestConfiguration : TestConfiguration {

    lateinit var mockSdkLogHandler: LogHandler
    lateinit var mockDevLogHandler: LogHandler

    lateinit var originalSdkLogHandler: LogHandler
    lateinit var originalDevLogHandler: LogHandler

    override fun setUp(forge: Forge) {
        mockSdkLogHandler = mock()
        mockDevLogHandler = mock()

        originalSdkLogHandler = sdkLogger.handler
        originalDevLogHandler = devLogger.handler

        sdkLogger.handler = mockSdkLogHandler
        devLogger.handler = mockDevLogHandler
    }

    override fun tearDown(forge: Forge) {
        sdkLogger.handler = originalSdkLogHandler
        devLogger.handler = originalDevLogHandler
    }
}
