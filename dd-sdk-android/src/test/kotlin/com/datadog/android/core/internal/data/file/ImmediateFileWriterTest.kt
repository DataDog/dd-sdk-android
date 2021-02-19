/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.file

import android.os.Build
import com.datadog.android.core.internal.data.Orchestrator
import com.datadog.android.core.internal.domain.Serializer
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(ApiLevelExtension::class)
)
@ForgeConfiguration(Configurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class ImmediateFileWriterTest {

    lateinit var testedWriter: ImmediateFileWriter<String>

    @Mock
    lateinit var mockSerializer: Serializer<String>

    @Mock
    lateinit var mockOrchestrator: Orchestrator

    @TempDir
    lateinit var tempRootDir: File

    @BeforeEach
    fun `set up`() {
        whenever(mockSerializer.serialize(any())).doAnswer {
            it.getArgument(0)
        }
        testedWriter = ImmediateFileWriter(
            mockOrchestrator,
            mockSerializer
        )
    }

    @AfterEach
    fun `tear down`() {
        tempRootDir.deleteRecursively()
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `𝕄 write a valid model 𝕎 write(model)`(forge: Forge) {
        val model = forge.anAlphabeticalString()
        val fileNameToWriteTo = forge.anAlphaNumericalString()
        val file = File(tempRootDir, fileNameToWriteTo)
        whenever(mockOrchestrator.getWritableFile(any())).thenReturn(file)

        testedWriter.write(model)

        assertThat(file.readText())
            .isEqualTo(model)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `𝕄 write a collection of models 𝕎 write(list)`(forge: Forge) {
        val models: List<String> = forge.aList { forge.anAlphabeticalString() }
        val fileNameToWriteTo = forge.anAlphaNumericalString()
        val file = File(tempRootDir, fileNameToWriteTo)
        whenever(mockOrchestrator.getWritableFile(any())).thenReturn(file)

        testedWriter.write(models)

        assertThat(file.readText().split(","))
            .isEqualTo(models)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `𝕄 write several models 𝕎 write()+`(forge: Forge) {
        // GIVEN
        val models = forge.aList { anAlphabeticalString() }
        val fileNameToWriteTo = forge.anAlphaNumericalString()
        val file = File(tempRootDir, fileNameToWriteTo)
        whenever(mockOrchestrator.getWritableFile(any())).thenReturn(file)

        // WHEN
        models.forEach {
            testedWriter.write(it)
        }

        // THEN
        assertThat(file.readText())
            .isEqualTo(models.joinToString(","))
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `𝕄 write several models with custom separator 𝕎 write()+`(forge: Forge) {
        // GIVEN
        val separator = forge.anAsciiString()
        testedWriter = ImmediateFileWriter(
            mockOrchestrator,
            mockSerializer,
            separator
        )
        val models = forge.aList { anAlphabeticalString() }
        val fileNameToWriteTo = forge.anAlphaNumericalString()
        val file = File(tempRootDir, fileNameToWriteTo)
        whenever(mockOrchestrator.getWritableFile(any())).thenReturn(file)

        // WHEN
        models.forEach {
            testedWriter.write(it)
        }

        assertThat(file.readText())
            .isEqualTo(models.joinToString(separator))
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `𝕄 do nothing 𝕎 write() and serialisation fails`(
        @StringForgery model: String,
        @StringForgery errorMessage: String
    ) {
        // GIVEN
        val throwable = RuntimeException(errorMessage)
        doThrow(throwable).whenever(mockSerializer).serialize(model)

        // WHEN
        testedWriter.write(model)

        // THEN
        assertThat(tempRootDir.listFiles()).isEmpty()
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `𝕄 do nothing 𝕎 write() with SecurityException thrown while providing a file`(
        forge: Forge
    ) {
        // GIVEN
        val modelValue = forge.anAlphabeticalString()
        val exception = SecurityException(forge.anAlphabeticalString())
        doThrow(exception).whenever(mockOrchestrator).getWritableFile(any())

        // WHEN
        testedWriter.write(modelValue)

        // THEN
        assertThat(tempRootDir.listFiles()).isEmpty()
    }

    @Test
    fun `𝕄 do nothing 𝕎 write() and FileOrchestrator returns a null file`(
        forge: Forge
    ) {
        // GIVEN
        val modelValue = forge.anAlphabeticalString()
        whenever(mockOrchestrator.getWritableFile(any())).thenReturn(null)

        // WHEN
        testedWriter.write(modelValue)

        // THEN
        assertThat(tempRootDir.listFiles()).isEmpty()
    }

    @Test
    fun `𝕄 do nothing 𝕎 write() and FileOrchestrator returns a file that doesn't exist`(
        @StringForgery dirName: String,
        @StringForgery fileName: String,
        forge: Forge
    ) {
        // GIVEN
        val nonExistentDir = File(tempRootDir, dirName)
        val file = File(nonExistentDir, fileName)
        val modelValue = forge.anAlphabeticalString()
        whenever(mockOrchestrator.getWritableFile(any())).thenReturn(file)

        // WHEN
        testedWriter.write(modelValue)

        // THEN
        assertThat(tempRootDir.listFiles()).isEmpty()
    }

    @Test
    fun `𝕄 respect file locks 𝕎 write() on locked file`(
        forge: Forge
    ) {
        val models = forge.aList { anAlphabeticalString() }
        val fileNameToWriteTo = forge.anAlphaNumericalString()
        val file = File(tempRootDir, fileNameToWriteTo)
        file.createNewFile()
        whenever(mockOrchestrator.getWritableFile(any())).thenReturn(file)

        val outputStream = file.outputStream()
        val lock = outputStream.channel.lock()
        try {
            models.forEach {
                testedWriter.write(it)
            }
        } finally {
            lock.release()
            outputStream.close()
        }

        assertThat(file.readText())
            .isEmpty()
    }

    @Test
    fun `𝕄 lock and release file 𝕎 write() from multiple threads`(forge: Forge) {
        val models = forge.aList(size = 10) { anAlphabeticalString() }
        val fileNameToWriteTo = forge.anAlphaNumericalString()
        val file = File(tempRootDir, fileNameToWriteTo)
        file.createNewFile()
        whenever(mockOrchestrator.getWritableFile(any())).thenReturn(file)
        val countDownLatch = CountDownLatch(2)

        Thread {
            models.take(5).forEach {
                testedWriter.write(it)
            }
            countDownLatch.countDown()
        }.start()

        Thread {
            models.takeLast(5).forEach {
                testedWriter.write(it)
            }
            countDownLatch.countDown()
        }.start()

        countDownLatch.await(4, TimeUnit.SECONDS)
        assertThat(file.readText().split(",").size)
            .isEqualTo(models.size)
    }
}
