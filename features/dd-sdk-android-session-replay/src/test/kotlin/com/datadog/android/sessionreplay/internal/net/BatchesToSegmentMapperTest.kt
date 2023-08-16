/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.net

import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.processor.EnrichedRecord
import com.datadog.android.sessionreplay.internal.utils.SessionReplayRumContext
import com.datadog.android.sessionreplay.model.MobileSegment
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import fr.xgouchet.elmyr.Forge
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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class BatchesToSegmentMapperTest {

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    private lateinit var testedMapper: BatchesToSegmentsMapper

    @BeforeEach
    fun `set up`() {
        testedMapper = BatchesToSegmentsMapper(mockInternalLogger)
    }

    @Test
    fun `M generate segment to jsonSegment pair W map`(forge: Forge) {
        // Given
        val rumContext: SessionReplayRumContext = forge.getForgery()
        val fakeRecordsSize = forge.anInt(min = 10, max = 20)
        val fakeRecords: List<MobileSegment.MobileRecord> = forge.aList(fakeRecordsSize) {
            forge.getForgery()
        }
        val fakeExpectedRecords = fakeRecords.sortedBy { it.timestamp() }
        val fakeEnrichedRecords = fakeRecords
            .chunked(forge.anInt(min = 1, max = fakeRecordsSize))
            .map {
                EnrichedRecord(
                    rumContext.applicationId,
                    rumContext.sessionId,
                    rumContext.sessionId,
                    it
                )
            }
        val fakeExpectedRecord = EnrichedRecord(
            rumContext.applicationId,
            rumContext.sessionId,
            rumContext.sessionId,
            fakeExpectedRecords
        )

        val expectedEmptySegment = fakeExpectedRecord.toSegment().copy(records = emptyList())
        val expectedSerializedSegment = fakeExpectedRecord.toSegment()
            .toJson().asJsonObject.toString()
        val fakeBatchData = fakeEnrichedRecords.map { it.toJson().toByteArray() }

        // When
        val mappedSegment = testedMapper.map(fakeBatchData)

        // Then
        val segment = mappedSegment?.first
        val serializedSegment = mappedSegment?.second.toString()
        assertThat(segment).isEqualTo(expectedEmptySegment)
        assertThat(serializedSegment).isEqualTo(expectedSerializedSegment)
    }

    @Test
    fun `M use the first record in the list as start timestamp W map`(
        forge: Forge
    ) {
        // Given
        val rumContext: SessionReplayRumContext = forge.getForgery()
        val fakeRecordsSize = forge.anInt(min = 10, max = 20)
        val fakeRecords: List<MobileSegment.MobileRecord> = forge.aList(fakeRecordsSize) {
            forge.getForgery()
        }
        val fakeExpectedRecords = fakeRecords.sortedBy { it.timestamp() }
        val fakeEnrichedRecords = fakeRecords
            .chunked(forge.anInt(min = 1, max = fakeRecordsSize))
            .map {
                EnrichedRecord(
                    rumContext.applicationId,
                    rumContext.sessionId,
                    rumContext.sessionId,
                    it
                )
            }
        val fakeBatchData = fakeEnrichedRecords.map { it.toJson().toByteArray() }
        val expectedStartTimestamp = fakeExpectedRecords.first().timestamp()

        // When
        val mappedSegment = testedMapper.map(fakeBatchData)

        // Then
        assertThat(mappedSegment?.first?.start).isEqualTo(expectedStartTimestamp)
        val serializedRecordStartTimestamp = mappedSegment?.second
            ?.getAsJsonPrimitive("start")?.asLong
        assertThat(serializedRecordStartTimestamp).isEqualTo(expectedStartTimestamp)
    }

    @Test
    fun `M use the last record in the list as end timestamp W map`(
        forge: Forge
    ) {
        // Given
        val rumContext: SessionReplayRumContext = forge.getForgery()
        val fakeRecordsSize = forge.anInt(min = 10, max = 20)
        val fakeRecords: List<MobileSegment.MobileRecord> = forge.aList(fakeRecordsSize) {
            forge.getForgery()
        }
        val fakeExpectedRecords = fakeRecords.sortedBy { it.timestamp() }
        val fakeEnrichedRecords = fakeRecords
            .chunked(forge.anInt(min = 1, max = fakeRecordsSize))
            .map {
                EnrichedRecord(
                    rumContext.applicationId,
                    rumContext.sessionId,
                    rumContext.sessionId,
                    it
                )
            }
        val fakeBatchData = fakeEnrichedRecords.map { it.toJson().toByteArray() }
        val expectedEndTimestamp = fakeExpectedRecords.last().timestamp()

        // When
        val mappedSegment = testedMapper.map(fakeBatchData)

        // Then
        assertThat(mappedSegment?.first?.end).isEqualTo(expectedEndTimestamp)
        val serializedRecordStartTimestamp = mappedSegment?.second
            ?.getAsJsonPrimitive("end")?.asLong
        assertThat(serializedRecordStartTimestamp).isEqualTo(expectedEndTimestamp)
    }

    @Test
    fun `M return null W map{ empty records }`(
        forge: Forge
    ) {
        // Given
        val fakeEnrichedRecords: List<EnrichedRecord> =
            forge.aList(forge.anInt(min = 10, max = 30)) {
                forge.getForgery<EnrichedRecord>().copy(records = emptyList())
            }
        val fakeBatchData = fakeEnrichedRecords.map { it.toJson().toByteArray() }

        // When
        assertThat(testedMapper.map(fakeBatchData)).isNull()
    }

    @Test
    fun `M return empty null W map { broken serialized records }`(
        forge: Forge
    ) {
        // Given
        val fakeBatchData = forge.aList { forge.anAlphabeticalString().toByteArray() }

        // When
        assertThat(testedMapper.map(fakeBatchData)).isNull()
    }

    @Test
    fun `M return null W map { all records with missing timestamp key }`(
        forge: Forge
    ) {
        // Given
        val rumContext: SessionReplayRumContext = forge.getForgery()
        val fakeRecordsSize = forge.anInt(min = 10, max = 20)
        val fakeRecords: List<MobileSegment.MobileRecord> = forge.aList(fakeRecordsSize) {
            forge.getForgery()
        }
        val fakeEnrichedRecords = fakeRecords
            .chunked(forge.anInt(min = 1, max = fakeRecordsSize))
            .map {
                EnrichedRecord(
                    rumContext.applicationId,
                    rumContext.sessionId,
                    rumContext.sessionId,
                    it
                )
            }
        val fakeBatchData = fakeEnrichedRecords
            .map { JsonParser.parseString(it.toJson()).asJsonObject }
            .map {
                val records = it.get(EnrichedRecord.RECORDS_KEY)
                    .asJsonArray
                records.forEach { record ->
                    record.asJsonObject.remove(BatchesToSegmentsMapper.TIMESTAMP_KEY)
                }
                it.add(EnrichedRecord.RECORDS_KEY, records)
                it
            }
            .map { it.toString().toByteArray() }

        // When
        assertThat(testedMapper.map(fakeBatchData)).isNull()
    }

    @Test
    fun `M drop the broken records W map { some records with missing timestamp key }`(
        forge: Forge
    ) {
        // Given
        val rumContext: SessionReplayRumContext = forge.getForgery()
        val fakeRecordsSize = forge.anInt(min = 20, max = 50)
        val fakeRecords: List<MobileSegment.MobileRecord> = forge.aList(fakeRecordsSize) {
            forge.getForgery()
        }
        val fakeEnrichedRecords = fakeRecords
            .chunked(forge.anInt(min = 1, max = fakeRecordsSize))
            .map {
                EnrichedRecord(
                    rumContext.applicationId,
                    rumContext.sessionId,
                    rumContext.sessionId,
                    it
                )
            }
        var removedRecords = 0
        val fakeBatchData = fakeEnrichedRecords
            .map { JsonParser.parseString(it.toJson()).asJsonObject }
            .mapIndexed { index, jsonObject ->
                if (index == 0) {
                    val records = jsonObject.get(EnrichedRecord.RECORDS_KEY)
                        .asJsonArray
                    removedRecords = records.size()
                    records.forEach { record ->
                        record.asJsonObject.remove(BatchesToSegmentsMapper.TIMESTAMP_KEY)
                    }
                    jsonObject.add(EnrichedRecord.RECORDS_KEY, records)
                    jsonObject
                } else {
                    jsonObject
                }
            }
            .map { it.toString().toByteArray() }

        // When
        val mappedSegment = testedMapper.map(fakeBatchData)
        val expectedRecordsSize = fakeRecords.size - removedRecords
        assertThat(mappedSegment?.first?.recordsCount?.toInt()).isEqualTo(expectedRecordsSize)
        val recordsAsJsonArray = mappedSegment?.second
            ?.getAsJsonArray(BatchesToSegmentsMapper.RECORDS_KEY)
        assertThat(recordsAsJsonArray?.size()).isEqualTo(expectedRecordsSize)
    }

    @Test
    fun `M return null W map { enriched record with missing application id key }`(
        forge: Forge
    ) {
        // Given
        val rumContext: SessionReplayRumContext = forge.getForgery()
        val fakeRecordsSize = forge.anInt(min = 10, max = 20)
        val fakeRecords: List<MobileSegment.MobileRecord> = forge.aList(fakeRecordsSize) {
            forge.getForgery()
        }
        val fakeEnrichedRecords = fakeRecords
            .chunked(forge.anInt(min = 1, max = fakeRecordsSize))
            .map {
                EnrichedRecord(
                    rumContext.applicationId,
                    rumContext.sessionId,
                    rumContext.sessionId,
                    it
                )
            }
        val fakeBatchData = fakeEnrichedRecords
            .map { JsonParser.parseString(it.toJson()).asJsonObject }
            .map {
                it.remove(EnrichedRecord.APPLICATION_ID_KEY)
                it
            }
            .map { it.toString().toByteArray() }
        // When
        assertThat(testedMapper.map(fakeBatchData)).isNull()
        argumentCaptor<() -> String> {
            verify(mockInternalLogger, times(fakeBatchData.size)).log(
                eq(InternalLogger.Level.ERROR),
                eq(InternalLogger.Target.TELEMETRY),
                capture(),
                eq(null),
                eq(true)
            )
            assertThat(firstValue.invoke()).isEqualTo(
                BatchesToSegmentsMapper.ILLEGAL_STATE_ENRICHED_RECORD_ERROR_MESSAGE
            )
        }
    }

    @Test
    fun `M drop the broken records W map { some enriched records with missing application key }`(
        forge: Forge
    ) {
        // Given
        val rumContext: SessionReplayRumContext = forge.getForgery()
        val fakeRecordsSize = forge.anInt(min = 20, max = 50)
        val fakeRecords: List<MobileSegment.MobileRecord> = forge.aList(fakeRecordsSize) {
            forge.getForgery()
        }
        val fakeEnrichedRecords = fakeRecords
            .chunked(forge.anInt(min = 1, max = fakeRecordsSize))
            .map {
                EnrichedRecord(
                    rumContext.applicationId,
                    rumContext.sessionId,
                    rumContext.sessionId,
                    it
                )
            }
        var removedRecords = 0
        val fakeBatchData = fakeEnrichedRecords
            .map { JsonParser.parseString(it.toJson()).asJsonObject }
            .mapIndexed { index, jsonObject ->
                if (index == 0) {
                    val records = jsonObject.get(EnrichedRecord.RECORDS_KEY)
                        .asJsonArray
                    removedRecords = records.size()
                    jsonObject.remove(EnrichedRecord.APPLICATION_ID_KEY)
                    jsonObject
                } else {
                    jsonObject
                }
            }
            .map { it.toString().toByteArray() }

        // When
        val mappedSegment = testedMapper.map(fakeBatchData)
        val expectedRecordsSize = fakeRecords.size - removedRecords
        assertThat(mappedSegment?.first?.recordsCount?.toInt()).isEqualTo(expectedRecordsSize)
        val recordsAsJsonArray = mappedSegment?.second
            ?.getAsJsonArray(BatchesToSegmentsMapper.RECORDS_KEY)
        assertThat(recordsAsJsonArray?.size()).isEqualTo(expectedRecordsSize)
    }

    @Test
    fun `M return null W map { enriched records with missing session id key }`(
        forge: Forge
    ) {
        // Given
        val rumContext: SessionReplayRumContext = forge.getForgery()
        val fakeRecordsSize = forge.anInt(min = 10, max = 20)
        val fakeRecords: List<MobileSegment.MobileRecord> = forge.aList(fakeRecordsSize) {
            forge.getForgery()
        }
        val fakeEnrichedRecords = fakeRecords
            .chunked(forge.anInt(min = 1, max = fakeRecordsSize))
            .map {
                EnrichedRecord(
                    rumContext.applicationId,
                    rumContext.sessionId,
                    rumContext.sessionId,
                    it
                )
            }
        val fakeBatchData = fakeEnrichedRecords
            .map { JsonParser.parseString(it.toJson()).asJsonObject }
            .map {
                it.remove(EnrichedRecord.SESSION_ID_KEY)
                it
            }
            .map { it.toString().toByteArray() }

        // When
        assertThat(testedMapper.map(fakeBatchData)).isNull()
        argumentCaptor<() -> String> {
            verify(mockInternalLogger, times(fakeBatchData.size)).log(
                eq(InternalLogger.Level.ERROR),
                eq(InternalLogger.Target.TELEMETRY),
                capture(),
                eq(null),
                eq(true)
            )
            assertThat(firstValue.invoke()).isEqualTo(
                BatchesToSegmentsMapper.ILLEGAL_STATE_ENRICHED_RECORD_ERROR_MESSAGE
            )
        }
    }

    @Test
    fun `M drop the broken records W map { some enriched records with missing session key }`(
        forge: Forge
    ) {
        // Given
        val rumContext: SessionReplayRumContext = forge.getForgery()
        val fakeRecordsSize = forge.anInt(min = 20, max = 50)
        val fakeRecords: List<MobileSegment.MobileRecord> = forge.aList(fakeRecordsSize) {
            forge.getForgery()
        }
        val fakeEnrichedRecords = fakeRecords
            .chunked(forge.anInt(min = 1, max = fakeRecordsSize))
            .map {
                EnrichedRecord(
                    rumContext.applicationId,
                    rumContext.sessionId,
                    rumContext.sessionId,
                    it
                )
            }
        var removedRecords = 0
        val fakeBatchData = fakeEnrichedRecords
            .map { JsonParser.parseString(it.toJson()).asJsonObject }
            .mapIndexed { index, jsonObject ->
                if (index == 0) {
                    val records = jsonObject.get(EnrichedRecord.RECORDS_KEY)
                        .asJsonArray
                    removedRecords = records.size()
                    jsonObject.remove(EnrichedRecord.SESSION_ID_KEY)
                    jsonObject
                } else {
                    jsonObject
                }
            }
            .map { it.toString().toByteArray() }

        // When
        val mappedSegment = testedMapper.map(fakeBatchData)
        val expectedRecordsSize = fakeRecords.size - removedRecords
        assertThat(mappedSegment?.first?.recordsCount?.toInt()).isEqualTo(expectedRecordsSize)
        val recordsAsJsonArray = mappedSegment?.second
            ?.getAsJsonArray(BatchesToSegmentsMapper.RECORDS_KEY)
        assertThat(recordsAsJsonArray?.size()).isEqualTo(expectedRecordsSize)
    }

    @Test
    fun `M return null W map {  enriched records with missing view id key }`(
        forge: Forge
    ) {
        // Given
        val rumContext: SessionReplayRumContext = forge.getForgery()
        val fakeRecordsSize = forge.anInt(min = 10, max = 20)
        val fakeRecords: List<MobileSegment.MobileRecord> = forge.aList(fakeRecordsSize) {
            forge.getForgery()
        }
        val fakeEnrichedRecords = fakeRecords
            .chunked(forge.anInt(min = 1, max = fakeRecordsSize))
            .map {
                EnrichedRecord(
                    rumContext.applicationId,
                    rumContext.sessionId,
                    rumContext.sessionId,
                    it
                )
            }
        val fakeBatchData = fakeEnrichedRecords
            .map { JsonParser.parseString(it.toJson()).asJsonObject }
            .map {
                it.remove(EnrichedRecord.VIEW_ID_KEY)
                it
            }
            .map { it.toString().toByteArray() }

        // When
        assertThat(testedMapper.map(fakeBatchData)).isNull()
        argumentCaptor<() -> String> {
            verify(mockInternalLogger, times(fakeBatchData.size)).log(
                eq(InternalLogger.Level.ERROR),
                eq(InternalLogger.Target.TELEMETRY),
                capture(),
                eq(null),
                eq(true)
            )
            assertThat(firstValue.invoke()).isEqualTo(
                BatchesToSegmentsMapper.ILLEGAL_STATE_ENRICHED_RECORD_ERROR_MESSAGE
            )
        }
    }

    @Test
    fun `M drop the broken records W map { some enriched records with missing view id key }`(
        forge: Forge
    ) {
        // Given
        val rumContext: SessionReplayRumContext = forge.getForgery()
        val fakeRecordsSize = forge.anInt(min = 20, max = 50)
        val fakeRecords: List<MobileSegment.MobileRecord> = forge.aList(fakeRecordsSize) {
            forge.getForgery()
        }
        val fakeEnrichedRecords = fakeRecords
            .chunked(forge.anInt(min = 1, max = fakeRecordsSize))
            .map {
                EnrichedRecord(
                    rumContext.applicationId,
                    rumContext.sessionId,
                    rumContext.sessionId,
                    it
                )
            }
        var removedRecords = 0
        val fakeBatchData = fakeEnrichedRecords
            .map { JsonParser.parseString(it.toJson()).asJsonObject }
            .mapIndexed { index, jsonObject ->
                if (index == 0) {
                    val records = jsonObject.get(EnrichedRecord.RECORDS_KEY)
                        .asJsonArray
                    removedRecords = records.size()
                    jsonObject.remove(EnrichedRecord.VIEW_ID_KEY)
                    jsonObject
                } else {
                    jsonObject
                }
            }
            .map { it.toString().toByteArray() }

        // When
        val mappedSegment = testedMapper.map(fakeBatchData)
        val expectedRecordsSize = fakeRecords.size - removedRecords
        assertThat(mappedSegment?.first?.recordsCount?.toInt()).isEqualTo(expectedRecordsSize)
        val recordsAsJsonArray = mappedSegment?.second
            ?.getAsJsonArray(BatchesToSegmentsMapper.RECORDS_KEY)
        assertThat(recordsAsJsonArray?.size()).isEqualTo(expectedRecordsSize)
    }

    @Test
    fun `M drop the broken records W map { some records with wrong timestamp format key }`(
        forge: Forge
    ) {
        // Given
        val rumContext: SessionReplayRumContext = forge.getForgery()
        val fakeRecordsSize = forge.anInt(min = 20, max = 50)
        val fakeRecords: List<MobileSegment.MobileRecord> = forge.aList(fakeRecordsSize) {
            forge.getForgery()
        }
        val fakeEnrichedRecords = fakeRecords
            .chunked(forge.anInt(min = 1, max = fakeRecordsSize))
            .map {
                EnrichedRecord(
                    rumContext.applicationId,
                    rumContext.sessionId,
                    rumContext.sessionId,
                    it
                )
            }
        var removedRecords = 0
        val fakeBatchData = fakeEnrichedRecords
            .map { JsonParser.parseString(it.toJson()).asJsonObject }
            .mapIndexed { index, jsonObject ->
                if (index == 0) {
                    val records = jsonObject.get(EnrichedRecord.RECORDS_KEY)
                        .asJsonArray
                    removedRecords = records.size()
                    records.forEach { record ->
                        record.asJsonObject.add(
                            BatchesToSegmentsMapper.TIMESTAMP_KEY,
                            JsonPrimitive(forge.anAlphabeticalString())
                        )
                    }
                    jsonObject.add(EnrichedRecord.RECORDS_KEY, records)
                    jsonObject
                } else {
                    jsonObject
                }
            }
            .map { it.toString().toByteArray() }

        // When
        val mappedSegment = testedMapper.map(fakeBatchData)
        val expectedRecordsSize = fakeRecords.size - removedRecords
        assertThat(mappedSegment?.first?.recordsCount?.toInt()).isEqualTo(expectedRecordsSize)
        val recordsAsJsonArray = mappedSegment?.second
            ?.getAsJsonArray(BatchesToSegmentsMapper.RECORDS_KEY)
        assertThat(recordsAsJsonArray?.size()).isEqualTo(expectedRecordsSize)
    }

    @Test
    fun `M drop the broken records W map { some enriched records with wrong records format }`(
        forge: Forge
    ) {
        // Given
        val rumContext: SessionReplayRumContext = forge.getForgery()
        val fakeRecordsSize = forge.anInt(min = 20, max = 50)
        val fakeRecords: List<MobileSegment.MobileRecord> = forge.aList(fakeRecordsSize) {
            forge.getForgery()
        }
        val fakeEnrichedRecords = fakeRecords
            .chunked(forge.anInt(min = 1, max = fakeRecordsSize))
            .map {
                EnrichedRecord(
                    rumContext.applicationId,
                    rumContext.sessionId,
                    rumContext.sessionId,
                    it
                )
            }
        var removedRecords = 0
        val fakeBatchData = fakeEnrichedRecords
            .map { JsonParser.parseString(it.toJson()).asJsonObject }
            .mapIndexed { index, jsonObject ->
                if (index == 0) {
                    val records = jsonObject.get(EnrichedRecord.RECORDS_KEY)
                        .asJsonArray
                    removedRecords = records.size()
                    jsonObject.add(EnrichedRecord.RECORDS_KEY, forge.getForgery<JsonPrimitive>())
                    jsonObject
                } else {
                    jsonObject
                }
            }
            .map { it.toString().toByteArray() }

        // When
        val mappedSegment = testedMapper.map(fakeBatchData)
        val expectedRecordsSize = fakeRecords.size - removedRecords
        assertThat(mappedSegment?.first?.recordsCount?.toInt()).isEqualTo(expectedRecordsSize)
        val recordsAsJsonArray = mappedSegment?.second
            ?.getAsJsonArray(BatchesToSegmentsMapper.RECORDS_KEY)
        assertThat(recordsAsJsonArray?.size()).isEqualTo(expectedRecordsSize)
    }

    // region Internal

    private fun EnrichedRecord.toSegment(): MobileSegment {
        return MobileSegment(
            MobileSegment.Application(applicationId),
            MobileSegment.Session(sessionId),
            MobileSegment.View(viewId),
            startTimestamp(),
            endTimestamp(),
            records.size.toLong(),
            null,
            hasFullSnapshot(),
            MobileSegment.Source.ANDROID,
            records
        )
    }

    private fun EnrichedRecord.startTimestamp(): Long {
        return this.records.first().timestamp()
    }

    private fun EnrichedRecord.endTimestamp(): Long {
        return this.records.last().timestamp()
    }

    private fun EnrichedRecord.hasFullSnapshot(): Boolean {
        return this.records.hasFullSnapshot()
    }

    private fun List<MobileSegment.MobileRecord>.hasFullSnapshot(): Boolean {
        return firstOrNull { it is MobileSegment.MobileRecord.MobileFullSnapshotRecord } != null
    }

    private fun MobileSegment.MobileRecord.timestamp(): Long {
        return when (this) {
            is MobileSegment.MobileRecord.MetaRecord -> this.timestamp
            is MobileSegment.MobileRecord.FocusRecord -> this.timestamp
            is MobileSegment.MobileRecord.ViewEndRecord -> this.timestamp
            is MobileSegment.MobileRecord.MobileFullSnapshotRecord -> this.timestamp
            is MobileSegment.MobileRecord.MobileIncrementalSnapshotRecord -> this.timestamp
            is MobileSegment.MobileRecord.VisualViewportRecord -> this.timestamp
        }
    }

    // endregion
}
