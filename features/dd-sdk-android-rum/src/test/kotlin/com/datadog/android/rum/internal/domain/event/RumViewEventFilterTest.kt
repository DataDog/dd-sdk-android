/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event

import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.rum.utils.forge.Configurator
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
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.UUID

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumViewEventFilterTest {

    private lateinit var testedFilter: RumViewEventFilter

    @Mock
    lateinit var mockEventMetaDeserializer: RumEventMetaDeserializer

    @BeforeEach
    fun `set up`() {
        testedFilter = RumViewEventFilter(mockEventMetaDeserializer)
    }

    @Test
    fun `M leave batch untouched W filterOutRedundantViewEvents() { no events with metadata }`(
        forge: Forge
    ) {
        // Given
        val batch = forge.aList { RawBatchEvent(data = aString().toByteArray()) }
        whenever(mockEventMetaDeserializer.deserialize(any())) doReturn null

        // When
        val result = testedFilter.filterOutRedundantViewEvents(batch)

        // Then
        assertThat(result).isEqualTo(batch)
    }

    @Test
    fun `M keep view event with max version W filterOutRedundantViewEvents() { batch of same view event }`(
        @Forgery fakeViewId: UUID,
        forge: Forge
    ) {
        // Given
        val metas = forge.aViewEventMetaList(viewIds = setOf(fakeViewId.toString()))
        val batch = metas.map {
            RawBatchEvent(
                data = forge.aString().toByteArray(),
                metadata = it.toBytes()
            )
        }
        metas.forEach {
            whenever(mockEventMetaDeserializer.deserialize(it.toBytes())) doReturn it
        }

        val expectedViewEvent = batch
            .find { element -> element.metadata.contentEquals(metas.maxBy { it.documentVersion }.toBytes()) }

        // When
        val result = testedFilter.filterOutRedundantViewEvents(batch)

        // Then
        assertThat(result).containsOnly(expectedViewEvent)
    }

    @Test
    fun `M keep view event W hasAccessibility true { regardless of doc version }`(
        forge: Forge
    ) {
        // Given
        val viewEventMetas = forge.aList { getForgery<UUID>().toString() }.toSet()
            .flatMap { forge.aList { getForgery<RumEventMeta.View>().copy(viewId = it, hasAccessibility = true) } }

        val viewEvents = viewEventMetas.map {
            RawBatchEvent(
                data = forge.aString().toByteArray(),
                metadata = it.toBytes()
            )
        }
        val nonViewEvents = forge.aList {
            RawBatchEvent(
                data = forge.aString().toByteArray(),
                metadata = forge.aString().toByteArray()
            )
        }
        val batch = forge.shuffle(nonViewEvents + viewEvents)
        viewEventMetas.forEach {
            whenever(mockEventMetaDeserializer.deserialize(it.toBytes())) doReturn it
        }

        // When
        val result = testedFilter.filterOutRedundantViewEvents(batch)

        // Then
        assertThat(result).containsExactlyElementsOf(batch)
    }

    @Test
    fun `M keep only view event with max doc version W filterOutRedundantViewEvents() { mixed batch }`(
        forge: Forge
    ) {
        // Given
        val viewEventMetas = forge.aViewEventMetaList()
        val viewEvents = viewEventMetas.map {
            RawBatchEvent(
                data = forge.aString().toByteArray(),
                metadata = it.toBytes()
            )
        }
        val nonViewEvents = forge.aList {
            RawBatchEvent(
                data = forge.aString().toByteArray(),
                metadata = forge.aString().toByteArray()
            )
        }
        val batch = forge.shuffle(nonViewEvents + viewEvents)
        viewEventMetas.forEach {
            whenever(mockEventMetaDeserializer.deserialize(it.toBytes())) doReturn it
        }
        val expectedViewMetasToKeep = viewEventMetas.groupBy { it.viewId }
            .mapValues { element -> element.value.maxBy { it.documentVersion } }
            .values
        val expectedViewEventsToDrop = viewEvents
            .filter { viewEvent ->
                expectedViewMetasToKeep.none {
                    it.toBytes().contentEquals(viewEvent.metadata)
                }
            }
        val expectedResult = batch.filter { !expectedViewEventsToDrop.contains(it) }

        // When
        val result = testedFilter.filterOutRedundantViewEvents(batch)

        // Then
        assertThat(result).containsExactlyElementsOf(expectedResult)
    }

    // region Internal

    private fun Forge.aViewEventMetaList(viewIds: Set<String> = emptySet()): List<RumEventMeta.View> {
        return viewIds.ifEmpty { aList { getForgery<UUID>().toString() }.toSet() }
            .flatMap { aList { getForgery<RumEventMeta.View>().copy(viewId = it, hasAccessibility = false) } }
    }

    private fun RumEventMeta.View.toBytes() = toJson().toString().toByteArray()

    // endregion
}
