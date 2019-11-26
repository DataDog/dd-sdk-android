/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal.file

import android.content.Context
import android.os.Build
import com.datadog.android.log.forge.Configurator
import com.datadog.android.log.internal.Log
import com.datadog.android.log.internal.LogReader
import com.datadog.android.log.internal.LogStrategy
import com.datadog.android.log.internal.LogStrategyTest
import com.datadog.android.log.internal.LogWriter
import com.datadog.android.utils.TestTargetApi
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import java.io.File
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock

@ForgeConfiguration(Configurator::class)
internal class LogFileStrategyTest :
    LogStrategyTest() {

    @Mock
    lateinit var mockContext: Context
    @Mock(lenient = true)
    lateinit var mockDeferredHandler: DeferredHandler
    @TempDir
    lateinit var tempDir: File

    // region LogStrategyTest

    override fun getStrategy(): LogStrategy {
        whenever(mockContext.filesDir) doReturn tempDir
        return LogFileStrategy(mockContext, 250, MAX_BATCH_SIZE)
    }

    override fun setUp(writer: LogWriter, reader: LogReader) {
        whenever(mockDeferredHandler.handle(any())) doAnswer {
            val runnable = it.arguments[0] as Runnable
            runnable.run()
        }
        (writer as LogFileWriter).deferredHandler = mockDeferredHandler
    }

    override fun waitForNextBatch() {
        Thread.sleep(300)
    }

    // endregion

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `read returns null when 1st batch is already sent but file still present`(
        @Forgery fakeLog: Log
    ) {
        testedLogWriter.writeLog(fakeLog)
        waitForNextBatch()
        val batch = testedLogReader.readNextBatch()
        checkNotNull(batch)

        testedLogReader.dropBatch(batch.id)
        val logsDir = File(tempDir, LogFileStrategy.LOGS_FOLDER_NAME)
        val file = File(logsDir, batch.id)
        file.writeText("I'm still there !")
        val batch2 = testedLogReader.readNextBatch()

        Assertions.assertThat(batch2)
            .isNull()
    }
}
