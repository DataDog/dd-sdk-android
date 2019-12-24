/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal.file

import android.content.Context
import android.os.Build
import com.datadog.android.core.internal.data.file.FileWriter
import com.datadog.android.core.internal.data.Reader
import com.datadog.android.core.internal.data.Writer
import com.datadog.android.core.internal.threading.DeferredHandler
import com.datadog.android.log.forge.Configurator
import com.datadog.android.log.internal.LogStrategy
import com.datadog.android.log.internal.LogStrategyTest
import com.datadog.android.log.internal.domain.Log
import com.datadog.tools.unit.annotations.TestTargetApi
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Case
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import java.io.File
import org.assertj.core.api.Assertions.assertThat
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
        return LogFileStrategy(
            context = mockContext,
            recentDelayMs = RECENT_DELAY_MS,
            maxBatchSize = MAX_BATCH_SIZE,
            maxLogPerBatch = MAX_LOGS_PER_BATCH,
            maxDiskSpace = MAX_DISK_SPACE
        )
    }

    override fun setUp(writer: Writer, reader: Reader) {
        whenever(mockDeferredHandler.handle(any())) doAnswer {
            val runnable = it.arguments[0] as Runnable
            runnable.run()
        }
        (writer as FileWriter).deferredHandler = mockDeferredHandler
    }

    override fun waitForNextBatch() {
        Thread.sleep(RECENT_DELAY_MS * 2)
    }

    // endregion

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `read returns null when 1st batch is already sent but file still present`(
        @Forgery fakeLog: Log
    ) {
        testedWriter.writeLog(fakeLog)
        waitForNextBatch()
        val batch = testedReader.readNextBatch()
        checkNotNull(batch)

        testedReader.dropBatch(batch.id)
        val logsDir = File(tempDir, LogFileStrategy.LOGS_FOLDER_NAME)
        val file = File(logsDir, batch.id)
        file.writeText("I'm still there !")
        val batch2 = testedReader.readNextBatch()

        assertThat(batch2)
            .isNull()
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `read returns null when batch contains invalid base64 (size)`(
        forge: Forge
    ) {
        val ddLogDir = File(tempDir, "dd-logs")
        val fakeFile = File(ddLogDir, System.currentTimeMillis().toString())
        val size = forge.aSmallInt() * 4
        val validBase64 = forge.anAlphabeticalString(Case.ANY, size)
        val invalidBase64 = "$validBase64==" // Invalid padding

        fakeFile.writeText(invalidBase64, Charsets.UTF_8)
        waitForNextBatch()
        val batch = testedReader.readNextBatch()
        checkNotNull(batch)

        assertThat(batch.logs)
            .isEmpty()
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `read returns null when batch contains invalid base64 (illegal chars)`(
        forge: Forge
    ) {
        val ddLogDir = File(tempDir, "dd-logs")
        val fakeFile = File(ddLogDir, System.currentTimeMillis().toString())
        val invalidBase64 = forge.aStringMatching("[a-zA-Z0-9]{4}[&._!?%*]{4}[a-zA-Z0-9]{4}")

        fakeFile.writeText(invalidBase64, Charsets.UTF_8)
        waitForNextBatch()
        val batch = testedReader.readNextBatch()
        checkNotNull(batch)

        assertThat(batch.logs)
            .isEmpty()
    }

    companion object {
        private const val RECENT_DELAY_MS = 150L
        private const val MAX_DISK_SPACE = 16 * 32 * 1024L
    }
}
