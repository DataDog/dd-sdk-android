/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.file

import android.os.Build
import com.datadog.android.core.internal.data.Orchestrator
import com.datadog.android.core.internal.domain.Serializer
import com.datadog.android.core.internal.threading.AndroidDeferredHandler
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
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

    @Mock
    lateinit var mockDeferredHandler: AndroidDeferredHandler

    @TempDir
    lateinit var tempRootDir: File

    @BeforeEach
    fun `set up`() {
        whenever(mockSerializer.serialize(any())).doAnswer {
            it.getArgument(0)
        }
        whenever(mockDeferredHandler.handle(any())) doAnswer {
            val runnable = it.arguments[0] as Runnable
            runnable.run()
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
    fun `writes a valid model`(forge: Forge) {
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
    fun `writes a collection of models`(forge: Forge) {
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
    fun `writes several models`(forge: Forge) {
        val models = forge.aList { anAlphabeticalString() }
        val fileNameToWriteTo = forge.anAlphaNumericalString()
        val file = File(tempRootDir, fileNameToWriteTo)
        whenever(mockOrchestrator.getWritableFile(any())).thenReturn(file)

        models.forEach {
            testedWriter.write(it)
        }

        assertThat(file.readText())
            .isEqualTo(models.joinToString(","))
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `writes several models with custom separator`(forge: Forge) {
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

        models.forEach {
            testedWriter.write(it)
        }

        assertThat(file.readText())
            .isEqualTo(models.joinToString(separator))
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `does nothing when SecurityException was thrown while providing a file`(
        forge: Forge
    ) {
        val modelValue = forge.anAlphabeticalString()
        val exception = SecurityException(forge.anAlphabeticalString())
        doThrow(exception).whenever(mockOrchestrator).getWritableFile(any())

        testedWriter.write(modelValue)

        verifyZeroInteractions(mockDeferredHandler)
    }

    @Test
    fun `does nothing when FileOrchestrator returns a null file`(
        forge: Forge
    ) {
        val modelValue = forge.anAlphabeticalString()
        whenever(mockOrchestrator.getWritableFile(any())).thenReturn(null)

        // when
        testedWriter.write(modelValue)

        // then
        verifyZeroInteractions(mockDeferredHandler)
    }

    @Test
    fun `if the file is locked from same JVM(not our case) will handle correctly the exception`(
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
    fun `handles well the file locking when accessed from multiple threads`(forge: Forge) {
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
