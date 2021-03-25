/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain

import com.datadog.android.core.internal.persistence.PayloadDecoration
import com.datadog.android.core.internal.persistence.Serializer
import com.datadog.android.core.internal.persistence.file.FileHandler
import com.datadog.android.core.internal.persistence.file.FileOrchestrator
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
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.File
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
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
internal class RumDataWriterTest {

    lateinit var testedWriter: RumDataWriter

    @Mock
    lateinit var mockSerializer: Serializer<RumEvent>

    @Mock
    lateinit var mockOrchestrator: FileOrchestrator

    @Mock
    lateinit var mockFileHandler: FileHandler

    @Mock
    lateinit var mockRumMonitor: AdvancedRumMonitor

    @Forgery
    lateinit var fakeDecoration: PayloadDecoration

    @StringForgery
    lateinit var fakeSerializedEvent: String
    lateinit var fakeSerializedData: ByteArray

    @Forgery
    lateinit var fakeLastViewEventFile: File

    @BeforeEach
    fun `set up`() {
        GlobalRum.monitor = mockRumMonitor
        GlobalRum.isRegistered.set(true)

        fakeSerializedData = fakeSerializedEvent.toByteArray(Charsets.UTF_8)

        testedWriter = RumDataWriter(
            mockOrchestrator,
            mockSerializer,
            fakeDecoration,
            mockFileHandler,
            fakeLastViewEventFile
        )
    }

    @AfterEach
    fun `tear down`() {
        GlobalRum.monitor = NoOpRumMonitor()
        GlobalRum.isRegistered.set(false)
    }

    @Test
    fun `𝕄 do not notify the RumMonitor 𝕎 onDataWritten() { ViewEvent }`(
        @Forgery fakeModel: RumEvent,
        @Forgery viewEvent: ViewEvent
    ) {
        // Given
        val rumEvent = fakeModel.copy(event = viewEvent)

        // When
        testedWriter.onDataWritten(rumEvent, fakeSerializedData)

        // Then
        verifyZeroInteractions(mockRumMonitor)
    }

    @Test
    fun `𝕄 persist the event into the NDK crash folder 𝕎 onDataWritten() { ViewEvent }`(
        @Forgery fakeModel: RumEvent,
        @Forgery viewEvent: ViewEvent
    ) {
        // Given
        val rumEvent = fakeModel.copy(event = viewEvent)

        // When
        testedWriter.onDataWritten(rumEvent, fakeSerializedData)

        // Then
        verify(mockFileHandler)
            .writeData(fakeLastViewEventFile, fakeSerializedData)
    }

    @Test
    fun `𝕄 do not notify the RumMonitor 𝕎 onDataWriteFailed() { ViewEvent }`(
        @Forgery fakeModel: RumEvent,
        @Forgery actionEvent: ViewEvent
    ) {
        // Given
        val rumEvent = fakeModel.copy(event = actionEvent)

        // When
        testedWriter.onDataWriteFailed(rumEvent)

        // Then
        verifyZeroInteractions(mockRumMonitor, mockFileHandler)
    }

    @Test
    fun `𝕄 notify the RumMonitor 𝕎 onDataWritten() { ActionEvent }`(
        @Forgery fakeModel: RumEvent,
        @Forgery actionEvent: ActionEvent
    ) {
        // Given
        val rumEvent = fakeModel.copy(event = actionEvent)

        // When
        testedWriter.onDataWritten(rumEvent, fakeSerializedData)

        // Then
        verify(mockRumMonitor).eventSent(actionEvent.view.id, EventType.ACTION)
        verifyZeroInteractions(mockFileHandler)
    }

    @Test
    fun `𝕄 do not notify the RumMonitor 𝕎 onDataWriteFailed() { ActionEvent }`(
        @Forgery fakeModel: RumEvent,
        @Forgery actionEvent: ActionEvent
    ) {
        // Given
        val rumEvent = fakeModel.copy(event = actionEvent)

        // When
        testedWriter.onDataWriteFailed(rumEvent)

        // Then
        verifyZeroInteractions(mockRumMonitor, mockFileHandler)
    }

