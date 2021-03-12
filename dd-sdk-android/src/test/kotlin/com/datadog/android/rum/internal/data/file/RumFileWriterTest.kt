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
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.NoOpRumMonitor
import com.datadog.android.rum.internal.domain.event.RumEvent
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.rum.internal.monitor.EventType
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestTargetApi
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
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

    @Mock
    lateinit var mockRumSerializer: Serializer<RumEvent>

    @Mock
    lateinit var mockOrchestrator: Orchestrator

    @Mock
    lateinit var mockRumMonitor: AdvancedRumMonitor

    @StringForgery(regex = "[a-zA-z]{3,10}")
    lateinit var fakeNdkCrashDataFolderName: String

    lateinit var fakeNdkCrashDataDirectory: File

    @StringForgery
    lateinit var fakeSerializedEvent: String

    @StringForgery(StringForgeryType.ALPHABETICAL)
    lateinit var fakeFileName: String

    lateinit var fakeOutputFile: File

    @TempDir
    lateinit var tempRootDir: File

    @BeforeEach
    fun `set up`() {
        GlobalRum.monitor = mockRumMonitor
        GlobalRum.isRegistered.set(true)
        fakeOutputFile = File(tempRootDir, fakeFileName)
        fakeNdkCrashDataDirectory = File(tempRootDir, fakeNdkCrashDataFolderName)
        testedWriter = RumFileWriter(
            fakeNdkCrashDataDirectory,
            mockOrchestrator,
            mockRumSerializer
        )
    }

    @AfterEach
    fun `tear down`() {
        tempRootDir.deleteRecursively()

        GlobalRum.monitor = NoOpRumMonitor()
        GlobalRum.isRegistered.set(false)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `𝕄 write a valid model 𝕎 write(model)`(forge: Forge) {
        val fakeModel: RumEvent = forge.getForgery()
        whenever(mockOrchestrator.getWritableFile(any())).thenReturn(fakeOutputFile)
        whenever(mockRumSerializer.serialize(fakeModel)) doReturn fakeSerializedEvent

        testedWriter.write(fakeModel)

        assertThat(fakeOutputFile.readText()).isEqualTo(fakeSerializedEvent)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `𝕄 write a collection of models 𝕎 write(list)`(forge: Forge) {
        val fakeModels = forge.aList { forge.getForgery(RumEvent::class.java) }
        val serializedEvents = forge.aList(fakeModels.size) { forge.anAlphabeticalString() }
        whenever(mockOrchestrator.getWritableFile(any())).thenReturn(fakeOutputFile)
        for (i in fakeModels.indices) {
            whenever(mockRumSerializer.serialize(fakeModels[i])) doReturn serializedEvents[i]
        }

        testedWriter.write(fakeModels)

        assertThat(fakeOutputFile.readText())
            .isEqualTo(
                serializedEvents.joinToString(PayloadDecoration.JSON_ARRAY_DECORATION.separator)
            )
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `𝕄 write several models with custom separator 𝕎 write()+`(forge: Forge) {
        val separator = forge.anAsciiString()
        testedWriter = RumFileWriter(
            tempRootDir,
            mockOrchestrator,
            mockRumSerializer,
            separator
        )
        val fakeModels: List<RumEvent> = forge.aList { forge.getForgery(RumEvent::class.java) }
        val serializedEvents = forge.aList(fakeModels.size) { forge.anAlphabeticalString() }
        whenever(mockOrchestrator.getWritableFile(any())).thenReturn(fakeOutputFile)
        for (i in fakeModels.indices) {
            whenever(mockRumSerializer.serialize(fakeModels[i])) doReturn serializedEvents[i]
        }

        fakeModels.forEach {
            testedWriter.write(it)
        }

        assertThat(fakeOutputFile.readText())
            .isEqualTo(serializedEvents.joinToString(separator))
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
        @Forgery fakeModel: RumEvent
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
        @StringForgery fileName: String
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
        fakeOutputFile.createNewFile()
        whenever(mockOrchestrator.getWritableFile(any())).thenReturn(fakeOutputFile)

        val outputStream = fakeOutputFile.outputStream()
        val lock = outputStream.channel.lock()
        try {
            fakeModels.forEach {
                testedWriter.write(it)
            }
        } finally {
            lock.release()
            outputStream.close()
        }

        assertThat(fakeOutputFile.readText())
            .isEmpty()
    }

    @Test
    fun `𝕄 lock and release file 𝕎 write() from multiple threads`(forge: Forge) {
        val fakeModels = forge.aList(size = 10) { forge.getForgery(RumEvent::class.java) }
        val serializedEvents = forge.aList(fakeModels.size) { forge.anAlphabeticalString() }
        fakeOutputFile.createNewFile()
        whenever(mockOrchestrator.getWritableFile(any())).thenReturn(fakeOutputFile)
        for (i in fakeModels.indices) {
            whenever(mockRumSerializer.serialize(fakeModels[i])) doReturn serializedEvents[i]
        }
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
        val rawData = fakeOutputFile.readText()
            .split(PayloadDecoration.JSON_ARRAY_DECORATION.separator.toString())
            .sorted()
        assertThat(rawData).isEqualTo(serializedEvents.sorted())
    }

    @Test
    fun `M persist the event into the NDK crash folder W write() { ViewEvent }`(forge: Forge) {
        // GIVEN
        val fakeModel: RumEvent = forge.getForgery(RumEvent::class.java)
            .copy(event = forge.getForgery(ViewEvent::class.java))
        fakeNdkCrashDataDirectory.mkdirs()
        fakeOutputFile.createNewFile()
        whenever(mockOrchestrator.getWritableFile(any())).thenReturn(fakeOutputFile)
        whenever(mockRumSerializer.serialize(fakeModel)) doReturn fakeSerializedEvent

        // WHEN
        testedWriter.write(fakeModel)

        // THEN
        assertThat(fakeNdkCrashDataDirectory.listFiles()?.get(0)?.readText())
            .isEqualTo(fakeSerializedEvent)
    }

    @Test
    fun `M always cleanup the last_view_event W write() { ViewEvent }`(forge: Forge) {
        // GIVEN
        val fakeModel1: RumEvent = forge.getForgery(RumEvent::class.java)
            .copy(event = forge.getForgery(ViewEvent::class.java))
        val fakeSerializedEvent1 = forge.anAlphabeticalString()
        val fakeModel2: RumEvent = forge.getForgery(RumEvent::class.java)
            .copy(event = forge.getForgery(ViewEvent::class.java))
        val fakeSerializedEvent2 = forge.anAlphabeticalString()
        fakeOutputFile.createNewFile()
        whenever(mockOrchestrator.getWritableFile(any())).thenReturn(fakeOutputFile)
        whenever(mockRumSerializer.serialize(fakeModel1)) doReturn fakeSerializedEvent1
        whenever(mockRumSerializer.serialize(fakeModel1)) doReturn fakeSerializedEvent2

        // WHEN
        testedWriter.write(fakeModel1)
        testedWriter.write(fakeModel2)

        // THEN
        assertThat(fakeNdkCrashDataDirectory.listFiles()?.get(0)?.readText())
            .isEqualTo(fakeSerializedEvent2)
    }

    @Test
    fun `M create the ndk crash data directory if does not exists W write() { ViewEvent }`(
        forge: Forge
    ) {
        // GIVEN
        val fakeModel: RumEvent = forge.getForgery(RumEvent::class.java)
            .copy(event = forge.getForgery(ViewEvent::class.java))
        fakeOutputFile.createNewFile()
        whenever(mockOrchestrator.getWritableFile(any())).thenReturn(fakeOutputFile)
        whenever(mockRumSerializer.serialize(fakeModel)) doReturn fakeSerializedEvent

        // WHEN
        testedWriter.write(fakeModel)

        // THEN
        assertThat(fakeNdkCrashDataDirectory.listFiles()?.get(0)?.readText())
            .isEqualTo(fakeSerializedEvent)
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
        fakeOutputFile.createNewFile()
        whenever(mockOrchestrator.getWritableFile(any())).thenReturn(fakeOutputFile)

        // WHEN
        testedWriter.write(fakeModel)

        // THEN
        assertThat(fakeNdkCrashDataDirectory.listFiles()).isEmpty()
    }

    @Test
    fun `M do not notify the RumMonitor W write() { ViewEvent }`(
        @Forgery fakeModel: RumEvent,
        @Forgery viewEvent: ViewEvent
    ) {
        // GIVEN
        val rumEvent = fakeModel.copy(event = viewEvent)
        whenever(mockRumSerializer.serialize(rumEvent)) doReturn fakeSerializedEvent
        whenever(mockOrchestrator.getWritableFile(any())).thenReturn(fakeOutputFile)

        // WHEN
        testedWriter.write(rumEvent)

        // THEN
        verifyZeroInteractions(mockRumMonitor)
    }

    @Test
    fun `M notify the RumMonitor W write() { ActionEvent }`(
        @Forgery fakeModel: RumEvent,
        @Forgery actionEvent: ActionEvent
    ) {
        // GIVEN
        val rumEvent = fakeModel.copy(event = actionEvent)
        whenever(mockRumSerializer.serialize(rumEvent)) doReturn fakeSerializedEvent
        whenever(mockOrchestrator.getWritableFile(any())).thenReturn(fakeOutputFile)

        // WHEN
        testedWriter.write(rumEvent)

        // THEN
        verify(mockRumMonitor).eventSent(actionEvent.view.id, EventType.ACTION)
    }

    @Test
    fun `M do not notify the RumMonitor W write() { ActionEvent, write fails }`(
        @Forgery fakeModel: RumEvent,
        @Forgery actionEvent: ActionEvent
    ) {
        // GIVEN
        val rumEvent = fakeModel.copy(event = actionEvent)
        whenever(mockRumSerializer.serialize(rumEvent)) doReturn fakeSerializedEvent
        whenever(mockOrchestrator.getWritableFile(any())).thenReturn(null)

        // WHEN
        testedWriter.write(rumEvent)

        // THEN
        verifyZeroInteractions(mockRumMonitor)
    }

    @Test
    fun `M notify the RumMonitor W write() { ResourceEvent }`(
        @Forgery fakeModel: RumEvent,
        @Forgery resourceEvent: ResourceEvent
    ) {
        // GIVEN
        val rumEvent = fakeModel.copy(event = resourceEvent)
        whenever(mockRumSerializer.serialize(rumEvent)) doReturn fakeSerializedEvent
        whenever(mockOrchestrator.getWritableFile(any())).thenReturn(fakeOutputFile)

        // WHEN
        testedWriter.write(rumEvent)

        // THEN
        verify(mockRumMonitor).eventSent(resourceEvent.view.id, EventType.RESOURCE)
    }

    @Test
    fun `M do not notify the RumMonitor W write() { ResourceEvent, write fails }`(
        @Forgery fakeModel: RumEvent,
        @Forgery actionEvent: ResourceEvent
    ) {
        // GIVEN
        val rumEvent = fakeModel.copy(event = actionEvent)
        whenever(mockRumSerializer.serialize(rumEvent)) doReturn fakeSerializedEvent
        whenever(mockOrchestrator.getWritableFile(any())).thenReturn(null)

        // WHEN
        testedWriter.write(rumEvent)

        // THEN
        verifyZeroInteractions(mockRumMonitor)
    }

    @Test
    fun `M notify the RumMonitor W write() { ErrorEvent isCrash=false }`(
        @Forgery fakeModel: RumEvent,
        @Forgery errorEvent: ErrorEvent
    ) {
        // GIVEN
        val rumEvent = fakeModel.copy(
            event = errorEvent.copy(
                error = errorEvent.error.copy(isCrash = false)
            )
        )
        whenever(mockRumSerializer.serialize(rumEvent)) doReturn fakeSerializedEvent
        whenever(mockOrchestrator.getWritableFile(any())).thenReturn(fakeOutputFile)

        // WHEN
        testedWriter.write(rumEvent)

        // THEN
        verify(mockRumMonitor).eventSent(errorEvent.view.id, EventType.ERROR)
    }

    @Test
    fun `M notify the RumMonitor W write() { ErrorEvent isCrash=true }`(
        @Forgery fakeModel: RumEvent,
        @Forgery errorEvent: ErrorEvent
    ) {
        // GIVEN
        val rumEvent = fakeModel.copy(
            event = errorEvent.copy(
                error = errorEvent.error.copy(isCrash = true)
            )
        )
        whenever(mockRumSerializer.serialize(rumEvent)) doReturn fakeSerializedEvent
        whenever(mockOrchestrator.getWritableFile(any())).thenReturn(fakeOutputFile)

        // WHEN
        testedWriter.write(rumEvent)

        // THEN
        verify(mockRumMonitor).eventSent(errorEvent.view.id, EventType.CRASH)
    }

    @Test
    fun `M do not notify the RumMonitor W write() { ErrorEvent, write fails }`(
        @Forgery fakeModel: RumEvent,
        @Forgery actionEvent: ErrorEvent
    ) {
        // GIVEN
        val rumEvent = fakeModel.copy(event = actionEvent)
        whenever(mockRumSerializer.serialize(rumEvent)) doReturn fakeSerializedEvent
        whenever(mockOrchestrator.getWritableFile(any())).thenReturn(null)

        // WHEN
        testedWriter.write(rumEvent)

        // THEN
        verifyZeroInteractions(mockRumMonitor)
    }

    @Test
    fun `M notify the RumMonitor W write() { LongTaskEvent }`(
        @Forgery fakeModel: RumEvent,
        @Forgery longTaskEvent: LongTaskEvent
    ) {
        // GIVEN
        val rumEvent = fakeModel.copy(event = longTaskEvent)
        whenever(mockRumSerializer.serialize(rumEvent)) doReturn fakeSerializedEvent
        whenever(mockOrchestrator.getWritableFile(any())).thenReturn(fakeOutputFile)

        // WHEN
        testedWriter.write(rumEvent)

        // THEN
        verify(mockRumMonitor).eventSent(longTaskEvent.view.id, EventType.LONG_TASK)
    }

    @Test
    fun `M do not notify the RumMonitor W write() { LongTaskEvent, write fails }`(
        @Forgery fakeModel: RumEvent,
        @Forgery actionEvent: LongTaskEvent
    ) {
        // GIVEN
        val rumEvent = fakeModel.copy(event = actionEvent)
        whenever(mockRumSerializer.serialize(rumEvent)) doReturn fakeSerializedEvent
        whenever(mockOrchestrator.getWritableFile(any())).thenReturn(null)

        // WHEN
        testedWriter.write(rumEvent)

        // THEN
        verifyZeroInteractions(mockRumMonitor)
    }
}
