/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal.file

import com.datadog.android.log.Configurator
import com.datadog.android.log.internal.Log
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.io.TempDir
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

/**
 * This test checks the behavior of a FileWriter
 */
@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class LogFileWriterInvalidTest {

    @TempDir
    lateinit var tempDir: File

    lateinit var testedFileWriter: LogFileWriter

    lateinit var logsDir: File

    @BeforeEach
    fun `set up`() {
        logsDir = File(tempDir, LogFileStrategy.LOGS_FOLDER_NAME)
        logsDir.writeText(I_LIED)

        testedFileWriter = LogFileWriter(logsDir, 250L)
    }

    @Test
    fun `write does nothing`(@Forgery fakeLog: Log) {
        testedFileWriter.writeLog(fakeLog)

        assertThat(logsDir.isFile)
        assertThat(logsDir.readText()).isEqualTo(I_LIED)
    }

    companion object {
        private const val I_LIED = "I'm a file"
    }
}
