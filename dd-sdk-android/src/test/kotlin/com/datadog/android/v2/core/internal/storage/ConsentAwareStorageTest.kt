/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.core.internal.storage

import com.datadog.android.core.internal.persistence.file.FileHandler
import com.datadog.android.core.internal.persistence.file.FileOrchestrator
import com.datadog.android.core.internal.persistence.file.batch.BatchFileDataReader
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.BatchWriterListener
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.context.DatadogContext
import com.datadog.tools.unit.extensions.ProhibitLeavingStaticMocksExtension
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.File
import java.util.Locale
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.fail
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(ProhibitLeavingStaticMocksExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class ConsentAwareStorageTest {

    lateinit var testedStorage: Storage

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @Mock
    lateinit var mockPendingOrchestrator: FileOrchestrator

    @Mock
    lateinit var mockGrantedOrchestrator: FileOrchestrator

    @Mock
    lateinit var mockFileHandler: FileHandler

    @Mock
    lateinit var mockListener: BatchWriterListener

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @BeforeEach
    fun `set up`() {
        testedStorage = ConsentAwareStorage(
            mockGrantedOrchestrator,
            mockPendingOrchestrator,
            mockFileHandler,
            mockListener,
            mockInternalLogger
        )
    }

    @AfterEach
    fun `tear down`() {
    }

    // region writeCurrentBatch
    // TODO RUMM-2186 handle writing/updating batch metadata in separate file

    @Test
    fun `𝕄 provide writer 𝕎 writeCurrentBatch() {consent=granted}`(
        @StringForgery data: String,
        @StringForgery eventId: String,
        @Forgery file: File
    ) {
        // Given
        val serialized = data.toByteArray(Charsets.UTF_8)
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.GRANTED)
        whenever(mockGrantedOrchestrator.getWritableFile(serialized.size)) doReturn file

        // When
        testedStorage.writeCurrentBatch(sdkContext) {
            it.write(serialized, eventId, ByteArray(0))
        }

        // Then
        verify(mockGrantedOrchestrator).getWritableFile(serialized.size)
        verify(mockFileHandler).writeData(
            file,
            serialized,
            append = true
        )
        verifyNoMoreInteractions(
            mockGrantedOrchestrator,
            mockPendingOrchestrator,
            mockFileHandler
        )
    }

    @Test
    fun `𝕄 do nothing 𝕎 write() {consent=granted, empty array}`(
        @StringForgery eventId: String
    ) {
        // Given
        val serialized = ByteArray(0)
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.GRANTED)

        // When
        testedStorage.writeCurrentBatch(sdkContext) {
            it.write(serialized, eventId, ByteArray(0))
        }

        // Then
        verifyZeroInteractions(mockFileHandler, mockGrantedOrchestrator, mockPendingOrchestrator)
    }

    @Test
    fun `𝕄 notify success 𝕎 write() {consent = granted}`(
        @StringForgery data: String,
        @StringForgery eventId: String,
        @Forgery file: File
    ) {
        // Given
        val serialized = data.toByteArray(Charsets.UTF_8)
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.GRANTED)
        whenever(mockGrantedOrchestrator.getWritableFile(any())) doReturn file
        whenever(mockFileHandler.writeData(file, serialized, true)) doReturn true

        // When
        testedStorage.writeCurrentBatch(sdkContext) {
            it.write(serialized, eventId, ByteArray(0))
        }

        // Then
        verify(mockListener).onDataWritten(eventId)
        verifyNoMoreInteractions(mockListener)
    }

    @Test
    fun `𝕄 notify success 𝕎 write() {consent = granted, empty array}`(
        @StringForgery eventId: String
    ) {
        // Given
        val serialized = ByteArray(0)
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.GRANTED)

        // When
        testedStorage.writeCurrentBatch(sdkContext) {
            it.write(serialized, eventId, ByteArray(0))
        }

        // Then
        verify(mockListener).onDataWritten(eventId)
        verifyNoMoreInteractions(mockListener)
    }

    @Test
    fun `𝕄 notify failure 𝕎 write() {consent = granted, no available file}`(
        @StringForgery data: String,
        @StringForgery eventId: String,
        @Forgery file: File
    ) {
        // Given
        val serialized = data.toByteArray(Charsets.UTF_8)
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.GRANTED)
        whenever(mockGrantedOrchestrator.getWritableFile(any())) doReturn null

        // When
        testedStorage.writeCurrentBatch(sdkContext) {
            it.write(serialized, eventId, ByteArray(0))
        }

        // Then
        verify(mockListener).onDataWriteFailed(eventId)
        verifyNoMoreInteractions(mockListener)
    }

    @Test
    fun `𝕄 notify failure 𝕎 write() {consent = granted, write operation failed}`(
        @StringForgery data: String,
        @StringForgery eventId: String,
        @Forgery file: File
    ) {
        // Given
        val serialized = data.toByteArray(Charsets.UTF_8)
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.GRANTED)
        whenever(mockGrantedOrchestrator.getWritableFile(any())) doReturn file
        whenever(mockFileHandler.writeData(file, serialized, true)) doReturn false

        // When
        testedStorage.writeCurrentBatch(sdkContext) {
            it.write(serialized, eventId, ByteArray(0))
        }

        // Then
        verify(mockListener).onDataWriteFailed(eventId)
        verifyNoMoreInteractions(mockListener)
    }

    @Test
    fun `𝕄 provide writer 𝕎 writeCurrentBatch() {consent=pending}`(
        @StringForgery data: String,
        @StringForgery eventId: String,
        @Forgery file: File
    ) {
        // Given
        val serialized = data.toByteArray(Charsets.UTF_8)
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.PENDING)
        whenever(mockPendingOrchestrator.getWritableFile(serialized.size)) doReturn file

        // When
        testedStorage.writeCurrentBatch(sdkContext) {
            it.write(serialized, eventId, ByteArray(0))
        }

        // Then
        verify(mockPendingOrchestrator).getWritableFile(serialized.size)
        verify(mockFileHandler).writeData(
            file,
            serialized,
            append = true
        )
        verifyNoMoreInteractions(
            mockGrantedOrchestrator,
            mockPendingOrchestrator,
            mockFileHandler
        )
    }

    @Test
    fun `𝕄 do nothing 𝕎 write() {consent=pending, empty array}`(
        @StringForgery eventId: String
    ) {
        // Given
        val serialized = ByteArray(0)
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.PENDING)

        // When
        testedStorage.writeCurrentBatch(sdkContext) {
            it.write(serialized, eventId, ByteArray(0))
        }

        // Then
        verifyZeroInteractions(mockFileHandler, mockGrantedOrchestrator, mockPendingOrchestrator)
    }

    @Test
    fun `𝕄 notify success 𝕎 write() {consent = pending}`(
        @StringForgery data: String,
        @StringForgery eventId: String,
        @Forgery file: File
    ) {
        // Given
        val serialized = data.toByteArray(Charsets.UTF_8)
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.PENDING)
        whenever(mockPendingOrchestrator.getWritableFile(any())) doReturn file
        whenever(mockFileHandler.writeData(file, serialized, true)) doReturn true

        // When
        testedStorage.writeCurrentBatch(sdkContext) {
            it.write(serialized, eventId, ByteArray(0))
        }

        // Then
        verify(mockListener).onDataWritten(eventId)
        verifyNoMoreInteractions(mockListener)
    }

    @Test
    fun `𝕄 notify success 𝕎 write() {consent = pending, empty array}`(
        @StringForgery eventId: String
    ) {
        // Given
        val serialized = ByteArray(0)
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.PENDING)

        // When
        testedStorage.writeCurrentBatch(sdkContext) {
            it.write(serialized, eventId, ByteArray(0))
        }

        // Then
        verify(mockListener).onDataWritten(eventId)
        verifyNoMoreInteractions(mockListener)
    }

    @Test
    fun `𝕄 notify failure 𝕎 write() {consent = pending, no available file}`(
        @StringForgery data: String,
        @StringForgery eventId: String,
        @Forgery file: File
    ) {
        // Given
        val serialized = data.toByteArray(Charsets.UTF_8)
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.PENDING)
        whenever(mockPendingOrchestrator.getWritableFile(any())) doReturn null

        // When
        testedStorage.writeCurrentBatch(sdkContext) {
            it.write(serialized, eventId, ByteArray(0))
        }

        // Then
        verify(mockListener).onDataWriteFailed(eventId)
        verifyNoMoreInteractions(mockListener)
    }

    @Test
    fun `𝕄 notify failure 𝕎 write() {consent = pending, write operation failed}`(
        @StringForgery data: String,
        @StringForgery eventId: String,
        @Forgery file: File
    ) {
        // Given
        val serialized = data.toByteArray(Charsets.UTF_8)
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.PENDING)
        whenever(mockPendingOrchestrator.getWritableFile(any())) doReturn file
        whenever(mockFileHandler.writeData(file, serialized, true)) doReturn false

        // When
        testedStorage.writeCurrentBatch(sdkContext) {
            it.write(serialized, eventId, ByteArray(0))
        }

        // Then
        verify(mockListener).onDataWriteFailed(eventId)
        verifyNoMoreInteractions(mockListener)
    }

    @Test
    fun `𝕄 provide no op writer 𝕎 writeCurrentBatch() {consent=not_granted}`(
        @StringForgery data: String,
        @StringForgery eventId: String,
        @Forgery file: File
    ) {
        // Given
        val serialized = data.toByteArray(Charsets.UTF_8)
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.NOT_GRANTED)

        // When
        testedStorage.writeCurrentBatch(sdkContext) {
            it.write(serialized, eventId, ByteArray(0))
        }

        // Then
        verifyZeroInteractions(mockFileHandler, mockGrantedOrchestrator, mockPendingOrchestrator)
    }

    @Test
    fun `𝕄 do nothing 𝕎 write() {consent=not_granted, empty array}`(
        @StringForgery eventId: String
    ) {
        // Given
        val serialized = ByteArray(0)
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.NOT_GRANTED)

        // When
        testedStorage.writeCurrentBatch(sdkContext) {
            it.write(serialized, eventId, ByteArray(0))
        }

        // Then
        verifyZeroInteractions(mockFileHandler, mockGrantedOrchestrator, mockPendingOrchestrator)
    }

    @Test
    fun `𝕄 notify success 𝕎 write() {consent = not_granted}`(
        @StringForgery data: String,
        @StringForgery eventId: String,
        @Forgery file: File
    ) {
        // Given
        val serialized = data.toByteArray(Charsets.UTF_8)
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.NOT_GRANTED)
        whenever(mockGrantedOrchestrator.getWritableFile(any())) doReturn file
        whenever(mockFileHandler.writeData(file, serialized, true)) doReturn true

        // When
        testedStorage.writeCurrentBatch(sdkContext) {
            it.write(serialized, eventId, ByteArray(0))
        }

        // Then
        verify(mockListener).onDataWritten(eventId)
        verifyNoMoreInteractions(mockListener)
    }

    @Test
    fun `𝕄 notify success 𝕎 write() {consent = not_granted, empty array}`(
        @StringForgery eventId: String
    ) {
        // Given
        val serialized = ByteArray(0)
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.NOT_GRANTED)

        // When
        testedStorage.writeCurrentBatch(sdkContext) {
            it.write(serialized, eventId, ByteArray(0))
        }

        // Then
        verify(mockListener).onDataWritten(eventId)
        verifyNoMoreInteractions(mockListener)
    }

    // endregion

    // region readNextBatch

    @Test
    fun `𝕄 provide reader 𝕎 readNextBatch()`(
        @StringForgery data: List<String>,
        @Forgery file: File
    ) {
        // Given
        val mockData = data.map { it.toByteArray() }
        whenever(mockGrantedOrchestrator.getReadableFile(any())) doReturn file
        whenever(mockFileHandler.readData(file)) doReturn mockData

        // Whenever
        var readData: List<ByteArray>? = null
        testedStorage.readNextBatch(fakeDatadogContext) { id, reader ->
            readData = reader.read(id)
        }

        // Then
        assertThat(readData).isEqualTo(mockData)
    }

    @Test
    fun `𝕄 provide reader only once 𝕎 readNextBatch()`(
        @StringForgery data: List<String>,
        @Forgery file: File
    ) {
        // Given
        val mockData = data.map { it.toByteArray() }
        whenever(mockGrantedOrchestrator.getReadableFile(emptySet())) doReturn file
        whenever(mockGrantedOrchestrator.getReadableFile(setOf(file))) doReturn null
        whenever(mockFileHandler.readData(file)) doReturn mockData

        // Whenever
        var readData: List<ByteArray>? = null
        testedStorage.readNextBatch(fakeDatadogContext) { id, reader ->
            readData = reader.read(id)
        }
        testedStorage.readNextBatch(fakeDatadogContext) { _, _ ->
            fail { "Callback should not have been called again" }
        }

        // Then
        assertThat(readData).isEqualTo(mockData)
    }

    @Test
    fun `𝕄 do nothing 𝕎 readNextBatch() {no file}`() {
        // Given
        whenever(mockGrantedOrchestrator.getReadableFile(any())) doReturn null

        // When
        testedStorage.readNextBatch(fakeDatadogContext) { _, _ ->
            fail { "Callback should not have been called here" }
        }
    }

    // endregion

    // region confirmBatchRead

    @Test
    fun `𝕄 delete batch file 𝕎 readNextBatch()+confirmBatchRead() {delete=true}`(
        @Forgery file: File
    ) {
        // Given
        whenever(mockGrantedOrchestrator.getReadableFile(emptySet())) doReturn file
        whenever(mockFileHandler.delete(file)) doReturn true

        // When
        var batchId: BatchId? = null
        testedStorage.readNextBatch(fakeDatadogContext) { id, _ ->
            batchId = id
        }
        testedStorage.confirmBatchRead(batchId!!) { confirm ->
            confirm.markAsRead(true)
        }

        // Then
        verify(mockFileHandler).delete(file)
    }

    @Test
    fun `𝕄 read batch twice if released 𝕎 readNextBatch()+confirmBatchRead() {delete=false}`(
        @Forgery file: File
    ) {
        // Given
        whenever(mockGrantedOrchestrator.getReadableFile(emptySet())) doReturn file
        whenever(mockGrantedOrchestrator.getReadableFile(setOf(file))) doReturn null

        // When
        var batchId1: BatchId? = null
        var batchId2: BatchId? = null
        testedStorage.readNextBatch(fakeDatadogContext) { id, _ ->
            batchId1 = id
        }
        testedStorage.readNextBatch(fakeDatadogContext) { _, _ ->
            fail { "Callback should not have been called here" }
        }
        testedStorage.confirmBatchRead(batchId1!!) { confirm ->
            confirm.markAsRead(false)
        }
        testedStorage.readNextBatch(fakeDatadogContext) { id, _ ->
            batchId2 = id
        }

        // Then
        verify(mockFileHandler, never()).delete(file)
        assertThat(batchId1).isEqualTo(batchId2)
    }

    @Test
    fun `𝕄 keep batch file locked 𝕎 readNextBatch()+confirmBatchRead() {delete=true, != batchId}`(
        @Forgery file: File,
        @Forgery anotherFile: File
    ) {
        // Given
        whenever(mockGrantedOrchestrator.getReadableFile(emptySet())) doReturn file

        // When
        var batchId: BatchId? = null
        testedStorage.readNextBatch(fakeDatadogContext) { id, _ ->
            batchId = id
        }
        testedStorage.confirmBatchRead(BatchId.fromFile(anotherFile)) { confirm ->
            confirm.markAsRead(true)
        }
        testedStorage.readNextBatch(fakeDatadogContext) { _, _ ->
            fail { "Callback should not have been called here" }
        }

        // Then
        verify(mockFileHandler, never()).delete(file)
    }

    @Test
    fun `𝕄 warn 𝕎 readNextBatch() + confirmBatchRead() {delete fails}`(
        @Forgery file: File
    ) {
        // Given
        whenever(mockGrantedOrchestrator.getReadableFile(emptySet())) doReturn file
        whenever(mockFileHandler.delete(file)) doReturn false

        // When
        var batchId: BatchId? = null
        testedStorage.readNextBatch(fakeDatadogContext) { id, _ ->
            batchId = id
        }
        testedStorage.confirmBatchRead(batchId!!) { confirm ->
            confirm.markAsRead(true)
        }

        // Then
        verify(mockFileHandler).delete(file)
        verify(mockInternalLogger).log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.MAINTAINER,
            BatchFileDataReader.WARNING_DELETE_FAILED.format(Locale.US, file.path),
            null,
            emptyMap()
        )
    }

    // endregion
}
