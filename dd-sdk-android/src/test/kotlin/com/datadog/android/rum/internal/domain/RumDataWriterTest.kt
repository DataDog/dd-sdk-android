/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain

import com.datadog.android.core.internal.persistence.Serializer
import com.datadog.android.core.internal.persistence.file.FileWriter
import com.datadog.android.rum.internal.monitor.StorageEvent
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.EventBatchWriter
import com.datadog.android.v2.api.InternalLogger
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.datadog.tools.unit.forge.aThrowable
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.io.File
import java.util.Locale

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumDataWriterTest {

    lateinit var testedWriter: RumDataWriter

    @Mock
    lateinit var mockSerializer: Serializer<Any>

    @Mock
    lateinit var mockFileWriter: FileWriter

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockEventBatchWriter: EventBatchWriter

    @StringForgery
    lateinit var fakeSerializedEvent: String
    lateinit var fakeSerializedData: ByteArray

    @Mock
    lateinit var fakeLastViewEventFile: File

    @BeforeEach
    fun `set up`() {
        fakeSerializedData = fakeSerializedEvent.toByteArray(Charsets.UTF_8)

        whenever(mockEventBatchWriter.write(fakeSerializedData, null)) doReturn true

        testedWriter = RumDataWriter(
            mockSerializer,
            mockFileWriter,
            mockInternalLogger,
            fakeLastViewEventFile
        )
    }

    @Test
    fun `𝕄 write data 𝕎 write()`(
        forge: Forge
    ) {
        // Given
        val fakeEvent = forge.anElementFrom(
            forge.getForgery(ViewEvent::class.java),
            forge.getForgery(ActionEvent::class.java),
            forge.getForgery(ResourceEvent::class.java),
            forge.getForgery(LongTaskEvent::class.java),
            forge.getForgery(ErrorEvent::class.java)
        )

        whenever(mockSerializer.serialize(fakeEvent)) doReturn fakeSerializedEvent

        // When
        val result = testedWriter.write(mockEventBatchWriter, fakeEvent)

        // Then
        assertThat(result).isTrue

        verify(mockEventBatchWriter).write(
            fakeSerializedData,
            null
        )
    }

    @Test
    fun `𝕄 not write data 𝕎 write() { exception during serialization }`(
        forge: Forge
    ) {
        // Given
        val fakeEvent = forge.anElementFrom(
            forge.getForgery(ViewEvent::class.java),
            forge.getForgery(ActionEvent::class.java),
            forge.getForgery(ResourceEvent::class.java),
            forge.getForgery(LongTaskEvent::class.java),
            forge.getForgery(ErrorEvent::class.java)
        )

        whenever(mockSerializer.serialize(fakeEvent)) doThrow forge.aThrowable()

        // When
        val result = testedWriter.write(mockEventBatchWriter, fakeEvent)

        // Then
        assertThat(result).isFalse

        verifyZeroInteractions(mockEventBatchWriter)
    }

    @Test
    fun `𝕄 return false 𝕎 write() { data was not written }`(
        forge: Forge
    ) {
        // Given
        val fakeEvent = forge.anElementFrom(
            forge.getForgery(ViewEvent::class.java),
            forge.getForgery(ActionEvent::class.java),
            forge.getForgery(ResourceEvent::class.java),
            forge.getForgery(LongTaskEvent::class.java),
            forge.getForgery(ErrorEvent::class.java)
        )

        whenever(mockEventBatchWriter.write(fakeSerializedData, null)) doReturn false

        // When
        val result = testedWriter.write(mockEventBatchWriter, fakeEvent)

        // Then
        assertThat(result).isFalse
    }

    // region onDataWritten

    @Test
    fun `𝕄 do not notify the RumMonitor 𝕎 onDataWritten() { ViewEvent }`(
        @Forgery viewEvent: ViewEvent
    ) {
        // When
        testedWriter.onDataWritten(viewEvent, fakeSerializedData)

        // Then
        verifyZeroInteractions(rumMonitor.mockInstance)
    }

    @Test
    fun `𝕄 persist the event into the NDK crash folder 𝕎 onDataWritten(){ViewEvent+dir exists}`(
        @Forgery viewEvent: ViewEvent
    ) {
        // Given
        val ndkReportsFolder = mock<File>()
        whenever(ndkReportsFolder.exists()) doReturn true
        whenever(fakeLastViewEventFile.parentFile) doReturn ndkReportsFolder

        // When
        testedWriter.onDataWritten(viewEvent, fakeSerializedData)

        // Then
        verify(mockFileWriter)
            .writeData(fakeLastViewEventFile, fakeSerializedData, false)
        verifyZeroInteractions(mockInternalLogger)
    }

    @Test
    fun `𝕄 log info when writing last view event 𝕎 onDataWritten(){ ViewEvent+no crash dir }`(
        @Forgery viewEvent: ViewEvent
    ) {
        // Given
        whenever(fakeLastViewEventFile.parentFile) doReturn null

        // When
        testedWriter.onDataWritten(viewEvent, fakeSerializedData)

        // Then
        verifyZeroInteractions(mockFileWriter)
        verify(mockInternalLogger).log(
            InternalLogger.Level.INFO,
            InternalLogger.Target.MAINTAINER,
            RumDataWriter.LAST_VIEW_EVENT_DIR_MISSING_MESSAGE.format(
                Locale.US,
                fakeLastViewEventFile.parent
            )
        )
    }

    @Test
    fun `𝕄 notify the RumMonitor 𝕎 onDataWritten() { ActionEvent }`(
        @Forgery actionEvent: ActionEvent
    ) {
        // When
        testedWriter.onDataWritten(actionEvent, fakeSerializedData)

        // Then
        verify(rumMonitor.mockInstance).eventSent(
            actionEvent.view.id,
            StorageEvent.Action(frustrationCount = actionEvent.action.frustration?.type?.size ?: 0)
        )
        verifyZeroInteractions(mockFileWriter)
    }

    @Test
    fun `𝕄 notify the RumMonitor 𝕎 onDataWritten() { ResourceEvent }`(
        @Forgery resourceEvent: ResourceEvent
    ) {
        // When
        testedWriter.onDataWritten(resourceEvent, fakeSerializedData)

        // Then
        verify(rumMonitor.mockInstance).eventSent(resourceEvent.view.id, StorageEvent.Resource)
        verifyZeroInteractions(mockFileWriter)
    }

    @Test
    fun `𝕄 notify the RumMonitor 𝕎 onDataWritten() { ErrorEvent isCrash=false }`(
        @Forgery fakeEvent: ErrorEvent
    ) {
        // Given
        val errorEvent = fakeEvent.copy(error = fakeEvent.error.copy(isCrash = false))

        // When
        testedWriter.onDataWritten(errorEvent, fakeSerializedData)

        // Then
        verify(rumMonitor.mockInstance).eventSent(fakeEvent.view.id, StorageEvent.Error)
        verifyZeroInteractions(mockFileWriter)
    }

    @Test
    fun `𝕄 not notify the RumMonitor 𝕎 onDataWritten() { ErrorEvent isCrash=true }`(
        @Forgery fakeEvent: ErrorEvent
    ) {
        // Given
        val errorEvent = fakeEvent.copy(error = fakeEvent.error.copy(isCrash = true))

        // When
        testedWriter.onDataWritten(errorEvent, fakeSerializedData)

        // Then
        verify(rumMonitor.mockInstance, never()).eventSent(eq(fakeEvent.view.id), any())
        verifyZeroInteractions(mockFileWriter)
    }

    @Test
    fun `𝕄 notify the RumMonitor 𝕎 onDataWritten() { LongTaskEvent }`(
        @Forgery fakeEvent: LongTaskEvent
    ) {
        // Given
        val longTaskEvent = fakeEvent.copy(
            longTask = LongTaskEvent.LongTask(
                id = fakeEvent.longTask.id,
                duration = fakeEvent.longTask.duration,
                isFrozenFrame = false
            )
        )

        // When
        testedWriter.onDataWritten(longTaskEvent, fakeSerializedData)

        // Then
        verify(rumMonitor.mockInstance).eventSent(longTaskEvent.view.id, StorageEvent.LongTask)
        verifyZeroInteractions(mockFileWriter)
    }

    @Test
    fun `𝕄 notify the RumMonitor 𝕎 onDataWritten() { FrozenFrame Event }`(
        @Forgery fakeEvent: LongTaskEvent
    ) {
        // Given
        val frozenFrameEvent = fakeEvent.copy(
            longTask = LongTaskEvent.LongTask(
                id = fakeEvent.longTask.id,
                duration = fakeEvent.longTask.duration,
                isFrozenFrame = true
            )
        )

        // When
        testedWriter.onDataWritten(frozenFrameEvent, fakeSerializedData)

        // Then
        verify(rumMonitor.mockInstance).eventSent(
            frozenFrameEvent.view.id,
            StorageEvent.FrozenFrame
        )
        verifyZeroInteractions(mockFileWriter)
    }

    // endregion

    companion object {
        val rumMonitor = GlobalRumMonitorTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(rumMonitor)
        }
    }
}
