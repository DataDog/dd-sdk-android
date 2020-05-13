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
import com.datadog.tools.unit.assertj.ByteArrayOutputStreamAssert.Companion.assertThat
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

    lateinit var underTest: ImmediateFileWriter<String>

    @Mock
    lateinit var mockedSerializer: Serializer<String>

    @Mock
    lateinit var mockedOrchestrator: Orchestrator

    @Mock
    lateinit var mockDeferredHandler: AndroidDeferredHandler

    @TempDir
    lateinit var rootDir: File

    @BeforeEach
    fun `set up`() {
        whenever(mockedSerializer.serialize(any())).doAnswer {
            it.getArgument(0)
        }
        whenever(mockDeferredHandler.handle(any())) doAnswer {
            val runnable = it.arguments[0] as Runnable
            runnable.run()
        }
        underTest = ImmediateFileWriter(
            mockedOrchestrator,
            mockedSerializer
        )
    }

    @AfterEach
    fun `tear down`() {
        rootDir.deleteRecursively()
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `writes a valid model`(forge: Forge) {
        val model = forge.anAlphabeticalString()
        val fileNameToWriteTo = forge.anAlphaNumericalString()
        val file = File(rootDir, fileNameToWriteTo)
        whenever(mockedOrchestrator.getWritableFile(any())).thenReturn(file)

        underTest.write(model)

        assertThat(file.readText())
            .isEqualTo(model)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `writes a collection of models`(forge: Forge) {
        val models: List<String> = forge.aList { forge.anAlphabeticalString() }
        val fileNameToWriteTo = forge.anAlphaNumericalString()
        val file = File(rootDir, fileNameToWriteTo)
        whenever(mockedOrchestrator.getWritableFile(any())).thenReturn(file)

        underTest.write(models)

        assertThat(file.readText().split(","))
            .isEqualTo(models)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `writes several models`(forge: Forge) {
        val models = forge.aList { anAlphabeticalString() }
        val fileNameToWriteTo = forge.anAlphaNumericalString()
        val file = File(rootDir, fileNameToWriteTo)
        whenever(mockedOrchestrator.getWritableFile(any())).thenReturn(file)

        models.forEach {
            underTest.write(it)
        }

        assertThat(file.readText())
            .isEqualTo(models.joinToString(","))
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `writes several models with custom separator`(forge: Forge) {
        val separator = forge.anAsciiString()
        underTest = ImmediateFileWriter(
            mockedOrchestrator,
            mockedSerializer,
            separator
        )
        val models = forge.aList { anAlphabeticalString() }
        val fileNameToWriteTo = forge.anAlphaNumericalString()
        val file = File(rootDir, fileNameToWriteTo)
        whenever(mockedOrchestrator.getWritableFile(any())).thenReturn(file)

        models.forEach {
            underTest.write(it)
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
        doThrow(exception).whenever(mockedOrchestrator).getWritableFile(any())

        underTest.write(modelValue)

        verifyZeroInteractions(mockDeferredHandler)
    }

    @Test
    fun `does nothing when FileOrchestrator returns a null file`(
        forge: Forge
    ) {
        val modelValue = forge.anAlphabeticalString()
        whenever(mockedOrchestrator.getWritableFile(any())).thenReturn(null)

        // when
        underTest.write(modelValue)

        // then
        verifyZeroInteractions(mockDeferredHandler)
    }

    @Test
    fun `if the file is locked from same JVM(not our case) will handle correctly the exception`(
        forge: Forge
    ) {
        val models = forge.aList { anAlphabeticalString() }
        val fileNameToWriteTo = forge.anAlphaNumericalString()
        val file = File(rootDir, fileNameToWriteTo)
        file.createNewFile()
        whenever(mockedOrchestrator.getWritableFile(any())).thenReturn(file)

        val outputStream = file.outputStream()
        val lock = outputStream.channel.lock()
        try {
            models.forEach {
                underTest.write(it)
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
        val file = File(rootDir, fileNameToWriteTo)
        file.createNewFile()
        whenever(mockedOrchestrator.getWritableFile(any())).thenReturn(file)
        val countDownLatch = CountDownLatch(2)

        Thread {
            models.take(5).forEach {
                underTest.write(it)
            }
            countDownLatch.countDown()
        }.start()

        Thread {
            models.takeLast(5).forEach {
                underTest.write(it)
            }
            countDownLatch.countDown()
        }.start()

        countDownLatch.await(4, TimeUnit.SECONDS)
        assertThat(file.readText().split(",").size)
            .isEqualTo(models.size)
    }
}
