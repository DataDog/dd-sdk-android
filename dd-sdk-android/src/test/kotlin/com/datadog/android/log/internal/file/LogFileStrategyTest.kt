/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal.file

import com.datadog.android.core.internal.data.Reader
import com.datadog.android.core.internal.data.Writer
import com.datadog.android.core.internal.data.file.FileWriter
import com.datadog.android.core.internal.threading.LazyHandlerThread
import com.datadog.android.log.forge.Configurator
import com.datadog.android.log.internal.LogStrategy
import com.datadog.android.log.internal.LogStrategyTest
import com.datadog.android.log.internal.domain.Log
import com.datadog.tools.unit.invokeMethod
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@ForgeConfiguration(Configurator::class)
internal class LogFileStrategyTest :
    LogStrategyTest() {

    // region LogStrategyTest

    override fun getStrategy(): LogStrategy {
        return LogFileStrategy(
            context = mockContext,
            recentDelayMs = RECENT_DELAY_MS,
            maxBatchSize = MAX_BATCH_SIZE,
            maxLogPerBatch = MAX_LOGS_PER_BATCH,
            maxDiskSpace = MAX_DISK_SPACE
        )
    }

    override fun setUp(writer: Writer<Log>, reader: Reader) {
        // add fake data into the old data directory
        val oldDir = File(tempDir, LogFileStrategy.DATA_FOLDER_ROOT)
        oldDir.mkdirs()
        val file1 = File(oldDir, "file1")
        val file2 = File(oldDir, "file2")
        file1.createNewFile()
        file2.createNewFile()
        assertThat(oldDir).exists()
        (testedWriter as FileWriter<Log>).deferredHandler = mockDeferredHandler
        (testedWriter as FileWriter<Log>).invokeMethod("consumeQueue",
            methodEnclosingClass = LazyHandlerThread::class.java) // consume all the queued messages
    }

    override fun waitForNextBatch() {
        Thread.sleep(RECENT_DELAY_MS * 2)
    }

    // endregion

    @Test
    fun `migrates the data from v0 to v1`() {
        val oldDir = File(tempDir, LogFileStrategy.DATA_FOLDER_ROOT)
        assertThat(oldDir).doesNotExist()
    }

    @Test
    fun `read returns null when 1st batch is already sent but file still present`(
        @Forgery fakeLog: Log
    ) {
        testedWriter.write(fakeLog)
        waitForNextBatch()
        val batch = testedReader.readNextBatch()
        checkNotNull(batch)

        testedReader.dropBatch(batch.id)
        val logsDir = File(tempDir, LogFileStrategy.LOGS_FOLDER)
        val file = File(logsDir, batch.id)
        file.writeText("I'm still there !")
        val batch2 = testedReader.readNextBatch()

        assertThat(batch2)
            .isNull()
    }

    companion object {
        private const val RECENT_DELAY_MS = 150L
        private const val MAX_DISK_SPACE = 16 * 32 * 1024L
    }
}
