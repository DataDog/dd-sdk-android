/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal.file

import com.datadog.android.BuildConfig
import com.datadog.android.core.internal.data.Orchestrator
import com.datadog.android.core.internal.data.file.FileWriter
import com.datadog.android.log.forge.Configurator
import com.datadog.android.log.internal.domain.Log
import com.datadog.android.log.internal.domain.LogSerializer
import com.datadog.tools.unit.annotations.SystemOutStream
import com.datadog.tools.unit.extensions.SystemOutputExtension
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.ByteArrayOutputStream
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(SystemOutputExtension::class)
)
@MockitoSettings()
@ForgeConfiguration(Configurator::class)
internal class LogFileWriterInvalidTest {

    @Mock
    lateinit var mockFileOrchestrator: Orchestrator

    @TempDir
    lateinit var tempDir: File

    lateinit var testedFileWriter: FileWriter<Log>

    lateinit var logsDir: File

    @BeforeEach
    fun `set up`(@SystemOutStream outputStream: ByteArrayOutputStream) {
        logsDir = File(tempDir, LogFileStrategy.LOGS_FOLDER_NAME)
        logsDir.writeText(I_LIED)

        testedFileWriter =
            FileWriter(
                mockFileOrchestrator,
                logsDir,
                LogSerializer()
            )
        if (BuildConfig.DEBUG) {
            assertThat(outputStream.toString().trim())
                .withFailMessage("We were expecting a log error message")
                .matches("E/DD_LOG: FileWriter: .+")
        }
    }

    @Test
    fun `write does nothing`(@Forgery fakeLog: Log) {
        testedFileWriter.write(fakeLog)

        assertThat(logsDir.isFile)
        assertThat(logsDir.readText()).isEqualTo(I_LIED)
    }

    companion object {
        private const val I_LIED = "I'm a file"
    }
}
