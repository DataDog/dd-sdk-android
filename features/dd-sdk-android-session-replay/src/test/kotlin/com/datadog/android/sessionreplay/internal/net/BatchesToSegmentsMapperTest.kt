/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.net

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.processor.EnrichedRecord
import com.datadog.android.sessionreplay.internal.utils.SessionReplayRumContext
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.utils.verifyLog
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import fr.xgouchet.elmyr.jvm.ext.aTimestamp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.times
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class BatchesToSegmentsMapperTest {

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Forgery
    lateinit var datadogContext: DatadogContext

    private lateinit var testedMapper: BatchesToSegmentsMapper

    var fakeSegmentSource: MobileSegment.Source? = null

    @BeforeEach
    fun `set up`(forge: Forge) {
        testedMapper = BatchesToSegmentsMapper(mockInternalLogger)

        fakeSegmentSource = forge.aNullable { aValueFrom(MobileSegment.Source::class.java) }

        datadogContext = datadogContext.copy(
            source = fakeSegmentSource?.toJson()?.asString ?: forge.aString()
        )
    }

    @Test
    fun `M group the records into segments by context W map`(forge: Forge) {
        // Given
        val fakeRecordsSize = forge.anInt(min = 10, max = 20)
        val fakeRecords = forge.aList<MobileSegment.MobileRecord>(fakeRecordsSize) {
            forge.getForgery()
        }.sortedBy { it.timestamp() }
        val fakeEnrichedRecords = fakeRecords
            .chunked(forge.anInt(min = 1, max = fakeRecordsSize))
            .map {
                val fakeRumContext: SessionReplayRumContext = forge.getForgery()
                EnrichedRecord(
                    fakeRumContext.applicationId,
                    fakeRumContext.sessionId,
                    fakeRumContext.sessionId,
                    it
                )
            }
        val fakeExpectedPairs = fakeEnrichedRecords.map {
            Pair(it.toSegment().copy(records = emptyList()), it.toSegment().toJson())
        }
        val fakeBatchData = fakeEnrichedRecords.map { it.toJson().toByteArray() }

        // When
        val mappedSegments = testedMapper.map(datadogContext, fakeBatchData)

        // Then
        assertThat(mappedSegments.size).isEqualTo(fakeEnrichedRecords.size)
        mappedSegments.forEachIndexed { index, pair ->
            assertThat(pair.first).isEqualTo(fakeExpectedPairs[index].first)
            assertThat(pair.second.toString()).isEqualTo(fakeExpectedPairs[index].second.toString())
        }
    }

    @Test
    fun `M keep the same order W map { records have same timestamps }`(forge: Forge) {
        // Given
        val fakeRecordsSize = forge.anInt(min = 10, max = 20)
        val fakeTimestamp = forge.aTimestamp()
        val fakeRecords: List<MobileSegment.MobileRecord> = forge.aList(fakeRecordsSize) {
            forge.getForgery<MobileSegment.MobileRecord>().copy(fakeTimestamp)
        }
        val fakeEnrichedRecords = fakeRecords
            .chunked(forge.anInt(min = 1, max = fakeRecordsSize))
            .map {
                val rumContext: SessionReplayRumContext = forge.getForgery()
                EnrichedRecord(
                    rumContext.applicationId,
                    rumContext.sessionId,
                    rumContext.sessionId,
                    it
                )
            }
        val fakeExpectedPairs = fakeEnrichedRecords.map {
            Pair(it.toSegment().copy(records = emptyList()), it.toSegment().toJson())
        }
        val fakeBatchData = fakeEnrichedRecords.map { it.toJson().toByteArray() }

        // When
        val mappedSegments = testedMapper.map(datadogContext, fakeBatchData)

        // Then
        mappedSegments.forEachIndexed { index, pair ->
            assertThat(pair.first).isEqualTo(fakeExpectedPairs[index].first)
            assertThat(pair.second.toString()).isEqualTo(fakeExpectedPairs[index].second.toString())
        }
    }

    @Test
    fun `M use the first record in the list as start timestamp W map`(
        forge: Forge
    ) {
        val fakeRecordsSize = forge.anInt(min = 10, max = 20)
        val fakeRecords = forge.aList<MobileSegment.MobileRecord>(fakeRecordsSize) {
            forge.getForgery()
        }.sortedBy { it.timestamp() }
        val fakeEnrichedRecords = fakeRecords
            .chunked(forge.anInt(min = 1, max = fakeRecordsSize))
            .map {
                val rumContext: SessionReplayRumContext = forge.getForgery()
                EnrichedRecord(
                    rumContext.applicationId,
                    rumContext.sessionId,
                    rumContext.sessionId,
                    it
                )
            }
        val fakeExpectedPairs = fakeEnrichedRecords.map {
            Pair(it.toSegment().copy(records = emptyList()), it.toSegment().toJson())
        }
        val fakeBatchData = fakeEnrichedRecords.map { it.toJson().toByteArray() }

        // When
        val mappedSegments = testedMapper.map(datadogContext, fakeBatchData)

        // Then
        mappedSegments.forEachIndexed { index, pair ->
            val serializedRecordStartTimestamp = pair.second.getAsJsonPrimitive("start")
                ?.asLong
            assertThat(pair.first.start).isEqualTo(serializedRecordStartTimestamp)
            assertThat(pair.first).isEqualTo(fakeExpectedPairs[index].first)
            assertThat(pair.second.toString()).isEqualTo(fakeExpectedPairs[index].second.toString())
        }
    }

    @Test
    fun `M use the last record in the list as end timestamp W map`(
        forge: Forge
    ) {
        // Given
        val fakeRecordsSize = forge.anInt(min = 10, max = 20)
        val fakeRecords = forge.aList<MobileSegment.MobileRecord>(fakeRecordsSize) { forge.getForgery() }
            .sortedBy { it.timestamp() }
        val fakeEnrichedRecords = fakeRecords
            .chunked(forge.anInt(min = 1, max = fakeRecordsSize))
            .map {
                val rumContext: SessionReplayRumContext = forge.getForgery()
                EnrichedRecord(
                    rumContext.applicationId,
                    rumContext.sessionId,
                    rumContext.sessionId,
                    it
                )
            }
        val fakeExpectedPairs = fakeEnrichedRecords.map {
            Pair(it.toSegment().copy(records = emptyList()), it.toSegment().toJson())
        }
        val fakeBatchData = fakeEnrichedRecords.map { it.toJson().toByteArray() }

        // When
        val mappedSegments = testedMapper.map(datadogContext, fakeBatchData)

        // Then
        mappedSegments.forEachIndexed { index, pair ->
            val serializedRecordEndTimestamp = pair.second.getAsJsonPrimitive("end")
                ?.asLong
            assertThat(pair.first.end).isEqualTo(serializedRecordEndTimestamp)
            assertThat(pair.first).isEqualTo(fakeExpectedPairs[index].first)
            assertThat(pair.second.toString()).isEqualTo(fakeExpectedPairs[index].second.toString())
        }
    }

    @Test
    fun `M return empty list W map{ empty records }`(
        forge: Forge
    ) {
        // Given
        val fakeEnrichedRecords: List<EnrichedRecord> =
            forge.aList(forge.anInt(min = 10, max = 30)) {
                forge.getForgery<EnrichedRecord>().copy(records = emptyList())
            }
        val fakeBatchData = fakeEnrichedRecords.map { it.toJson().toByteArray() }

        // When
        assertThat(testedMapper.map(datadogContext, fakeBatchData)).isEmpty()
    }

    @Test
    fun `M return empty list W map { broken serialized records }`(
        forge: Forge
    ) {
        // Given
        val fakeBatchData = forge.aList { forge.anAlphabeticalString().toByteArray() }

        // When
        assertThat(testedMapper.map(datadogContext, fakeBatchData)).isEmpty()
    }

    @Test
    fun `M return empty list W map { all records with missing timestamp key }`(
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
            .asSequence()
            .map { JsonParser.parseString(it.toJson()).asJsonObject }
            .onEach {
                val records = it.get(EnrichedRecord.RECORDS_KEY)
                    .asJsonArray
                records.forEach { record ->
                    record.asJsonObject.remove(BatchesToSegmentsMapper.TIMESTAMP_KEY)
                }
                it.add(EnrichedRecord.RECORDS_KEY, records)
            }
            .map { it.toString().toByteArray() }
            .toList()

        // When
        assertThat(testedMapper.map(datadogContext, fakeBatchData)).isEmpty()
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
            .asSequence()
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
            .toList()

        // When
        val mappedSegments = testedMapper.map(datadogContext, fakeBatchData)
        val expectedRecordsSize = fakeRecords.size - removedRecords
        assertThat(mappedSegments.size).isEqualTo(1)
        assertThat(mappedSegments[0].first.recordsCount.toInt()).isEqualTo(expectedRecordsSize)
        val recordsAsJsonArray = mappedSegments[0].second
            .getAsJsonArray(BatchesToSegmentsMapper.RECORDS_KEY)
        assertThat(recordsAsJsonArray?.size()).isEqualTo(expectedRecordsSize)
    }

    @ParameterizedTest
    @MethodSource("invalidContextParameters")
    fun `M drop invalid context W map {all enriched records invalid}`(
        contextKey: String,
        invalidValue: InvalidContextValue,
        @Forgery fakeEnrichedRecord: EnrichedRecord,
        forge: Forge
    ) {
        // Given
        val fakeBrokenRecord = fakeEnrichedRecord.withInvalidContext(contextKey, invalidValue, forge)

        // When
        val mappedSegments = testedMapper.map(datadogContext, listOf(fakeBrokenRecord))

        // Then
        assertThat(mappedSegments).isEmpty()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.TELEMETRY,
            BatchesToSegmentsMapper.ILLEGAL_STATE_ENRICHED_RECORD_ERROR_MESSAGE,
            onlyOnce = true
        )
    }

    @ParameterizedTest
    @MethodSource("invalidContextParameters")
    fun `M preserve valid records W map {mixed valid and invalid contexts}`(
        contextKey: String,
        invalidValue: InvalidContextValue,
        @Forgery fakeEnrichedRecord: EnrichedRecord,
        forge: Forge
    ) {
        // Given
        val fakeSortedRecord = fakeEnrichedRecord.copy(records = fakeEnrichedRecord.records.sortedBy { it.timestamp() })
        val fakeBrokenRecord = fakeSortedRecord.withInvalidContext(contextKey, invalidValue, forge)
        val fakeValidRecord = fakeSortedRecord.toJson().toByteArray()
        val fakeExpectedSegment = fakeSortedRecord.toSegment()

        // When
        val mappedSegments = testedMapper.map(datadogContext, listOf(fakeBrokenRecord, fakeValidRecord))

        // Then
        assertThat(mappedSegments).hasSize(1)
        assertThat(mappedSegments.single().first).isEqualTo(fakeExpectedSegment.copy(records = emptyList()))
        assertThat(mappedSegments.single().second.toString()).isEqualTo(fakeExpectedSegment.toJson().toString())
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.TELEMETRY,
            BatchesToSegmentsMapper.ILLEGAL_STATE_ENRICHED_RECORD_ERROR_MESSAGE,
            onlyOnce = true
        )
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
            .asSequence()
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
            .toList()

        // When
        val mappedSegments = testedMapper.map(datadogContext, fakeBatchData)

        // Then
        val expectedRecordsSize = fakeRecords.size - removedRecords
        assertThat(mappedSegments.size).isEqualTo(1)
        assertThat(mappedSegments[0].first.recordsCount.toInt()).isEqualTo(expectedRecordsSize)
        val recordsAsJsonArray = mappedSegments[0].second
            .getAsJsonArray(BatchesToSegmentsMapper.RECORDS_KEY)
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
            .asSequence()
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
            .toList()

        // When
        val mappedSegments = testedMapper.map(datadogContext, fakeBatchData)

        // Then
        val expectedRecordsSize = fakeRecords.size - removedRecords
        assertThat(mappedSegments[0].first.recordsCount.toInt()).isEqualTo(expectedRecordsSize)
        val recordsAsJsonArray = mappedSegments[0].second
            .getAsJsonArray(BatchesToSegmentsMapper.RECORDS_KEY)
        assertThat(recordsAsJsonArray?.size()).isEqualTo(expectedRecordsSize)
    }

    // region Internal

    private fun EnrichedRecord.withInvalidContext(
        contextKey: String,
        invalidValue: InvalidContextValue,
        forge: Forge
    ): ByteArray {
        val json = JsonParser.parseString(toJson()).asJsonObject
        when (invalidValue) {
            InvalidContextValue.MISSING -> json.remove(contextKey)
            InvalidContextValue.NULL -> json.add(contextKey, JsonNull.INSTANCE)
            InvalidContextValue.NUMBER -> json.addProperty(contextKey, forge.aLong())
            InvalidContextValue.BOOLEAN -> json.addProperty(contextKey, forge.aBool())
            InvalidContextValue.OBJECT -> json.add(contextKey, JsonObject())
            InvalidContextValue.ARRAY -> json.add(contextKey, JsonArray().apply { add(json.get(contextKey)) })
            InvalidContextValue.EMPTY -> json.addProperty(contextKey, "")
            InvalidContextValue.BLANK -> json.addProperty(contextKey, " ")
            InvalidContextValue.ZERO_UUID -> json.addProperty(contextKey, "00000000-0000-0000-0000-000000000000")
        }
        return json.toString().toByteArray()
    }

    private fun EnrichedRecord.toSegment(): MobileSegment {
        return MobileSegment(
            application = MobileSegment.Application(applicationId),
            session = MobileSegment.Session(sessionId),
            view = MobileSegment.View(viewId),
            start = startTimestamp(),
            end = endTimestamp(),
            recordsCount = records.size.toLong(),
            indexInView = null,
            hasFullSnapshot = hasFullSnapshot(),
            source = fakeSegmentSource ?: MobileSegment.Source.ANDROID,
            records = records
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
        return any { it is MobileSegment.MobileRecord.MobileFullSnapshotRecord }
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

    private fun MobileSegment.MobileRecord.copy(timestamp: Long): MobileSegment.MobileRecord {
        return when (this) {
            is MobileSegment.MobileRecord.MetaRecord -> this.copy(timestamp = timestamp)
            is MobileSegment.MobileRecord.FocusRecord -> this.copy(timestamp = timestamp)
            is MobileSegment.MobileRecord.ViewEndRecord -> this.copy(timestamp = timestamp)
            is MobileSegment.MobileRecord.MobileFullSnapshotRecord ->
                this.copy(timestamp = timestamp)
            is MobileSegment.MobileRecord.MobileIncrementalSnapshotRecord ->
                this.copy(timestamp = timestamp)
            is MobileSegment.MobileRecord.VisualViewportRecord -> this.copy(timestamp = timestamp)
        }
    }

    // endregion

    enum class InvalidContextValue {
        MISSING,
        NULL,
        NUMBER,
        BOOLEAN,
        OBJECT,
        ARRAY,
        EMPTY,
        BLANK,
        ZERO_UUID
    }

    companion object {
        @JvmStatic
        fun invalidContextParameters(): List<Arguments> = listOf(
            EnrichedRecord.APPLICATION_ID_KEY,
            EnrichedRecord.SESSION_ID_KEY,
            EnrichedRecord.VIEW_ID_KEY
        ).flatMap { contextKey ->
            InvalidContextValue.entries.map { Arguments.of(contextKey, it) }
        }
    }
}
