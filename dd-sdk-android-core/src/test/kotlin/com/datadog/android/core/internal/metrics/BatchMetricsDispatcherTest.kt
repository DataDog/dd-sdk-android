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
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
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
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.File
import java.util.Locale
import kotlin.math.max

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class BatchMetricsDispatcherTest {

    private lateinit var testedBatchMetricsDispatcher: BatchMetricsDispatcher

    lateinit var fakeFeatureName: String

    @Forgery
    lateinit var fakeUploadConfiguration: DataUploadConfiguration

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @LongForgery(min = 100001, max = Long.MAX_VALUE / 4)
    var fakeTimestamp: Long = 0L

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @IntForgery(min = 0, max = 100)
    var fakePendingBatches: Int = 0

    @Forgery
    lateinit var fakeFilePersistenceConfig: FilePersistenceConfig

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeFeatureName = forge.anElementFrom(
            listOf(
                Feature.RUM_FEATURE_NAME,
                Feature.TRACING_FEATURE_NAME,
                Feature.LOGS_FEATURE_NAME,
                Feature.SESSION_REPLAY_FEATURE_NAME,
                Feature.SESSION_REPLAY_RESOURCES_FEATURE_NAME
            )
        )
        whenever(mockTimeProvider.getDeviceTimestamp()).doReturn(fakeTimestamp)
        testedBatchMetricsDispatcher = BatchMetricsDispatcher(
            fakeFeatureName,
            fakeUploadConfiguration,
            fakeFilePersistenceConfig,
            mockInternalLogger,
            mockTimeProvider
        )
    }

    @Test
    fun `M send metric W sendBatchDeletedMetric`(forge: Forge) {
        // Given
        val fakeReason = forge.forgeIncludeInMetricReason()
        val fakeFile: File = forge.forgeValidFile()
        val expectedAdditionalProperties = resolveDefaultDeleteExtraProperties(fakeFile).apply {
            put(BatchMetricsDispatcher.BATCH_REMOVAL_KEY, fakeReason.toString())
        }

        // When
        testedBatchMetricsDispatcher.sendBatchDeletedMetric(fakeFile, fakeReason, fakePendingBatches)

        // Then
        argumentCaptor<Map<String, Any?>> {
            verify(mockInternalLogger).logMetric(
                argThat { this.invoke() == BatchMetricsDispatcher.BATCH_DELETED_MESSAGE },
                capture(),
                eq(1.5f),
                eq(null)
            )
            assertThat(firstValue).containsExactlyInAnyOrderEntriesOf(expectedAdditionalProperties)
        }
    }

    @Test
    fun `M drop the metric W sendBatchDeletedMetric { time difference is negative }`(forge: Forge) {
        // Given
        val fakeReason = forge.forgeIncludeInMetricReason()
        val fakeFile: File = forge.forgeValidFile()
        val newFileName = (fakeTimestamp + forge.aLong(min = 100, max = 1000)).toString()
        whenever(fakeFile.name).thenReturn(newFileName)

        // When
        testedBatchMetricsDispatcher.sendBatchDeletedMetric(fakeFile, fakeReason, fakePendingBatches)

        // Then
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M send metric W sendBatchDeletedMetric { app in background }`(forge: Forge) {
        // Given
        val fakeReason = forge.forgeIncludeInMetricReason()
        val fakeFile: File = forge.forgeValidFile()
        val expectedAdditionalProperties = resolveDefaultDeleteExtraProperties(fakeFile).apply {
            put(BatchMetricsDispatcher.BATCH_REMOVAL_KEY, fakeReason.toString())
            put(BatchMetricsDispatcher.IN_BACKGROUND_KEY, true)
        }
        testedBatchMetricsDispatcher.onPaused()

        // When
        testedBatchMetricsDispatcher.sendBatchDeletedMetric(fakeFile, fakeReason, fakePendingBatches)

        // Then
        argumentCaptor<Map<String, Any?>> {
            verify(mockInternalLogger).logMetric(
                argThat { this.invoke() == BatchMetricsDispatcher.BATCH_DELETED_MESSAGE },
                capture(),
                eq(1.5f),
                eq(null)
            )
            assertThat(firstValue).containsExactlyInAnyOrderEntriesOf(expectedAdditionalProperties)
        }
    }

    @Test
    fun `M send metric W sendBatchDeletedMetric { app back in foreground }`(forge: Forge) {
        // Given
        testedBatchMetricsDispatcher.onPaused()
        val fakeReason = forge.forgeIncludeInMetricReason()
        val fakeFile: File = forge.forgeValidFile()
        val expectedAdditionalProperties = resolveDefaultDeleteExtraProperties(fakeFile).apply {
            put(BatchMetricsDispatcher.BATCH_REMOVAL_KEY, fakeReason.toString())
            put(BatchMetricsDispatcher.IN_BACKGROUND_KEY, false)
        }
        // When
        testedBatchMetricsDispatcher.onResumed()
        testedBatchMetricsDispatcher.sendBatchDeletedMetric(fakeFile, fakeReason, fakePendingBatches)

        // Then
        argumentCaptor<Map<String, Any?>> {
            verify(mockInternalLogger).logMetric(
                argThat { this.invoke() == BatchMetricsDispatcher.BATCH_DELETED_MESSAGE },
                capture(),
                eq(1.5f),
                eq(null)
            )
            assertThat(firstValue).containsExactlyInAnyOrderEntriesOf(expectedAdditionalProperties)
        }
    }

    @Test
    fun `M send metric W sendBatchDeletedMetric { file is in pending folder }`(forge: Forge) {
        // Given
        testedBatchMetricsDispatcher.onPaused()
        val fakeReason = forge.forgeIncludeInMetricReason()
        val fakeFile: File = forge.forgeValidFile().apply {
            val forgeAPendingDirName = forge.forgeAPendingDirName()
            whenever(this.parentFile?.name).thenReturn(forgeAPendingDirName)
        }
        val expectedAdditionalProperties = resolveDefaultDeleteExtraProperties(fakeFile).apply {
            put(BatchMetricsDispatcher.BATCH_REMOVAL_KEY, fakeReason.toString())
            put(BatchMetricsDispatcher.TRACKING_CONSENT_KEY, "pending")
        }
        // When
        testedBatchMetricsDispatcher.sendBatchDeletedMetric(fakeFile, fakeReason, fakePendingBatches)

        // Then
        argumentCaptor<Map<String, Any?>> {
            verify(mockInternalLogger).logMetric(
                argThat { this.invoke() == BatchMetricsDispatcher.BATCH_DELETED_MESSAGE },
                capture(),
                eq(1.5f),
                eq(null)
            )
            assertThat(firstValue).containsExactlyInAnyOrderEntriesOf(expectedAdditionalProperties)
        }
    }

    @Test
    fun `M send metric W sendBatchDeletedMetric { file parent dir is unknown  }`(forge: Forge) {
        // Given
        testedBatchMetricsDispatcher.onPaused()
        val fakeReason = forge.forgeIncludeInMetricReason()
        val fakeFile: File = forge.forgeValidFile().apply {
            whenever(this.parentFile?.name).thenReturn(forge.anAlphabeticalString())
        }
        val expectedAdditionalProperties = resolveDefaultDeleteExtraProperties(fakeFile).apply {
            put(BatchMetricsDispatcher.BATCH_REMOVAL_KEY, fakeReason.toString())
            put(BatchMetricsDispatcher.TRACKING_CONSENT_KEY, null)
        }
        // When
        testedBatchMetricsDispatcher.sendBatchDeletedMetric(fakeFile, fakeReason, fakePendingBatches)

        // Then
        argumentCaptor<Map<String, Any?>> {
            verify(mockInternalLogger).logMetric(
                argThat { this.invoke() == BatchMetricsDispatcher.BATCH_DELETED_MESSAGE },
                capture(),
                eq(1.5f),
                eq(null)
            )
            assertThat(firstValue).containsExactlyInAnyOrderEntriesOf(expectedAdditionalProperties)
        }
    }

    @Test
    fun `M send metric W sendBatchDeletedMetric { file parent dir is null  }`(forge: Forge) {
        // Given
        testedBatchMetricsDispatcher.onPaused()
        val fakeReason = forge.forgeIncludeInMetricReason()
        val fakeFile: File = forge.forgeValidFile().apply {
            whenever(this.parentFile).thenReturn(null)
        }
        val expectedAdditionalProperties = resolveDefaultDeleteExtraProperties(fakeFile).apply {
            put(BatchMetricsDispatcher.BATCH_REMOVAL_KEY, fakeReason.toString())
            put(BatchMetricsDispatcher.TRACKING_CONSENT_KEY, null)
        }
        // When
        testedBatchMetricsDispatcher.sendBatchDeletedMetric(fakeFile, fakeReason, fakePendingBatches)

        // Then
        argumentCaptor<Map<String, Any?>> {
            verify(mockInternalLogger).logMetric(
                argThat { this.invoke() == BatchMetricsDispatcher.BATCH_DELETED_MESSAGE },
                capture(),
                eq(1.5f),
                eq(null)
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
        testedBatchMetricsDispatcher.sendBatchDeletedMetric(fakeFile, fakeReason, fakePendingBatches)

        // Then
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.ERROR),
            eq(InternalLogger.Target.MAINTAINER),
            argThat {
                this.invoke() ==
                    BatchMetricsDispatcher.WRONG_FILE_NAME_MESSAGE_FORMAT
                        .format(Locale.ENGLISH, fakeFile.name)
            },
            eq(null),
            eq(false),
            eq(null)
        )
        verifyNoMoreInteractions(mockInternalLogger)
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
            mockTimeProvider
        )
        val fakeReason: RemovalReason.Flushed = forge.getForgery()
        val fakeFile: File = forge.forgeValidFile()

        // When
        testedBatchMetricsDispatcher.sendBatchDeletedMetric(fakeFile, fakeReason, fakePendingBatches)

        // Then
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M send metric W sendBatchClosedMetric`(
        @Forgery fakeMetadata: BatchClosedMetadata,
        forge: Forge
    ) {
        // Given
        val fakeFile: File = forge.forgeValidClosedFile()
        val expectedAdditionalProperties =
            resolveDefaultCloseExtraProperties(fakeFile, fakeMetadata).apply {
                put(BatchMetricsDispatcher.BATCH_SIZE_KEY, fakeFile.length())
            }

        // When
        testedBatchMetricsDispatcher.sendBatchClosedMetric(fakeFile, fakeMetadata)

        // Then
        argumentCaptor<Map<String, Any?>> {
            verify(mockInternalLogger).logMetric(
                argThat { this.invoke() == BatchMetricsDispatcher.BATCH_CLOSED_MESSAGE },
                capture(),
                eq(1.5f),
                eq(null)
            )
            assertThat(firstValue).containsExactlyInAnyOrderEntriesOf(expectedAdditionalProperties)
        }
    }

    @Test
    fun `M drop the metric W sendBatchClosedMetric { time difference is negative }`(
        @Forgery fakeMetadata: BatchClosedMetadata,
        forge: Forge
    ) {
        // Given
        val fakeFile: File = forge.forgeValidClosedFile()
        val fakeFileName = fakeMetadata.lastTimeWasUsedInMs + forge.aLong(min = 100, max = 1000)
        whenever(fakeFile.name).thenReturn(fakeFileName.toString())

        // When
        testedBatchMetricsDispatcher.sendBatchClosedMetric(fakeFile, fakeMetadata)

        // Then
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M send metric W sendBatchClosedMetric{ file is broken }`(
        forge: Forge,
        @Forgery fakeMetadata: BatchClosedMetadata,
        @Forgery fakeException: Exception
    ) {
        // Given
        val fakeFile: File = forge.forgeValidClosedFile().apply {
            whenever(this.length()).thenThrow(fakeException)
        }
        val expectedAdditionalProperties =
            resolveDefaultCloseExtraProperties(fakeFile, fakeMetadata).apply {
                put(BatchMetricsDispatcher.BATCH_SIZE_KEY, 0L)
            }

        // When
        testedBatchMetricsDispatcher.sendBatchClosedMetric(fakeFile, fakeMetadata)

        // Then
        argumentCaptor<Map<String, Any?>> {
            verify(mockInternalLogger).logMetric(
                argThat { this.invoke() == BatchMetricsDispatcher.BATCH_CLOSED_MESSAGE },
                capture(),
                eq(1.5f),
                eq(null)
            )
            assertThat(firstValue).containsExactlyInAnyOrderEntriesOf(expectedAdditionalProperties)
        }
    }

    @Test
    fun `M send metric W sendBatchClosedMetric{ file is in pending folder }`(
        forge: Forge,
        @Forgery fakeMetadata: BatchClosedMetadata,
        @Forgery fakeException: Exception
    ) {
        // Given
        val fakeFile: File = forge.forgeValidClosedFile().apply {
            whenever(this.length()).thenThrow(fakeException)
            whenever(this.parentFile?.name).thenReturn(forge.forgeAPendingDirName())
        }
        val expectedAdditionalProperties =
            resolveDefaultCloseExtraProperties(fakeFile, fakeMetadata).apply {
                put(BatchMetricsDispatcher.BATCH_SIZE_KEY, 0L)
                put(BatchMetricsDispatcher.TRACKING_CONSENT_KEY, "pending")
            }

        // When
        testedBatchMetricsDispatcher.sendBatchClosedMetric(fakeFile, fakeMetadata)

        // Then
        argumentCaptor<Map<String, Any?>> {
            verify(mockInternalLogger).logMetric(
                argThat { this.invoke() == BatchMetricsDispatcher.BATCH_CLOSED_MESSAGE },
                capture(),
                eq(1.5f),
                eq(null)
            )
            assertThat(firstValue).containsExactlyInAnyOrderEntriesOf(expectedAdditionalProperties)
        }
    }

    @Test
    fun `M send metric W sendBatchClosedMetric{ file parent dir is null }`(
        forge: Forge,
        @Forgery fakeMetadata: BatchClosedMetadata,
        @Forgery fakeException: Exception
    ) {
        // Given
        val fakeFile: File = forge.forgeValidClosedFile().apply {
            whenever(this.length()).thenThrow(fakeException)
            whenever(this.parentFile).thenReturn(null)
        }
        val expectedAdditionalProperties =
            resolveDefaultCloseExtraProperties(fakeFile, fakeMetadata).apply {
                put(BatchMetricsDispatcher.BATCH_SIZE_KEY, 0L)
                put(BatchMetricsDispatcher.TRACKING_CONSENT_KEY, null)
            }

        // When
        testedBatchMetricsDispatcher.sendBatchClosedMetric(fakeFile, fakeMetadata)

        // Then
        argumentCaptor<Map<String, Any?>> {
            verify(mockInternalLogger).logMetric(
                argThat { this.invoke() == BatchMetricsDispatcher.BATCH_CLOSED_MESSAGE },
                capture(),
                eq(1.5f),
                eq(null)
            )
            assertThat(firstValue).containsExactlyInAnyOrderEntriesOf(expectedAdditionalProperties)
        }
    }

    @Test
    fun `M send metric W sendBatchClosedMetric{ file parent dir is unknown }`(
        forge: Forge,
        @Forgery fakeMetadata: BatchClosedMetadata,
        @Forgery fakeException: Exception
    ) {
        // Given
        val fakeFile: File = forge.forgeValidClosedFile().apply {
            whenever(this.length()).thenThrow(fakeException)
            whenever(this.parentFile?.name).thenReturn(forge.anAlphabeticalString())
        }
        val expectedAdditionalProperties =
            resolveDefaultCloseExtraProperties(fakeFile, fakeMetadata).apply {
                put(BatchMetricsDispatcher.BATCH_SIZE_KEY, 0L)
                put(BatchMetricsDispatcher.TRACKING_CONSENT_KEY, null)
            }

        // When
        testedBatchMetricsDispatcher.sendBatchClosedMetric(fakeFile, fakeMetadata)

        // Then
        argumentCaptor<Map<String, Any?>> {
            verify(mockInternalLogger).logMetric(
                argThat { this.invoke() == BatchMetricsDispatcher.BATCH_CLOSED_MESSAGE },
                capture(),
                eq(1.5f),
                eq(null)
            )
            assertThat(firstValue).containsExactlyInAnyOrderEntriesOf(expectedAdditionalProperties)
        }
    }

    @Test
    fun `M do nothing W sendBatchClosedMetric { file name is broken }`(
        forge: Forge,
        @Forgery fakeMetadata: BatchClosedMetadata
    ) {
        // Given
        val fakeFile: File = forge.forgeValidClosedFile().apply {
            whenever(this.name).thenReturn(forge.anAlphabeticalString())
        }

        // When
        testedBatchMetricsDispatcher.sendBatchClosedMetric(fakeFile, fakeMetadata)

        // Then
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.ERROR),
            eq(InternalLogger.Target.MAINTAINER),
            argThat {
                this.invoke() ==
                    BatchMetricsDispatcher.WRONG_FILE_NAME_MESSAGE_FORMAT
                        .format(Locale.ENGLISH, fakeFile.name)
            },
            eq(null),
            eq(false),
            eq(null)
        )
        verifyNoMoreInteractions(mockInternalLogger)
    }

    @Test
    fun `M do nothing W sendBatchClosedMetric { file does not exist }`(
        forge: Forge,
        @Forgery fakeMetadata: BatchClosedMetadata
    ) {
        // Given
        val fakeFile: File = forge.forgeValidClosedFile().apply {
            whenever(this.exists()).thenReturn(false)
        }

        // When
        testedBatchMetricsDispatcher.sendBatchClosedMetric(fakeFile, fakeMetadata)

        // Then
        verifyNoInteractions(mockInternalLogger)
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
            mockTimeProvider
        )
        val fakeFile: File = forge.forgeValidClosedFile()

        // When
        testedBatchMetricsDispatcher.sendBatchClosedMetric(fakeFile, fakeMetadata)

        // Then
        verifyNoInteractions(mockInternalLogger)
    }

    private fun resolveDefaultDeleteExtraProperties(file: File): MutableMap<String, Any?> {
        return mutableMapOf(
            BatchMetricsDispatcher.TYPE_KEY to BatchMetricsDispatcher.BATCH_DELETED_TYPE_VALUE,
            BatchMetricsDispatcher.TRACK_KEY to resolveTrackName(fakeFeatureName),
            BatchMetricsDispatcher.BATCH_AGE_KEY to max(0, (fakeTimestamp - file.name.toLong())),
            BatchMetricsDispatcher.UPLOADER_WINDOW_KEY to
                fakeFilePersistenceConfig.recentDelayMs,
            BatchMetricsDispatcher.UPLOADER_DELAY_KEY to mapOf(
                BatchMetricsDispatcher.UPLOADER_DELAY_MIN_KEY to
                    fakeUploadConfiguration.minDelayMs,
                BatchMetricsDispatcher.UPLOADER_DELAY_MAX_KEY to
                    fakeUploadConfiguration.maxDelayMs
            ),
            BatchMetricsDispatcher.FILE_NAME to file.name,
            BatchMetricsDispatcher.THREAD_NAME to Thread.currentThread().name,
            BatchMetricsDispatcher.TRACKING_CONSENT_KEY to "granted",
            BatchMetricsDispatcher.PENDING_BATCHES to fakePendingBatches,
            BatchMetricsDispatcher.IN_BACKGROUND_KEY to true
        )
    }

    private fun resolveDefaultCloseExtraProperties(
        file: File,
        batchClosedMetadata: BatchClosedMetadata
    ): MutableMap<String, Any?> {
        return mutableMapOf(
            BatchMetricsDispatcher.TYPE_KEY to BatchMetricsDispatcher.BATCH_CLOSED_TYPE_VALUE,
            BatchMetricsDispatcher.TRACK_KEY to resolveTrackName(fakeFeatureName),
            BatchMetricsDispatcher.BATCH_DURATION_KEY to
                max(0, (batchClosedMetadata.lastTimeWasUsedInMs - file.name.toLong())),
            BatchMetricsDispatcher.UPLOADER_WINDOW_KEY to
                fakeFilePersistenceConfig.recentDelayMs,
            BatchMetricsDispatcher.BATCH_EVENTS_COUNT_KEY to batchClosedMetadata.eventsCount,
            BatchMetricsDispatcher.FILE_NAME to file.name,
            BatchMetricsDispatcher.THREAD_NAME to Thread.currentThread().name,
            BatchMetricsDispatcher.TRACKING_CONSENT_KEY to "granted"
        )
    }

    private fun Forge.forgeValidFile(): File {
        val fileNameAsLong = fakeTimestamp - aLong(min = 1000, max = 100000)
        val fileLength = aPositiveLong()
        val dirName = forgeAGrantedDirName()
        val parentDirectory: File = mock {
            whenever(it.isDirectory).thenReturn(true)
            whenever(it.name).thenReturn(dirName)
        }
        val fakeFile: File = mock {
            whenever(it.parentFile).thenReturn(parentDirectory)
            whenever(it.name).thenReturn(fileNameAsLong.toString())
            whenever(it.length()).thenReturn(fileLength)
        }
        return fakeFile
    }

    private fun Forge.forgeValidClosedFile(): File {
        return forgeValidFile().apply { whenever(this.exists()).thenReturn(true) }
    }

    private fun Forge.forgeAGrantedDirName(): String {
        val separator = "-"
        return aList(anInt(min = 1, max = 10)) { anAlphabeticalString() }
            .joinToString(separator) + "-v" + aNumericalString()
    }

    private fun Forge.forgeAPendingDirName(): String {
        val separator = "-"
        return aList(anInt(min = 1, max = 10)) { anAlphabeticalString() }
            .joinToString(separator) + "-pending-v" + aNumericalString()
    }

    private fun resolveTrackName(featureName: String): String? {
        return when (featureName) {
            Feature.RUM_FEATURE_NAME -> BatchMetricsDispatcher.RUM_TRACK_NAME
            Feature.LOGS_FEATURE_NAME -> BatchMetricsDispatcher.LOGS_TRACK_NAME
            Feature.TRACING_FEATURE_NAME -> BatchMetricsDispatcher.TRACE_TRACK_NAME
            Feature.SESSION_REPLAY_FEATURE_NAME -> BatchMetricsDispatcher.SR_TRACK_NAME
            Feature.SESSION_REPLAY_RESOURCES_FEATURE_NAME -> BatchMetricsDispatcher.SR_RESOURCES_TRACK_NAME
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
