/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.data.file

import android.os.Build
import com.datadog.android.core.internal.data.Orchestrator
import com.datadog.android.core.internal.domain.PayloadDecoration
import com.datadog.android.core.internal.domain.Serializer
import com.datadog.android.rum.internal.domain.event.RumEvent
import com.datadog.android.rum.internal.domain.event.RumEventSerializer
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestTargetApi
import com.google.gson.JsonParser
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
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
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumFileWriterTest {
    lateinit var testedWriter: RumFileWriter

    lateinit var rumSerializer: Serializer<RumEvent>

    @Mock
    lateinit var mockOrchestrator: Orchestrator

    @StringForgery(regex = "[a-zA-z]{3,10}")
    lateinit var fakeNdkCrashDataFolderName: String

    lateinit var fakeNdkCrashDataDirectory: File

    @TempDir
    lateinit var tempRootDir: File

    @BeforeEach
    fun `set up`() {
        fakeNdkCrashDataDirectory = File(tempRootDir, fakeNdkCrashDataFolderName)
        rumSerializer = RumEventSerializer()
        testedWriter = RumFileWriter(
            fakeNdkCrashDataDirectory,
            mockOrchestrator,
            rumSerializer
        )
    }

    @AfterEach
    fun `tear down`() {
        tempRootDir.deleteRecursively()
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `𝕄 write a valid model 𝕎 write(model)`(forge: Forge) {
        val fakeModel: RumEvent = forge.getForgery()
        val fileNameToWriteTo = forge.anAlphaNumericalString()
        val file = File(tempRootDir, fileNameToWriteTo)
        whenever(mockOrchestrator.getWritableFile(any())).thenReturn(file)

        testedWriter.write(fakeModel)

        assertThat(file.readText())
            .isEqualTo(rumSerializer.serialize(fakeModel))
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `𝕄 write a collection of models 𝕎 write(list)`(forge: Forge) {
        val fakeModels: List<RumEvent> = forge.aList { forge.getForgery(RumEvent::class.java) }
        val fileNameToWriteTo = forge.anAlphaNumericalString()
        val file = File(tempRootDir, fileNameToWriteTo)
        whenever(mockOrchestrator.getWritableFile(any())).thenReturn(file)

        testedWriter.write(fakeModels)

        assertThat(file.readText())
            .isEqualTo(
                fakeModels.map { rumSerializer.serialize(it) }
                    .joinToString(PayloadDecoration.JSON_ARRAY_DECORATION.separator)
            )
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `𝕄 write several models with custom separator 𝕎 write()+`(forge: Forge) {
        val separator = forge.anAsciiString()
        testedWriter = RumFileWriter(
            tempRootDir,
            mockOrchestrator,
            rumSerializer,
            separator
        )
        val fakeModels: List<RumEvent> = forge.aList { forge.getForgery(RumEvent::class.java) }
        val fileNameToWriteTo = forge.anAlphaNumericalString()
        val file = File(tempRootDir, fileNameToWriteTo)
        whenever(mockOrchestrator.getWritableFile(any())).thenReturn(file)

        fakeModels.forEach {
            testedWriter.write(it)
        }

        assertThat(file.readText())
            .isEqualTo(fakeModels.map { rumSerializer.serialize(it) }.joinToString(separator))
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `𝕄 do nothing 𝕎 write() and serialisation fails`(
        @Forgery fakeModel: RumEvent,
        @StringForgery errorMessage: String
    ) {
        val mockSerializer: Serializer<RumEvent> = mock()
        testedWriter = RumFileWriter(
            tempRootDir,
            mockOrchestrator,
            mockSerializer
        )
        val throwable = RuntimeException(errorMessage)
        doThrow(throwable).whenever(mockSerializer).serialize(fakeModel)

        testedWriter.write(fakeModel)

        assertThat(tempRootDir.listFiles()).containsExactly(fakeNdkCrashDataDirectory)
        assertThat(fakeNdkCrashDataDirectory.listFiles()).isEmpty()
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `𝕄 do nothing 𝕎 write() with SecurityException thrown while providing a file`(
        @Forgery fakeModel: RumEvent,
        forge: Forge
    ) {
        val exception = SecurityException(forge.anAlphabeticalString())
        doThrow(exception).whenever(mockOrchestrator).getWritableFile(any())

        testedWriter.write(fakeModel)

        assertThat(tempRootDir.listFiles()).containsExactly(fakeNdkCrashDataDirectory)
        assertThat(fakeNdkCrashDataDirectory.listFiles()).isEmpty()
    }

    @Test
    fun `𝕄 do nothing 𝕎 write and FileOrchestrator returns a null file`(
        @Forgery fakeModel: RumEvent,
        forge: Forge
    ) {
        whenever(mockOrchestrator.getWritableFile(any())).thenReturn(null)

        // When
        testedWriter.write(fakeModel)

        // Then
        assertThat(tempRootDir.listFiles()).containsExactly(fakeNdkCrashDataDirectory)
        assertThat(fakeNdkCrashDataDirectory.listFiles()).isEmpty()
    }

    @Test
    fun `𝕄 do nothing 𝕎 write() and FileOrchestrator returns a file that doesn't exist`(
        @Forgery fakeModel: RumEvent,
        @StringForgery dirName: String,
        @StringForgery fileName: String,
        forge: Forge
    ) {
        val nonExistentDir = File(tempRootDir, dirName)
        val file = File(nonExistentDir, fileName)
        whenever(mockOrchestrator.getWritableFile(any())).thenReturn(file)

        // When
        testedWriter.write(fakeModel)

        // Then
        assertThat(tempRootDir.listFiles()).containsExactly(fakeNdkCrashDataDirectory)
        assertThat(fakeNdkCrashDataDirectory.listFiles()).isEmpty()
    }

    @Test
    fun `𝕄 respect file locks 𝕎 write() on locked file`(
        forge: Forge
    ) {
        val fakeModels = forge.aList { forge.getForgery(RumEvent::class.java) }
        val fileNameToWriteTo = forge.anAlphaNumericalString()
        val file = File(tempRootDir, fileNameToWriteTo)
        file.createNewFile()
        whenever(mockOrchestrator.getWritableFile(any())).thenReturn(file)

        val outputStream = file.outputStream()
        val lock = outputStream.channel.lock()
        try {
            fakeModels.forEach {
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
        val fakeModels = forge.aList(size = 10) { forge.getForgery(RumEvent::class.java) }
        val fileNameToWriteTo = forge.anAlphaNumericalString()
        val file = File(tempRootDir, fileNameToWriteTo)
        file.createNewFile()
        whenever(mockOrchestrator.getWritableFile(any())).thenReturn(file)
        val countDownLatch = CountDownLatch(2)

        Thread {
            fakeModels.take(5).forEach {
                testedWriter.write(it)
            }
            countDownLatch.countDown()
        }.start()

        Thread {
            fakeModels.takeLast(5).forEach {
                testedWriter.write(it)
            }
            countDownLatch.countDown()
        }.start()

        countDownLatch.await(4, TimeUnit.SECONDS)
        val rawData = file.readText()
        val dataAsJsonArray = "[$rawData]"
        val jsonArray = JsonParser.parseString(dataAsJsonArray).asJsonArray
        assertThat(jsonArray.size())
            .isEqualTo(fakeModels.map { rumSerializer.serialize(it) }.size)
    }

    @Test
    fun `M persist the event into the NDK crash folder W write() { ViewEvent }`(forge: Forge) {
        // GIVEN
        val fakeModel: RumEvent = forge.getForgery(RumEvent::class.java)
            .copy(event = forge.getForgery(ViewEvent::class.java))
        val fileNameToWriteTo = forge.anAlphaNumericalString()
        val file = File(tempRootDir, fileNameToWriteTo)
        fakeNdkCrashDataDirectory.mkdirs()
        file.createNewFile()
        whenever(mockOrchestrator.getWritableFile(any())).thenReturn(file)

        // WHEN
        testedWriter.write(fakeModel)

        // THEN
        assertThat(fakeNdkCrashDataDirectory.listFiles()?.get(0)?.readText())
            .isEqualTo(rumSerializer.serialize(fakeModel))
    }

    @Test
    fun `M always cleanup the last_view_event W write() { ViewEvent }`(forge: Forge) {
        // GIVEN
        val fakeModel1: RumEvent = forge.getForgery(RumEvent::class.java)
            .copy(event = forge.getForgery(ViewEvent::class.java))
        val fakeModel2: RumEvent = forge.getForgery(RumEvent::class.java)
            .copy(event = forge.getForgery(ViewEvent::class.java))
        val fileNameToWriteTo = forge.anAlphaNumericalString()
        val file = File(tempRootDir, fileNameToWriteTo)
        file.createNewFile()
        whenever(mockOrchestrator.getWritableFile(any())).thenReturn(file)

        // WHEN
        testedWriter.write(fakeModel1)
        testedWriter.write(fakeModel2)

        // THEN
        assertThat(fakeNdkCrashDataDirectory.listFiles()?.get(0)?.readText())
            .isEqualTo(rumSerializer.serialize(fakeModel2))
    }

    @Test
    fun `M create the ndk crash data directory if does not exists W write() { ViewEvent }`(
        forge: Forge
    ) {
        // GIVEN
        val fakeModel: RumEvent = forge.getForgery(RumEvent::class.java)
            .copy(event = forge.getForgery(ViewEvent::class.java))
        val fileNameToWriteTo = forge.anAlphaNumericalString()
        val file = File(tempRootDir, fileNameToWriteTo)
        file.createNewFile()
        whenever(mockOrchestrator.getWritableFile(any())).thenReturn(file)

        // WHEN
        testedWriter.write(fakeModel)

        // THEN
        assertThat(fakeNdkCrashDataDirectory.listFiles()?.get(0)?.readText())
            .isEqualTo(rumSerializer.serialize(fakeModel))
    }

    @Test
    fun `M not persist the event into the NDK crash folder W write() { not a ViewEvent }`(
        forge: Forge
    ) {
        // GIVEN
        val fakeModel: RumEvent = forge.getForgery(RumEvent::class.java)
            .copy(
                event = forge.anElementFrom(
                    listOf(
                        forge.getForgery(ActionEvent::class.java),
                        forge.getForgery(ErrorEvent::class.java),
                        forge.getForgery(ResourceEvent::class.java)
                    )
                )
            )
        val fileNameToWriteTo = forge.anAlphaNumericalString()
        val file = File(tempRootDir, fileNameToWriteTo)
        file.createNewFile()
        whenever(mockOrchestrator.getWritableFile(any())).thenReturn(file)

        // WHEN
        testedWriter.write(fakeModel)

        // THEN
        assertThat(fakeNdkCrashDataDirectory.listFiles()).isEmpty()
    }
}
