/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.storage.EventBatchWriter
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

    lateinit var testedStorage: AbstractStorage

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

    // region Storage.writeCurrentBatch

    @Test
    fun `𝕄 provide writer 𝕎 writeCurrentBatch()+write() {consent=granted, batchMetadata=null}`(
        @BoolForgery forceNewBatch: Boolean,
        @BoolForgery fakeResult: Boolean,
        @Forgery fakeBatchEvent: RawBatchEvent
    ) {
        // Given
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.GRANTED)
        val mockWriteCallback = mock<(EventBatchWriter) -> Unit>()
        whenever(mockGrantedPersistenceStrategy.write(any(), anyOrNull())) doReturn fakeResult
        var result: Boolean? = null
        whenever(mockWriteCallback.invoke(any())) doAnswer {
            result = (it.getArgument(0) as? EventBatchWriter)?.write(fakeBatchEvent, null)
        }

        // When
        testedStorage.writeCurrentBatch(sdkContext, forceNewBatch, mockWriteCallback)

        // Then
        assertThat(result).isEqualTo(fakeResult)
        verify(mockGrantedPersistenceStrategy).write(fakeBatchEvent, null)
        verifyNoMoreInteractions(
            mockGrantedPersistenceStrategy,
            mockPendingPersistenceStrategy,
            mockInternalLogger
        )
    }

    @Test
    fun `𝕄 provide writer 𝕎 writeCurrentBatch()+write() {consent=granted, batchMetadata!=null}`(
        @BoolForgery forceNewBatch: Boolean,
        @BoolForgery fakeResult: Boolean,
        @Forgery fakeBatchEvent: RawBatchEvent,
        @StringForgery fakeBatchMetadata: String
    ) {
        // Given
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.GRANTED)
        val mockWriteCallback = mock<(EventBatchWriter) -> Unit>()
        val batchMetadata = fakeBatchMetadata.toByteArray()
        whenever(mockGrantedPersistenceStrategy.write(any(), anyOrNull())) doReturn fakeResult
        var result: Boolean? = null
        whenever(mockWriteCallback.invoke(any())) doAnswer {
            result = (it.getArgument(0) as? EventBatchWriter)?.write(fakeBatchEvent, batchMetadata)
        }

        // When
        testedStorage.writeCurrentBatch(sdkContext, forceNewBatch, mockWriteCallback)

        // Then
        assertThat(result).isEqualTo(fakeResult)
        verify(mockGrantedPersistenceStrategy).write(fakeBatchEvent, batchMetadata)
        verifyNoMoreInteractions(
            mockGrantedPersistenceStrategy,
            mockPendingPersistenceStrategy,
            mockInternalLogger
        )
    }

    @Test
    fun `𝕄 provide writer 𝕎 writeCurrentBatch()+currentMetadata() {consent=granted, batchMetadata=null}`(
        @BoolForgery forceNewBatch: Boolean
    ) {
        // Given
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.GRANTED)
        val mockWriteCallback = mock<(EventBatchWriter) -> Unit>()
        whenever(mockGrantedPersistenceStrategy.currentMetadata()) doReturn null
        var resultMetadata: ByteArray? = null
        whenever(mockWriteCallback.invoke(any())) doAnswer {
            resultMetadata = (it.getArgument(0) as? EventBatchWriter)?.currentMetadata()
        }

        // When
        testedStorage.writeCurrentBatch(sdkContext, forceNewBatch, mockWriteCallback)

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
    fun `𝕄 provide writer 𝕎 writeCurrentBatch()+currentMetadata() {consent=granted, batchMetadata!=null}`(
        @BoolForgery forceNewBatch: Boolean,
        @StringForgery fakeBatchMetadata: String
    ) {
        // Given
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.GRANTED)
        val mockWriteCallback = mock<(EventBatchWriter) -> Unit>()
        val batchMetadata = fakeBatchMetadata.toByteArray()
        whenever(mockGrantedPersistenceStrategy.currentMetadata()) doReturn batchMetadata
        var resultMetadata: ByteArray? = null
        whenever(mockWriteCallback.invoke(any())) doAnswer {
            resultMetadata = (it.getArgument(0) as? EventBatchWriter)?.currentMetadata()
        }

        // When
        testedStorage.writeCurrentBatch(sdkContext, forceNewBatch, mockWriteCallback)

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
    fun `𝕄 provide writer 𝕎 writeCurrentBatch()+write() {consent=pending, batchMetadata=null}`(
        @BoolForgery forceNewBatch: Boolean,
        @BoolForgery fakeResult: Boolean,
        @Forgery fakeBatchEvent: RawBatchEvent
    ) {
        // Given
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.PENDING)
        val mockWriteCallback = mock<(EventBatchWriter) -> Unit>()
        whenever(mockPendingPersistenceStrategy.write(any(), anyOrNull())) doReturn fakeResult
        var result: Boolean? = null
        whenever(mockWriteCallback.invoke(any())) doAnswer {
            result = (it.getArgument(0) as? EventBatchWriter)?.write(fakeBatchEvent, null)
        }

        // When
        testedStorage.writeCurrentBatch(sdkContext, forceNewBatch, mockWriteCallback)

        // Then
        assertThat(result).isEqualTo(fakeResult)
        verify(mockPendingPersistenceStrategy).write(fakeBatchEvent, null)
        verifyNoMoreInteractions(
            mockGrantedPersistenceStrategy,
            mockPendingPersistenceStrategy,
            mockInternalLogger
        )
    }

    @Test
    fun `𝕄 provide writer 𝕎 writeCurrentBatch()+write() {consent=pending, batchMetadata!=null}`(
        @BoolForgery forceNewBatch: Boolean,
        @BoolForgery fakeResult: Boolean,
        @Forgery fakeBatchEvent: RawBatchEvent,
        @StringForgery fakeBatchMetadata: String
    ) {
        // Given
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.PENDING)
        val mockWriteCallback = mock<(EventBatchWriter) -> Unit>()
        val batchMetadata = fakeBatchMetadata.toByteArray()
        whenever(mockPendingPersistenceStrategy.write(any(), anyOrNull())) doReturn fakeResult
        var result: Boolean? = null
        whenever(mockWriteCallback.invoke(any())) doAnswer {
            result = (it.getArgument(0) as? EventBatchWriter)?.write(fakeBatchEvent, batchMetadata)
        }

        // When
        testedStorage.writeCurrentBatch(sdkContext, forceNewBatch, mockWriteCallback)

        // Then
        assertThat(result).isEqualTo(fakeResult)
        verify(mockPendingPersistenceStrategy).write(fakeBatchEvent, batchMetadata)
        verifyNoMoreInteractions(
            mockGrantedPersistenceStrategy,
            mockPendingPersistenceStrategy,
            mockInternalLogger
        )
    }

    @Test
    fun `𝕄 provide writer 𝕎 writeCurrentBatch()+currentMetadata() {consent=pending, batchMetadata=null}`(
        @BoolForgery forceNewBatch: Boolean
    ) {
        // Given
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.PENDING)
        val mockWriteCallback = mock<(EventBatchWriter) -> Unit>()
        var resultMetadata: ByteArray? = null
        whenever(mockWriteCallback.invoke(any())) doAnswer {
            resultMetadata = (it.getArgument(0) as? EventBatchWriter)?.currentMetadata()
        }
        whenever(mockPendingPersistenceStrategy.currentMetadata()) doReturn null

        // When
        testedStorage.writeCurrentBatch(sdkContext, forceNewBatch, mockWriteCallback)

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
    fun `𝕄 provide writer 𝕎 writeCurrentBatch()+currentMetadata() {consent=pending, batchMetadata!=null}`(
        @BoolForgery forceNewBatch: Boolean,
        @StringForgery fakeBatchMetadata: String
    ) {
        // Given
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.PENDING)
        val mockWriteCallback = mock<(EventBatchWriter) -> Unit>()
        val batchMetadata = fakeBatchMetadata.toByteArray()
        var resultMetadata: ByteArray? = null
        whenever(mockWriteCallback.invoke(any())) doAnswer {
            resultMetadata = (it.getArgument(0) as? EventBatchWriter)?.currentMetadata()
        }
        whenever(mockPendingPersistenceStrategy.currentMetadata()) doReturn batchMetadata

        // When
        testedStorage.writeCurrentBatch(sdkContext, forceNewBatch, mockWriteCallback)

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
    fun `𝕄 provide no op writer 𝕎 writeCurrentBatch()+write() {consent=not_granted}`(
        @BoolForgery forceNewBatch: Boolean,
        @Forgery fakeBatchEvent: RawBatchEvent,
        @StringForgery fakeBatchMetadata: String
    ) {
        // Given
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.NOT_GRANTED)
        val mockWriteCallback = mock<(EventBatchWriter) -> Unit>()
        val batchMetadata = fakeBatchMetadata.toByteArray()
        var result: Boolean? = null
        whenever(mockWriteCallback.invoke(any())) doAnswer {
            result = (it.getArgument(0) as? EventBatchWriter)?.write(fakeBatchEvent, batchMetadata)
        }

        // When
        testedStorage.writeCurrentBatch(sdkContext, forceNewBatch, mockWriteCallback)

        // Then
        assertThat(result).isFalse()
        verifyNoInteractions(
            mockGrantedPersistenceStrategy,
            mockPendingPersistenceStrategy,
            mockInternalLogger
        )
    }

    @Test
    fun `𝕄 provide no op writer 𝕎 writeCurrentBatch()+currentMetadata() {consent=not_granted}`(
        @BoolForgery forceNewBatch: Boolean
    ) {
        // Given
        val sdkContext = fakeDatadogContext.copy(trackingConsent = TrackingConsent.NOT_GRANTED)
        val mockWriteCallback = mock<(EventBatchWriter) -> Unit>()
        var resultMetadata: ByteArray? = null
        whenever(mockWriteCallback.invoke(any())) doAnswer {
            resultMetadata = (it.getArgument(0) as? EventBatchWriter)?.currentMetadata()
        }

        // When
        testedStorage.writeCurrentBatch(sdkContext, forceNewBatch, mockWriteCallback)

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
    fun `𝕄 provide reader 𝕎 readNextBatch() {no batch}`() {
        // Given
        val mockNoBatchCallback = mock<() -> Unit>()
        val mockReadBatchCallback = mock<(BatchId, BatchReader) -> Unit>()
        whenever(mockGrantedPersistenceStrategy.lockAndReadNext()) doReturn null

        // When
        testedStorage.readNextBatch(mockNoBatchCallback, mockReadBatchCallback)

        // Then
        verify(mockNoBatchCallback).invoke()
        verify(mockGrantedPersistenceStrategy).lockAndReadNext()
        verifyNoInteractions(mockReadBatchCallback, mockPendingPersistenceStrategy)
        verifyNoMoreInteractions(
            mockNoBatchCallback,
            mockGrantedPersistenceStrategy,
            mockInternalLogger
        )
    }

    @Test
    fun `𝕄 provide reader 𝕎 readNextBatch()+read() {with batch}`(
        @Forgery fakeBatch: PersistenceStrategy.Batch
    ) {
        // Given
        val mockNoBatchCallback = mock<() -> Unit>()
        val mockReadBatchCallback = mock<(BatchId, BatchReader) -> Unit>()
        whenever(mockGrantedPersistenceStrategy.lockAndReadNext()) doReturn fakeBatch
        var batchId: BatchId? = null
        var result: List<RawBatchEvent>? = null
        whenever(mockReadBatchCallback.invoke(any(), any())) doAnswer {
            batchId = it.getArgument(0) as? BatchId
            result = (it.getArgument(1) as? BatchReader)?.read()
        }

        // When
        testedStorage.readNextBatch(mockNoBatchCallback, mockReadBatchCallback)

        // Then
        assertThat(batchId).isEqualTo(BatchId(fakeBatch.batchId))
        assertThat(result).isEqualTo(fakeBatch.events)
        verify(mockGrantedPersistenceStrategy).lockAndReadNext()
        verifyNoInteractions(mockPendingPersistenceStrategy)
        verifyNoMoreInteractions(
            mockNoBatchCallback,
            mockGrantedPersistenceStrategy,
            mockInternalLogger
        )
    }

    @Test
    fun `𝕄 provide reader 𝕎 readNextBatch()+currentMetadata() {with batch}`(
        @Forgery fakeBatch: PersistenceStrategy.Batch
    ) {
        // Given
        val mockNoBatchCallback = mock<() -> Unit>()
        val mockReadBatchCallback = mock<(BatchId, BatchReader) -> Unit>()
        whenever(mockGrantedPersistenceStrategy.lockAndReadNext()) doReturn fakeBatch
        var batchId: BatchId? = null
        var result: ByteArray? = null
        whenever(mockReadBatchCallback.invoke(any(), any())) doAnswer {
            batchId = it.getArgument(0) as? BatchId
            result = (it.getArgument(1) as? BatchReader)?.currentMetadata()
        }

        // When
        testedStorage.readNextBatch(mockNoBatchCallback, mockReadBatchCallback)

        // Then
        assertThat(batchId).isEqualTo(BatchId(fakeBatch.batchId))
        assertThat(result).isEqualTo(fakeBatch.metadata)
        verify(mockGrantedPersistenceStrategy).lockAndReadNext()
        verifyNoInteractions(mockPendingPersistenceStrategy)
        verifyNoMoreInteractions(
            mockNoBatchCallback,
            mockGrantedPersistenceStrategy,
            mockInternalLogger
        )
    }

    // endregion

    // region Storage.readNextBatch

    @Test
    fun `𝕄 provide reader 𝕎 confirmBatchRead() {delete=true}`(
        @StringForgery fakeBatchId: String,
        @Forgery fakeRemovalReason: RemovalReason
    ) {
        // Given
        val mockConfirmationCallback = mock<(BatchConfirmation) -> Unit>()
        whenever(mockConfirmationCallback.invoke(any())) doAnswer {
            (it.getArgument(0) as? BatchConfirmation)?.markAsRead(true)
        }

        // When
        testedStorage.confirmBatchRead(BatchId(fakeBatchId), fakeRemovalReason, mockConfirmationCallback)

        // Then
        verify(mockGrantedPersistenceStrategy).unlockAndDelete(fakeBatchId)
        verifyNoMoreInteractions(
            mockGrantedPersistenceStrategy,
            mockPendingPersistenceStrategy,
            mockInternalLogger
        )
    }

    @Test
    fun `𝕄 provide reader 𝕎 confirmBatchRead() {delete=false}`(
        @StringForgery fakeBatchId: String,
        @Forgery fakeRemovalReason: RemovalReason
    ) {
        // Given
        val mockConfirmationCallback = mock<(BatchConfirmation) -> Unit>()
        whenever(mockConfirmationCallback.invoke(any())) doAnswer {
            (it.getArgument(0) as? BatchConfirmation)?.markAsRead(false)
        }

        // When
        testedStorage.confirmBatchRead(BatchId(fakeBatchId), fakeRemovalReason, mockConfirmationCallback)

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
    fun `𝕄 drop both granted and pending 𝕎 dropAll()`() {
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
    fun `𝕄 register as consent listener 𝕎 init()`() {
        // Then
        verify(mockConsentProvider).registerCallback(testedStorage)
        verifyNoInteractions(
            mockGrantedPersistenceStrategy,
            mockPendingPersistenceStrategy,
            mockInternalLogger
        )
    }

    @Test
    fun `𝕄 do nothing 𝕎 onConsentUpdated() {not_granted to not_granted}`() {
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
    fun `𝕄 do nothing 𝕎 onConsentUpdated() {not_granted to pending}`() {
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
    fun `𝕄 do nothing 𝕎 onConsentUpdated() {not_granted to granted}`() {
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
    fun `𝕄 drop pending data 𝕎 onConsentUpdated() {pending to not_granted}`() {
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
    fun `𝕄 do nothing 𝕎 onConsentUpdated() {pending to pending}`() {
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
    fun `𝕄 migrate data 𝕎 onConsentUpdated() {pending to granted}`() {
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
    fun `𝕄 do nothing 𝕎 onConsentUpdated() {granted to not_granted}`() {
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
    fun `𝕄 do nothing 𝕎 onConsentUpdated() {granted to pending}`() {
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
    fun `𝕄 do nothing 𝕎 onConsentUpdated() {granted to granted}`() {
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
