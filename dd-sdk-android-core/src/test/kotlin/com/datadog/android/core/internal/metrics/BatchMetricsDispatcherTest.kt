/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.metrics

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.core.internal.configuration.DataUploadConfiguration
import com.datadog.android.core.internal.persistence.file.FilePersistenceConfig
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.core.sampling.Sampler
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
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
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.File
import java.util.Locale

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class BatchMetricsDispatcherTest {

    lateinit var testedBatchMetricsDispatcher: BatchMetricsDispatcher

    lateinit var fakeFeatureName: String

    @Forgery
    lateinit var fakeUploadConfiguration: DataUploadConfiguration

    @Mock
    lateinit var mockDateTimeProvider: TimeProvider

    private var currentTimeInMillis: Long = System.currentTimeMillis()

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockSampler: Sampler

    @Forgery
    lateinit var fakeFilePersistenceConfig: FilePersistenceConfig

    @BeforeEach
    fun `set up`(forge: Forge) {
        whenever(mockSampler.sample()).doReturn(true)
        fakeFeatureName = forge.anElementFrom(
            listOf(
                Feature.RUM_FEATURE_NAME,
                Feature.TRACING_FEATURE_NAME,
                Feature.LOGS_FEATURE_NAME,
                Feature.SESSION_REPLAY_FEATURE_NAME
            )
        )
        whenever(mockDateTimeProvider.getDeviceTimestamp()).doReturn(currentTimeInMillis)
        testedBatchMetricsDispatcher = BatchMetricsDispatcher(
            fakeFeatureName,
            fakeUploadConfiguration,
            fakeFilePersistenceConfig,
            mockInternalLogger,
            mockDateTimeProvider,
            mockSampler
        )
    }

    @Test
    fun `M send metric W sendBatchDeletedMetric`(forge: Forge) {
        // Given
        val fakeReason = forge.forgeIncludeInMetricReason()
        val fakeFile: File = forge.forgeValidFile()
        val expectedAdditionalProperties = mapOf(
            BatchMetricsDispatcher.TYPE_KEY to
                BatchMetricsDispatcher.BATCH_DELETED_TYPE_VALUE,
            BatchMetricsDispatcher.TRACK_KEY to
                trackNameResolver(fakeFeatureName),
            BatchMetricsDispatcher.BATCH_AGE_KEY to
                (currentTimeInMillis - fakeFile.name.toLong()),
            BatchMetricsDispatcher.UPLOADER_WINDOW_KEY to
                fakeFilePersistenceConfig.recentDelayMs,
            BatchMetricsDispatcher.UPLOADER_DELAY_KEY to mapOf(
                BatchMetricsDispatcher.UPLOADER_DELAY_MIN_KEY to
                    fakeUploadConfiguration.minDelayMs,
                BatchMetricsDispatcher.UPLOADER_DELAY_MAX_KEY to
                    fakeUploadConfiguration.maxDelayMs
            ),
            BatchMetricsDispatcher.BATCH_REMOVAL_KEY to fakeReason.toString(),
            BatchMetricsDispatcher.IN_BACKGROUND_KEY to false
        )

        // When
        testedBatchMetricsDispatcher.sendBatchDeletedMetric(fakeFile, fakeReason)

        // Then
        argumentCaptor<Map<String, Any?>> {
            verify(mockInternalLogger).logMetric(
                argThat { this.invoke() == BatchMetricsDispatcher.BATCH_DELETED_MESSAGE },
                capture()
            )
            assertThat(firstValue).containsExactlyInAnyOrderEntriesOf(expectedAdditionalProperties)
        }
    }

    @Test
    fun `M do nothing W sendBatchDeletedMetric { file name is broken }`(forge: Forge) {
        // Given
        val fakeReason = forge.forgeIncludeInMetricReason()
        val fakeFile: File = mock {
            whenever(it.name).thenReturn(forge.anAlphabeticalString())
        }

        // When
        testedBatchMetricsDispatcher.sendBatchDeletedMetric(fakeFile, fakeReason)

        // Then
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.ERROR),
            eq(InternalLogger.Target.MAINTAINER),
            argThat {
                this.invoke() ==
                    BatchMetricsDispatcher.WRONG_FILE_NAME_MESSAGE_FORMAT
                        .format(Locale.ENGLISH, fakeFile.name)
            },
            argThat { this is NumberFormatException },
            eq(false),
            eq(null)
        )
        verifyNoMoreInteractions(mockInternalLogger)
    }

    @Test
    fun `M do nothing W sendBatchDeletedMetric { sampled out }`(forge: Forge) {
        // Given
        reset(mockSampler)
        whenever(mockSampler.sample()).doReturn(false)
        val fakeReason = forge.forgeIncludeInMetricReason()
        val fakeFile: File = forge.forgeValidFile()

        // When
        testedBatchMetricsDispatcher.sendBatchDeletedMetric(fakeFile, fakeReason)

        // Then
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M do nothing W sendBatchDeletedMetric { reason notIncludedInMetrics }`(forge: Forge) {
        // Given
        reset(mockSampler)
        whenever(mockSampler.sample()).doReturn(false)
        val fakeReason: RemovalReason.Flushed = forge.getForgery()
        val fakeFile: File = forge.forgeValidFile()

        // When
        testedBatchMetricsDispatcher.sendBatchDeletedMetric(fakeFile, fakeReason)

        // Then
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M do nothing W sendBatchDeletedMetric { feature unknown }`(forge: Forge) {
        // Given
        val fakeUnknownFeature = forge.anAlphabeticalString()
        testedBatchMetricsDispatcher = BatchMetricsDispatcher(
            fakeUnknownFeature,
            fakeUploadConfiguration,
            fakeFilePersistenceConfig,
            mockInternalLogger,
            mockDateTimeProvider,
            mockSampler
        )
        val fakeReason: RemovalReason.Flushed = forge.getForgery()
        val fakeFile: File = forge.forgeValidFile()

        // When
        testedBatchMetricsDispatcher.sendBatchDeletedMetric(fakeFile, fakeReason)

        // Then
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M send metric W sendBatchClosedMetric`(
        @Forgery fakeMetadata: BatchClosedMetadata,
        forge: Forge
    ) {
        // Given
        val fakeFile: File = forge.forgeValidFile()
        val expectedAdditionalProperties = mapOf(
            BatchMetricsDispatcher.TYPE_KEY to
                BatchMetricsDispatcher.BATCH_CLOSED_TYPE_VALUE,
            BatchMetricsDispatcher.TRACK_KEY to
                trackNameResolver(fakeFeatureName),
            BatchMetricsDispatcher.BATCH_DURATION_KEY to
                (currentTimeInMillis - fakeMetadata.lastTimeWasUsedInMs),
            BatchMetricsDispatcher.UPLOADER_WINDOW_KEY to
                fakeFilePersistenceConfig.recentDelayMs,
            BatchMetricsDispatcher.BATCH_SIZE_KEY to fakeFile.length(),
            BatchMetricsDispatcher.FORCE_NEW_KEY to fakeMetadata.forcedNew,
            BatchMetricsDispatcher.BATCH_EVENTS_COUNT_KEY to fakeMetadata.eventsCount
        )

        // When
        testedBatchMetricsDispatcher.sendBatchClosedMetric(fakeFile, fakeMetadata)

        // Then
        argumentCaptor<Map<String, Any?>> {
            verify(mockInternalLogger).logMetric(
                argThat { this.invoke() == BatchMetricsDispatcher.BATCH_CLOSED_MESSAGE },
                capture()
            )
            assertThat(firstValue).containsExactlyInAnyOrderEntriesOf(expectedAdditionalProperties)
        }
    }

    @Test
    fun `M send metric W sendBatchClosedMetric{ file is broken }`(
        @Forgery fakeMetadata: BatchClosedMetadata,
        @Forgery fakeThrowable: Throwable
    ) {
        // Given
        val fakeFile: File = mock {
            whenever(it.length()).thenThrow(fakeThrowable)
        }
        val expectedAdditionalProperties = mapOf(
            BatchMetricsDispatcher.TYPE_KEY to
                BatchMetricsDispatcher.BATCH_CLOSED_TYPE_VALUE,
            BatchMetricsDispatcher.TRACK_KEY to
                trackNameResolver(fakeFeatureName),
            BatchMetricsDispatcher.BATCH_DURATION_KEY to
                (currentTimeInMillis - fakeMetadata.lastTimeWasUsedInMs),
            BatchMetricsDispatcher.UPLOADER_WINDOW_KEY to
                fakeFilePersistenceConfig.recentDelayMs,
            BatchMetricsDispatcher.BATCH_SIZE_KEY to 0L,
            BatchMetricsDispatcher.FORCE_NEW_KEY to fakeMetadata.forcedNew,
            BatchMetricsDispatcher.BATCH_EVENTS_COUNT_KEY to fakeMetadata.eventsCount
        )

        // When
        testedBatchMetricsDispatcher.sendBatchClosedMetric(fakeFile, fakeMetadata)

        // Then
        argumentCaptor<Map<String, Any?>> {
            verify(mockInternalLogger).logMetric(
                argThat { this.invoke() == BatchMetricsDispatcher.BATCH_CLOSED_MESSAGE },
                capture()
            )
            assertThat(firstValue).containsExactlyInAnyOrderEntriesOf(expectedAdditionalProperties)
        }
    }

    @Test
    fun `M do nothing W sendBatchClosedMetric { feature unknown }`(
        @Forgery fakeMetadata: BatchClosedMetadata,
        forge: Forge
    ) {
        // Given
        val fakeUnknownFeature = forge.anAlphabeticalString()
        testedBatchMetricsDispatcher = BatchMetricsDispatcher(
            fakeUnknownFeature,
            fakeUploadConfiguration,
            fakeFilePersistenceConfig,
            mockInternalLogger,
            mockDateTimeProvider,
            mockSampler
        )
        val fakeFile: File = forge.forgeValidFile()

        // When
        testedBatchMetricsDispatcher.sendBatchClosedMetric(fakeFile, fakeMetadata)

        // Then
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M do nothing W sendBatchClosedMetric { sampled out }`(
        @Forgery fakeMetadata: BatchClosedMetadata,
        forge: Forge
    ) {
        // Given
        reset(mockSampler)
        whenever(mockSampler.sample()).doReturn(false)
        val fakeFile: File = forge.forgeValidFile()

        // When
        testedBatchMetricsDispatcher.sendBatchClosedMetric(fakeFile, fakeMetadata)

        // Then
        verifyNoInteractions(mockInternalLogger)
    }

    private fun Forge.forgeValidFile(): File {
        val fileNameAsLong = currentTimeInMillis - aLong(min = 1000, max = 100000)
        val fileLength = aPositiveLong()
        val fakeFile: File = mock {
            whenever(it.name).thenReturn(fileNameAsLong.toString())
            whenever(it.length()).thenReturn(fileLength)
        }
        return fakeFile
    }

    private fun trackNameResolver(featureName: String): String? {
        return when (featureName) {
            Feature.RUM_FEATURE_NAME -> BatchMetricsDispatcher.RUM_TRACK_NAME
            Feature.LOGS_FEATURE_NAME -> BatchMetricsDispatcher.LOGS_TRACK_NAME
            Feature.TRACING_FEATURE_NAME -> BatchMetricsDispatcher.TRACE_TRACK_NAME
            Feature.SESSION_REPLAY_FEATURE_NAME -> BatchMetricsDispatcher.SR_TRACK_NAME
            else -> null
        }
    }

    private fun Forge.forgeIncludeInMetricReason(): RemovalReason {
        while (true) {
            val reason: RemovalReason = getForgery()
            if (reason.includeInMetrics()) {
                return reason
            }
        }
    }
}
