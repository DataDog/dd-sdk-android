/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.net

import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.processor.EnrichedRecord
import com.datadog.android.sessionreplay.utils.ForgeConfigurator
import com.google.gson.JsonParser
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.LinkedList
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class BatchesToSegmentMapperTest {

    lateinit var testedMapper: BatchesToSegmentsMapper

    @BeforeEach
    fun `set up`() {
        testedMapper = BatchesToSegmentsMapper()
    }

    @Test
    fun `M generate segment to jsonSegment pairs W groupDataIntoSegments`(forge: Forge) {
        // Given
        val numberOfDifferentContexts = forge.anInt(min = 2, max = 10)
        val fakeEnrichedRecords: List<EnrichedRecord> = forge.aList(numberOfDifferentContexts) {
            forge.getForgery()
        }
        val fakeBatchData = fakeEnrichedRecords.map { it.toJson().toByteArray() }
        val expectedEmptySegments = fakeEnrichedRecords.map {
            it.toSegment().copy(records = emptyList())
        }
        val expectedSerializedSegments = fakeEnrichedRecords.map {
            it.toSegment().toJson().asJsonObject.toString()
        }

        // When
        val mappedSegments = testedMapper.map(fakeBatchData)

        // Then
        assertThat(mappedSegments.size).isEqualTo(fakeBatchData.size)
        val segments = mappedSegments.map { it.first }
        val serializedSegments = mappedSegments.map { it.second.toString() }
        assertThat(segments).isEqualTo(expectedEmptySegments)
        assertThat(serializedSegments).isEqualTo(expectedSerializedSegments)
    }

    @Test
    fun `M group the segments by context W groupDataIntoSegments`(forge: Forge) {
        // Given
        val fakeRecords: List<MobileSegment.MobileRecord> = forge.aList(
            forge.anInt(min = 30, max = 100)
        ) {
            forge.getForgery()
        }
        val chunkSize = fakeRecords.size / 4
        val expectedEnrichedRecords: LinkedList<EnrichedRecord> = LinkedList()
        val fakeEnrichedRecords: List<EnrichedRecord> = fakeRecords
            .chunked(chunkSize)
            .map { it.sortedBy { record -> record.timestamp() } }
            .flatMap {
                val rootRecord = forge.getForgery<EnrichedRecord>().copy(records = it)
                expectedEnrichedRecords.add(rootRecord)
                val subChunkSize = it.size / 2
                if (subChunkSize > 0) {
                    it.chunked(subChunkSize).map { records ->
                        rootRecord.copy(records = records)
                    }
                } else {
                    listOf(rootRecord)
                }
            }

        val fakeBatchData = fakeEnrichedRecords
            .map { it.toJson().toByteArray() }
        val expectedSegments = expectedEnrichedRecords.map {
            it.toSegment()
        }
        val expectedEmptySegments = expectedSegments.map { it.copy(records = emptyList()) }
        val expectedSerializedSegments = expectedSegments.map { it.toJson().toString() }

        // When
        val mappedSegments = testedMapper.map(fakeBatchData)

        // Then
        assertThat(mappedSegments.size).isEqualTo(expectedEnrichedRecords.size)
        val segments = mappedSegments.map { it.first }
        val serializedSegments = mappedSegments.map { it.second.toString() }
        assertThat(segments).isEqualTo(expectedEmptySegments)
        assertThat(serializedSegments).isEqualTo(expectedSerializedSegments)
    }

    @Test
    fun `M use the first record in the list as start timestamp W groupDataIntoSegments`(
        forge: Forge
    ) {
        // Given
        val fakeEnrichedRecords: List<EnrichedRecord> = forge
            .aList<EnrichedRecord>(size = 1) {
                forge.getForgery()
            }
            .map {
                it.copy(records = it.records.sortedBy { record -> record.timestamp() })
            }
        val fakeBatchData = fakeEnrichedRecords.map { it.toJson().toByteArray() }
        val expectedStartTimestamp = fakeEnrichedRecords[0].records.first().timestamp()

        // When
        val mappedSegments = testedMapper.map(fakeBatchData)

        // Then
        assertThat(mappedSegments.size).isEqualTo(1)
        assertThat(mappedSegments[0].first.start).isEqualTo(expectedStartTimestamp)
        val serializedRecordStartTimestamp = mappedSegments[0].second
            .getAsJsonPrimitive("start").asLong
        assertThat(serializedRecordStartTimestamp).isEqualTo(expectedStartTimestamp)
    }

    @Test
    fun `M use the last record in the list as end timestamp W groupDataIntoSegments`(
        forge: Forge
    ) {
        // Given
        val fakeEnrichedRecords: List<EnrichedRecord> = forge
            .aList<EnrichedRecord>(size = 1) {
                forge.getForgery()
            }
            .map {
                it.copy(records = it.records.sortedBy { record -> record.timestamp() })
            }
        val fakeBatchData = fakeEnrichedRecords.map { it.toJson().toByteArray() }
        val expectedEndTimestamp = fakeEnrichedRecords[0].records.last().timestamp()

        // When
        val mappedSegments = testedMapper.map(fakeBatchData)

        // Then
        assertThat(mappedSegments.size).isEqualTo(1)
        assertThat(mappedSegments[0].first.end).isEqualTo(expectedEndTimestamp)
        val serializedRecordStartTimestamp = mappedSegments[0].second
            .getAsJsonPrimitive("end").asLong
        assertThat(serializedRecordStartTimestamp).isEqualTo(expectedEndTimestamp)
    }

    @Test
    fun `M return empty list W groupDataIntoSegments { empty records }`(
        forge: Forge
    ) {
        // Given
        val fakeEnrichedRecords: List<EnrichedRecord> =
            forge.aList(forge.anInt(min = 10, max = 30)) {
                forge.getForgery<EnrichedRecord>().copy(records = emptyList())
            }
        val fakeBatchData = fakeEnrichedRecords.map { it.toJson().toByteArray() }

        // When
        val mappedSegments = testedMapper.map(fakeBatchData)

        // Then
        assertThat(mappedSegments).isEmpty()
    }

    @Test
    fun `M return empty list W groupDataIntoSegments { broken serialized records }`(
        forge: Forge
    ) {
        // Given
        val fakeBatchData = forge.aList { forge.anAlphabeticalString().toByteArray() }

        // When
        val mappedSegments = testedMapper.map(fakeBatchData)

        // Then
        assertThat(mappedSegments).isEmpty()
    }

    @Test
    fun `M return empty list W groupDataIntoSegments { records with missing timestamp key }`(
        forge: Forge
    ) {
        // Given
        val fakeBatchData = forge
            .aList<EnrichedRecord>(size = 1) {
                forge.getForgery()
            }
            .map { JsonParser.parseString(it.toJson()).asJsonObject }
            .map {
                val records = it.get(EnrichedRecord.RECORDS_KEY)
                    .asJsonArray
                records.forEach { record ->
                    record.asJsonObject.remove(BatchesToSegmentsMapper.TIMESTAMP_KEY)
                }
                it.add(EnrichedRecord.RECORDS_KEY, records)
            }
            .map { it.toString().toByteArray() }

        // When
        val mappedSegments = testedMapper.map(fakeBatchData)

        // Then
        assertThat(mappedSegments).isEmpty()
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
