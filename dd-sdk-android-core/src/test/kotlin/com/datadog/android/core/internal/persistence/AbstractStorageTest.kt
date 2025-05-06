/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.api.storage.FeatureStorageConfiguration
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.core.internal.metrics.RemovalReason
import com.datadog.android.core.internal.privacy.ConsentProvider
import com.datadog.android.core.persistence.PersistenceStrategy
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.BoolForgery
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
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class AbstractStorageTest {

    private lateinit var testedStorage: AbstractStorage

    @Mock
    lateinit var mockPersistenceStrategyFactory: PersistenceStrategy.Factory

    @Mock
    lateinit var mockGrantedPersistenceStrategy: PersistenceStrategy

    @Mock
    lateinit var mockPendingPersistenceStrategy: PersistenceStrategy

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockConsentProvider: ConsentProvider

    @StringForgery
    lateinit var fakeSdkInstanceId: String

    @StringForgery
    lateinit var fakeFeatureName: String

    @Forgery
    lateinit var fakeStorageConfiguration: FeatureStorageConfiguration

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @Forgery
    lateinit var fakeEventType: EventType

    @BeforeEach
    fun `set up`() {
        whenever(mockPersistenceStrategyFactory.create(argThat { contains("/GRANTED") }, any(), any()))
            .doReturn(mockGrantedPersistenceStrategy)
        whenever(mockPersistenceStrategyFactory.create(argThat { contains("/PENDING") }, any(), any()))
            .doReturn(mockPendingPersistenceStrategy)

        testedStorage = AbstractStorage(
            fakeSdkInstanceId,
            fakeFeatureName,
            mockPersistenceStrategyFactory,
            FakeSameThreadExecutorService(),
            mockInternalLogger,
            fakeStorageConfiguration,
            mockConsentProvider
        )
    }

    // region Storage.getEventWriteScope

    @Test
    fun `M provide writer W getEventWriteScope()+invoke() {consent=granted, batchMetadata=null}`(
        @BoolForgery fakeResult: Boolean,
        @Forgery fakeBatchEvent: RawBatchEvent
    ) {
        // Given
        fakeDatadogContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.GRANTED)
        val mockWriteCallback = mock<(EventBatchWriter) -> Unit>()
        whenever(mockGrantedPersistenceStrategy.write(any(), anyOrNull(), any())) doReturn fakeResult
        var result: Boolean? = null
        whenever(mockWriteCallback.invoke(any())) doAnswer {
            result = it.getArgument<EventBatchWriter>(0)
                .write(fakeBatchEvent, null, fakeEventType)
        }

        // When
        testedStorage.getEventWriteScope(fakeDatadogContext)
            .invoke(mockWriteCallback)

        // Then
        assertThat(result).isEqualTo(fakeResult)
        verify(mockGrantedPersistenceStrategy).write(fakeBatchEvent, null, fakeEventType)
        verifyNoMoreInteractions(
            mockGrantedPersistenceStrategy,
            mockPendingPersistenceStrategy,
            mockInternalLogger
        )
    }

    @Test
    fun `M provide writer W getEventWriteScope()+invoke() {consent=granted, batchMetadata!=null}`(
        @BoolForgery fakeResult: Boolean,
        @Forgery fakeBatchEvent: RawBatchEvent,
        @StringForgery fakeBatchMetadata: String
    ) {
        // Given
        fakeDatadogContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.GRANTED)
        val mockWriteCallback = mock<(EventBatchWriter) -> Unit>()
        val batchMetadata = fakeBatchMetadata.toByteArray()
        whenever(mockGrantedPersistenceStrategy.write(any(), anyOrNull(), any())) doReturn fakeResult
        var result: Boolean? = null
        whenever(mockWriteCallback.invoke(any())) doAnswer {
            result = it.getArgument<EventBatchWriter>(0)
                .write(fakeBatchEvent, batchMetadata, fakeEventType)
        }

        // When
        testedStorage.getEventWriteScope(fakeDatadogContext)
            .invoke(mockWriteCallback)

        // Then
        assertThat(result).isEqualTo(fakeResult)
        verify(mockGrantedPersistenceStrategy).write(fakeBatchEvent, batchMetadata, fakeEventType)
        verifyNoMoreInteractions(
            mockGrantedPersistenceStrategy,
            mockPendingPersistenceStrategy,
            mockInternalLogger
        )
    }

    @Test
    fun `M provide writer W getEventWriteScope()+currentMetadata() {consent=granted, batchMetadata=null}`() {
        // Given
        fakeDatadogContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.GRANTED)
        whenever(mockGrantedPersistenceStrategy.currentMetadata()) doReturn null
        val mockWriteCallback = mock<(EventBatchWriter) -> Unit>()
        var resultMetadata: ByteArray? = null
        whenever(mockWriteCallback.invoke(any())) doAnswer {
            resultMetadata = it.getArgument<EventBatchWriter>(0).currentMetadata()
        }

        // When
        testedStorage.getEventWriteScope(fakeDatadogContext)
            .invoke(mockWriteCallback)

        // Then
        assertThat(resultMetadata).isNull()
        verify(mockGrantedPersistenceStrategy).currentMetadata()
        verifyNoMoreInteractions(
            mockGrantedPersistenceStrategy,
            mockPendingPersistenceStrategy,
            mockInternalLogger
        )
    }

    @Test
    fun `M provide writer W getEventWriteScope()+currentMetadata() {consent=granted, batchMetadata!=null}`(
        @StringForgery fakeBatchMetadata: String
    ) {
        // Given
        fakeDatadogContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.GRANTED)
        val mockWriteCallback = mock<(EventBatchWriter) -> Unit>()
        val batchMetadata = fakeBatchMetadata.toByteArray()
        whenever(mockGrantedPersistenceStrategy.currentMetadata()) doReturn batchMetadata
        var resultMetadata: ByteArray? = null
        whenever(mockWriteCallback.invoke(any())) doAnswer {
            resultMetadata = it.getArgument<EventBatchWriter>(0).currentMetadata()
        }

        // When
        testedStorage.getEventWriteScope(fakeDatadogContext)
            .invoke(mockWriteCallback)

        // Then
        assertThat(resultMetadata).isEqualTo(batchMetadata)
        verify(mockGrantedPersistenceStrategy).currentMetadata()
        verifyNoMoreInteractions(
            mockGrantedPersistenceStrategy,
            mockPendingPersistenceStrategy,
            mockInternalLogger
        )
    }

    @Test
    fun `M provide writer W getEventWriteScope()+write() {consent=pending, batchMetadata=null}`(
        @BoolForgery fakeResult: Boolean,
        @Forgery fakeBatchEvent: RawBatchEvent
    ) {
        // Given
        fakeDatadogContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.PENDING)
        val mockWriteCallback = mock<(EventBatchWriter) -> Unit>()
        whenever(mockPendingPersistenceStrategy.write(any(), anyOrNull(), any())) doReturn fakeResult
        var result: Boolean? = null
        whenever(mockWriteCallback.invoke(any())) doAnswer {
            result = it.getArgument<EventBatchWriter>(0).write(fakeBatchEvent, null, fakeEventType)
        }

        // When
        testedStorage.getEventWriteScope(fakeDatadogContext)
            .invoke(mockWriteCallback)

        // Then
        assertThat(result).isEqualTo(fakeResult)
        verify(mockPendingPersistenceStrategy).write(fakeBatchEvent, null, fakeEventType)
        verifyNoMoreInteractions(
            mockGrantedPersistenceStrategy,
            mockPendingPersistenceStrategy,
            mockInternalLogger
        )
    }

    @Test
    fun `M provide writer W getEventWriteScope()+write() {consent=pending, batchMetadata!=null}`(
        @BoolForgery fakeResult: Boolean,
        @Forgery fakeBatchEvent: RawBatchEvent,
        @StringForgery fakeBatchMetadata: String
    ) {
        // Given
        fakeDatadogContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.PENDING)
        val mockWriteCallback = mock<(EventBatchWriter) -> Unit>()
        val batchMetadata = fakeBatchMetadata.toByteArray()
        whenever(mockPendingPersistenceStrategy.write(any(), anyOrNull(), any())) doReturn fakeResult
        var result: Boolean? = null
        whenever(mockWriteCallback.invoke(any())) doAnswer {
            result = it.getArgument<EventBatchWriter>(0).write(fakeBatchEvent, batchMetadata, fakeEventType)
        }

        // When
        testedStorage.getEventWriteScope(fakeDatadogContext)
            .invoke(mockWriteCallback)

        // Then
        assertThat(result).isEqualTo(fakeResult)
        verify(mockPendingPersistenceStrategy).write(fakeBatchEvent, batchMetadata, fakeEventType)
        verifyNoMoreInteractions(
            mockGrantedPersistenceStrategy,
            mockPendingPersistenceStrategy,
            mockInternalLogger
        )
    }

    @Test
    fun `M provide writer W getEventWriteScope()+currentMetadata() {consent=pending, batchMetadata=null}`() {
        // Given
        fakeDatadogContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.PENDING)
        val mockWriteCallback = mock<(EventBatchWriter) -> Unit>()
        var resultMetadata: ByteArray? = null
        whenever(mockWriteCallback.invoke(any())) doAnswer {
            resultMetadata = it.getArgument<EventBatchWriter>(0).currentMetadata()
        }
        whenever(mockPendingPersistenceStrategy.currentMetadata()) doReturn null

        // When
        testedStorage.getEventWriteScope(fakeDatadogContext)
            .invoke(mockWriteCallback)

        // Then
        assertThat(resultMetadata).isNull()
        verify(mockPendingPersistenceStrategy).currentMetadata()
        verifyNoMoreInteractions(
            mockGrantedPersistenceStrategy,
            mockPendingPersistenceStrategy,
            mockInternalLogger
        )
    }

    @Test
    fun `M provide writer W getEventWriteScope()+currentMetadata() {consent=pending, batchMetadata!=null}`(
        @StringForgery fakeBatchMetadata: String
    ) {
        // Given
        fakeDatadogContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.PENDING)
        val mockWriteCallback = mock<(EventBatchWriter) -> Unit>()
        val batchMetadata = fakeBatchMetadata.toByteArray()
        var resultMetadata: ByteArray? = null
        whenever(mockWriteCallback.invoke(any())) doAnswer {
            resultMetadata = it.getArgument<EventBatchWriter>(0).currentMetadata()
        }
        whenever(mockPendingPersistenceStrategy.currentMetadata()) doReturn batchMetadata

        // When
        testedStorage.getEventWriteScope(fakeDatadogContext)
            .invoke(mockWriteCallback)

        // Then
        assertThat(resultMetadata).isEqualTo(batchMetadata)
        verify(mockPendingPersistenceStrategy).currentMetadata()
        verifyNoMoreInteractions(
            mockGrantedPersistenceStrategy,
            mockPendingPersistenceStrategy,
            mockInternalLogger
        )
    }

    @Test
    fun `M provide no-op writer W getEventWriteScope()+write() {consent=not_granted}`(
        @Forgery fakeBatchEvent: RawBatchEvent,
        @StringForgery fakeBatchMetadata: String
    ) {
        // Given
        fakeDatadogContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.NOT_GRANTED)
        val mockWriteCallback = mock<(EventBatchWriter) -> Unit>()
        val batchMetadata = fakeBatchMetadata.toByteArray()
        var result: Boolean? = null
        whenever(mockWriteCallback.invoke(any())) doAnswer {
            result = it.getArgument<EventBatchWriter>(0).write(fakeBatchEvent, batchMetadata, fakeEventType)
        }

        // When
        testedStorage.getEventWriteScope(fakeDatadogContext)
            .invoke(mockWriteCallback)

        // Then
        assertThat(result).isFalse()
        verifyNoInteractions(
            mockGrantedPersistenceStrategy,
            mockPendingPersistenceStrategy,
            mockInternalLogger
        )
    }

    @Test
    fun `M provide no-op writer W getEventWriteScope()+currentMetadata() {consent=not_granted}`() {
        // Given
        fakeDatadogContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.NOT_GRANTED)
        val mockWriteCallback = mock<(EventBatchWriter) -> Unit>()
        var resultMetadata: ByteArray? = null
        whenever(mockWriteCallback.invoke(any())) doAnswer {
            resultMetadata = it.getArgument<EventBatchWriter>(0).currentMetadata()
        }

        // When
        testedStorage.getEventWriteScope(fakeDatadogContext)
            .invoke(mockWriteCallback)

        // Then
        assertThat(resultMetadata).isNull()
        verifyNoMoreInteractions(
            mockGrantedPersistenceStrategy,
            mockPendingPersistenceStrategy,
            mockInternalLogger
        )
    }

    // endregion

    // region Storage.readNextBatch

    @Test
    fun `M provide null W readNextBatch() {no batch}`() {
        // Given
        whenever(mockGrantedPersistenceStrategy.lockAndReadNext()) doReturn null

        // Then
        assertThat(testedStorage.readNextBatch()).isNull()
        verify(mockGrantedPersistenceStrategy).lockAndReadNext()
        verifyNoMoreInteractions(
            mockGrantedPersistenceStrategy,
            mockInternalLogger
        )
    }

    @Test
    fun `M provide BatchData W readNextBatch() {with batch}`(
        @Forgery fakeBatch: PersistenceStrategy.Batch
    ) {
        // Given
        whenever(mockGrantedPersistenceStrategy.lockAndReadNext()) doReturn fakeBatch

        // When
        val batchData = testedStorage.readNextBatch()

        // Then
        assertThat(batchData).isNotNull
        assertThat(batchData?.id).isEqualTo(BatchId(fakeBatch.batchId))
        assertThat(batchData?.data).isEqualTo(fakeBatch.events)
        assertThat(batchData?.metadata).isEqualTo(fakeBatch.metadata)
    }

    @Test
    fun `M return null W readNextBatch() {no batch}`() {
        // Given
        whenever(mockGrantedPersistenceStrategy.lockAndReadNext()) doReturn null

        // Then
        assertThat(testedStorage.readNextBatch()).isNull()
    }

    // endregion

    // region Storage.readNextBatch

    @Test
    fun `M delete batch W confirmBatchRead() {delete=true}`(
        @StringForgery fakeBatchId: String,
        @Forgery fakeRemovalReason: RemovalReason
    ) {
        // When
        testedStorage.confirmBatchRead(BatchId(fakeBatchId), fakeRemovalReason, true)

        // Then
        verify(mockGrantedPersistenceStrategy).unlockAndDelete(fakeBatchId)
        verifyNoMoreInteractions(
            mockGrantedPersistenceStrategy,
            mockPendingPersistenceStrategy,
            mockInternalLogger
        )
    }

    @Test
    fun `M keep batch W confirmBatchRead() {delete=false}`(
        @StringForgery fakeBatchId: String,
        @Forgery fakeRemovalReason: RemovalReason
    ) {
        // When
        testedStorage.confirmBatchRead(BatchId(fakeBatchId), fakeRemovalReason, false)

        // Then
        verify(mockGrantedPersistenceStrategy).unlockAndKeep(fakeBatchId)
        verifyNoMoreInteractions(
            mockGrantedPersistenceStrategy,
            mockPendingPersistenceStrategy,
            mockInternalLogger
        )
    }

    // endregion

    // region Storage.dropAll

    @Test
    fun `M drop both granted and pending W dropAll()`() {
        // When
        testedStorage.dropAll()

        // Then
        verify(mockGrantedPersistenceStrategy).dropAll()
        verify(mockPendingPersistenceStrategy).dropAll()
        verifyNoMoreInteractions(
            mockGrantedPersistenceStrategy,
            mockPendingPersistenceStrategy,
            mockInternalLogger
        )
    }

    // endregion

    // region TrackingConsentProviderCallback

    @Test
    fun `M register as consent listener W init()`() {
        // Then
        verify(mockConsentProvider).registerCallback(testedStorage)
        verifyNoInteractions(
            mockGrantedPersistenceStrategy,
            mockPendingPersistenceStrategy,
            mockInternalLogger
        )
    }

    @Test
    fun `M do nothing W onConsentUpdated() {not_granted to not_granted}`() {
        // When
        testedStorage.onConsentUpdated(TrackingConsent.NOT_GRANTED, TrackingConsent.NOT_GRANTED)

        // Then
        verifyNoInteractions(
            mockGrantedPersistenceStrategy,
            mockPendingPersistenceStrategy,
            mockInternalLogger
        )
    }

    @Test
    fun `M do nothing W onConsentUpdated() {not_granted to pending}`() {
        // When
        testedStorage.onConsentUpdated(TrackingConsent.NOT_GRANTED, TrackingConsent.PENDING)

        // Then
        verifyNoInteractions(
            mockGrantedPersistenceStrategy,
            mockPendingPersistenceStrategy,
            mockInternalLogger
        )
    }

    @Test
    fun `M do nothing W onConsentUpdated() {not_granted to granted}`() {
        // When
        testedStorage.onConsentUpdated(TrackingConsent.NOT_GRANTED, TrackingConsent.GRANTED)

        // Then
        verifyNoInteractions(
            mockGrantedPersistenceStrategy,
            mockPendingPersistenceStrategy,
            mockInternalLogger
        )
    }

    @Test
    fun `M drop pending data W onConsentUpdated() {pending to not_granted}`() {
        // When
        testedStorage.onConsentUpdated(TrackingConsent.PENDING, TrackingConsent.NOT_GRANTED)

        // Then
        verify(mockPendingPersistenceStrategy).dropAll()
        verifyNoMoreInteractions(
            mockGrantedPersistenceStrategy,
            mockPendingPersistenceStrategy,
            mockInternalLogger
        )
    }

    @Test
    fun `M do nothing W onConsentUpdated() {pending to pending}`() {
        // When
        testedStorage.onConsentUpdated(TrackingConsent.PENDING, TrackingConsent.PENDING)

        // Then
        verifyNoInteractions(
            mockGrantedPersistenceStrategy,
            mockPendingPersistenceStrategy,
            mockInternalLogger
        )
    }

    @Test
    fun `M migrate data W onConsentUpdated() {pending to granted}`() {
        // When
        testedStorage.onConsentUpdated(TrackingConsent.PENDING, TrackingConsent.GRANTED)

        // Then
        verify(mockPendingPersistenceStrategy).migrateData(mockGrantedPersistenceStrategy)
        verifyNoMoreInteractions(
            mockGrantedPersistenceStrategy,
            mockPendingPersistenceStrategy,
            mockInternalLogger
        )
    }

    @Test
    fun `M do nothing W onConsentUpdated() {granted to not_granted}`() {
        // When
        testedStorage.onConsentUpdated(TrackingConsent.GRANTED, TrackingConsent.NOT_GRANTED)

        // Then
        verifyNoInteractions(
            mockGrantedPersistenceStrategy,
            mockPendingPersistenceStrategy,
            mockInternalLogger
        )
    }

    @Test
    fun `M do nothing W onConsentUpdated() {granted to pending}`() {
        // When
        testedStorage.onConsentUpdated(TrackingConsent.GRANTED, TrackingConsent.PENDING)

        // Then
        verifyNoInteractions(
            mockGrantedPersistenceStrategy,
            mockPendingPersistenceStrategy,
            mockInternalLogger
        )
    }

    @Test
    fun `M do nothing W onConsentUpdated() {granted to granted}`() {
        // When
        testedStorage.onConsentUpdated(TrackingConsent.GRANTED, TrackingConsent.GRANTED)

        // Then
        verifyNoInteractions(
            mockGrantedPersistenceStrategy,
            mockPendingPersistenceStrategy,
            mockInternalLogger
        )
    }

    // endregion
}
