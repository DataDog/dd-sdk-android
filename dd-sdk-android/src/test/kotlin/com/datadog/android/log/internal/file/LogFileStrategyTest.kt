/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal.file

import android.content.Context
import com.datadog.android.log.internal.LogStrategy
import com.datadog.android.log.internal.LogStrategyTest
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import java.io.File
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock

internal class LogFileStrategyTest :
    LogStrategyTest() {

    @Mock
    lateinit var mockContext: Context
    @TempDir
    lateinit var tempDir: File

    override fun getStrategy(): LogStrategy {
        whenever(mockContext.filesDir) doReturn tempDir
        return LogFileStrategy(mockContext)
    }

    override fun waitForNextBatch() {
        Thread.sleep(LogFileStrategy.MAX_DELAY_BETWEEN_LOGS_MS)
    }
}
