/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal.file

import android.content.Context
import com.datadog.android.log.forge.Configurator
import com.datadog.android.log.internal.LogStrategy
import com.datadog.android.log.internal.LogStrategyTest
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import java.io.File
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock

@ForgeConfiguration(Configurator::class)
internal class LogFileStrategyTest :
    LogStrategyTest() {

    @Mock
    lateinit var mockContext: Context
    @TempDir
    lateinit var tempDir: File

    // region LogStrategyTest

    override fun getStrategy(): LogStrategy {
        whenever(mockContext.filesDir) doReturn tempDir
        return LogFileStrategy(mockContext, 250, MAX_BATCH_SIZE)
    }

    override fun waitForNextBatch() {
        Thread.sleep(300)
    }

    // endregion
}