    @Test
    fun `𝕄 notify the RumMonitor 𝕎 onDataWritten() { ResourceEvent }`(
        @Forgery fakeModel: RumEvent,
        @Forgery resourceEvent: ResourceEvent
    ) {
        // Given
        val rumEvent = fakeModel.copy(event = resourceEvent)

        // When
        testedWriter.onDataWritten(rumEvent, fakeSerializedData)

        // Then
        verify(mockRumMonitor).eventSent(resourceEvent.view.id, EventType.RESOURCE)
        verifyZeroInteractions(mockFileHandler)
    }

    @Test
    fun `𝕄 do not notify the RumMonitor 𝕎 onDataWriteFailed() { ResourceEvent }`(
        @Forgery fakeModel: RumEvent,
        @Forgery actionEvent: ResourceEvent
    ) {
        // Given
        val rumEvent = fakeModel.copy(event = actionEvent)

        // When
        testedWriter.onDataWriteFailed(rumEvent)

        // Then
        verifyZeroInteractions(mockRumMonitor, mockFileHandler)
    }

    @Test
    fun `𝕄 notify the RumMonitor 𝕎 onDataWritten() { ErrorEvent isCrash=false }`(
        @Forgery fakeModel: RumEvent,
        @Forgery errorEvent: ErrorEvent
    ) {
        // Given
        val rumEvent = fakeModel.copy(
            event = errorEvent.copy(
                error = errorEvent.error.copy(isCrash = false)
            )
        )

        // When
        testedWriter.onDataWritten(rumEvent, fakeSerializedData)

        // Then
        verify(mockRumMonitor).eventSent(errorEvent.view.id, EventType.ERROR)
        verifyZeroInteractions(mockFileHandler)
    }

    @Test
    fun `𝕄 notify the RumMonitor 𝕎 onDataWritten() { ErrorEvent isCrash=true }`(
        @Forgery fakeModel: RumEvent,
        @Forgery errorEvent: ErrorEvent
    ) {
        // Given
        val rumEvent = fakeModel.copy(
            event = errorEvent.copy(
                error = errorEvent.error.copy(isCrash = true)
            )
        )

        // When
        testedWriter.onDataWritten(rumEvent, fakeSerializedData)

        // Then
        verify(mockRumMonitor).eventSent(errorEvent.view.id, EventType.CRASH)
        verifyZeroInteractions(mockFileHandler)
    }

    @Test
    fun `𝕄 do not notify the RumMonitor 𝕎 onDataWriteFailed() { ErrorEvent }`(
        @Forgery fakeModel: RumEvent,
        @Forgery actionEvent: ErrorEvent
    ) {
        // Given
        val rumEvent = fakeModel.copy(event = actionEvent)

        // When
        testedWriter.onDataWriteFailed(rumEvent)

        // Then
        verifyZeroInteractions(mockRumMonitor, mockFileHandler)
    }

    @Test
    fun `𝕄 notify the RumMonitor 𝕎 onDataWritten() { LongTaskEvent }`(
        @Forgery fakeModel: RumEvent,
        @Forgery longTaskEvent: LongTaskEvent
    ) {
        // Given
        val rumEvent = fakeModel.copy(event = longTaskEvent)

        // When
        testedWriter.onDataWritten(rumEvent, fakeSerializedData)

        // Then
        verify(mockRumMonitor).eventSent(longTaskEvent.view.id, EventType.LONG_TASK)
        verifyZeroInteractions(mockFileHandler)
    }

    @Test
    fun `𝕄 do not notify the RumMonitor 𝕎 onDataWriteFailed() { LongTaskEvent }`(
        @Forgery fakeModel: RumEvent,
        @Forgery actionEvent: LongTaskEvent
    ) {
        // Given
        val rumEvent = fakeModel.copy(event = actionEvent)

        // When
        testedWriter.onDataWriteFailed(rumEvent)

        // Then
        verifyZeroInteractions(mockRumMonitor, mockFileHandler)
    }
}
