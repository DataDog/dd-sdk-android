/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal

import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.processor.EnrichedRecord
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
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(ForgeExtension::class),
    ExtendWith(MockitoExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class SessionReplayRecordCallbackTest {

    @Mock
    lateinit var mockDatadogCore: FeatureSdkCore
    lateinit var testedRecordCallback: SessionReplayRecordCallback
    lateinit var fakeEnrichedRecord: EnrichedRecord

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeEnrichedRecord = forge.forgeFakeValidEnrichedRecord()
        testedRecordCallback = SessionReplayRecordCallback(mockDatadogCore)
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `M update session replay context W onRecordForViewSent`() {
        // When
        testedRecordCallback.onRecordForViewSent(fakeEnrichedRecord)

        // Then
        argumentCaptor<(MutableMap<String, Any?>) -> Unit> {
            verify(mockDatadogCore).updateFeatureContext(
                eq(Feature.SESSION_REPLAY_FEATURE_NAME),
                capture()
            )

            val featureContext = mutableMapOf<String, Any?>()
            lastValue.invoke(featureContext)

            val viewMetadata = featureContext[fakeEnrichedRecord.viewId] as? Map<String, Any?>
            assertThat(viewMetadata).isNotNull
            val hasReplay = viewMetadata?.get(SessionReplayRecordCallback.HAS_REPLAY_KEY)
                as? Boolean
            assertThat(hasReplay).isTrue
            val recordsCount = viewMetadata?.get(SessionReplayRecordCallback.VIEW_RECORDS_COUNT_KEY)
                as? Long
            assertThat(recordsCount).isEqualTo(fakeEnrichedRecord.records.size.toLong())
        }
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `M do nothing W onRecordForViewSent{empty EnrichedRecord}`(forge: Forge) {
        // Given
        val fakeEmptyEnrichedRecord = forge.forgeEmptyValidEnrichedRecord()

        // When
        testedRecordCallback.onRecordForViewSent(fakeEmptyEnrichedRecord)

        // Then
        verifyNoInteractions(mockDatadogCore)
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `M discard prev keys if the types are incorrect onRecordForViewSent`(forge: Forge) {
        // When
        testedRecordCallback.onRecordForViewSent(fakeEnrichedRecord)

        // Then
        argumentCaptor<(MutableMap<String, Any?>) -> Unit> {
            verify(mockDatadogCore).updateFeatureContext(
                eq(Feature.SESSION_REPLAY_FEATURE_NAME),
                capture()
            )

            val featureContext = mutableMapOf<String, Any?>(
                SessionReplayRecordCallback.HAS_REPLAY_KEY to forge.aString(),
                SessionReplayRecordCallback.VIEW_RECORDS_COUNT_KEY to forge.aString()
            )
            lastValue.invoke(featureContext)

            val viewMetadata = featureContext[fakeEnrichedRecord.viewId] as? Map<String, Any?>
            assertThat(viewMetadata).isNotNull
            val hasReplay = viewMetadata?.get(SessionReplayRecordCallback.HAS_REPLAY_KEY)
                as? Boolean
            assertThat(hasReplay).isTrue
            val recordsCount = viewMetadata?.get(SessionReplayRecordCallback.VIEW_RECORDS_COUNT_KEY)
                as? Long
            assertThat(recordsCount).isEqualTo(fakeEnrichedRecord.records.size.toLong())
        }
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `M increase the records count W onRecordForViewSent`(forge: Forge) {
        // Given
        val fakeViewId = forge.aString()
        val fakeNumberOfUpdates = forge.anInt(min = 1, max = 10)
        val fakeEnrichedRecords = forge.aList(size = fakeNumberOfUpdates) {
            forge.forgeFakeValidEnrichedRecord().copy(viewId = fakeViewId)
        }
        // When
        fakeEnrichedRecords.forEach {
            testedRecordCallback.onRecordForViewSent(it)
        }

        // Then
        argumentCaptor<(MutableMap<String, Any?>) -> Unit> {
            verify(mockDatadogCore, times(fakeNumberOfUpdates)).updateFeatureContext(
                eq(Feature.SESSION_REPLAY_FEATURE_NAME),
                capture()
            )

            val featureContext = mutableMapOf<String, Any?>()
            this.allValues.forEach {
                it.invoke(featureContext)
            }
            val viewMetadata = featureContext[fakeViewId] as? Map<String, Any?>
            assertThat(viewMetadata).isNotNull
            val hasReplay = viewMetadata?.get(SessionReplayRecordCallback.HAS_REPLAY_KEY)
                as? Boolean
            assertThat(hasReplay).isTrue
            val recordsCount = viewMetadata?.get(SessionReplayRecordCallback.VIEW_RECORDS_COUNT_KEY)
                as? Long
            assertThat(recordsCount)
                .isEqualTo(fakeEnrichedRecords.sumOf { it.records.size.toLong() })
        }
    }

    private fun Forge.forgeFakeValidEnrichedRecord(): EnrichedRecord {
        return getForgery<EnrichedRecord>()
            .copy(records = aList(size = anInt(min = 1, max = 10)) { getForgery() })
    }

    private fun Forge.forgeEmptyValidEnrichedRecord(): EnrichedRecord {
        return getForgery<EnrichedRecord>().copy(records = emptyList())
    }
}
