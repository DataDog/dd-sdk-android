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

    // region accessibility

    @Test
    fun `M keep only max doc version W filterOutRedundantViewEvents() { mixed batch, no accessibility }`(
        forge: Forge
    ) {
        // Given
        val viewEventMetas = forge.aViewEventMetaList(hasAccessibility = false)
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

    @Test
    fun `M all accessibility batches W filterOutRedundantViewEvents() { mixed batch, has accessibility }`(
        forge: Forge
    ) {
        // Given
        val viewEventMetas = forge.aViewEventMetaList(hasAccessibility = true)
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
    fun `M keep only accessibility and maxDocVersion W filterOutRedundantViewEvents()`(
        forge: Forge
    ) {
        // Given
        val viewId = forge.aString()
        val newMetaList = mutableListOf<RumEventMeta.View>()
        // max doc version
        val keptMaxDocEvent = RumEventMeta.View(
            viewId = viewId,
            documentVersion = Long.MAX_VALUE,
            hasAccessibility = false
        )
        newMetaList.add(keptMaxDocEvent)

        // accessibility true
        val keptAccessibilityEvent = RumEventMeta.View(
            viewId = viewId,
            documentVersion = Long.MIN_VALUE,
            hasAccessibility = true
        )
        newMetaList.add(keptAccessibilityEvent)

        // some other doc version
        val droppedEvent = RumEventMeta.View(
            viewId = viewId,
            documentVersion = forge.aLong(max = Long.MAX_VALUE - 1),
            hasAccessibility = false
        )
        newMetaList.add(droppedEvent)

        val viewEvents = newMetaList.map {
            RawBatchEvent(
                data = forge.aString().toByteArray(),
                metadata = it.toBytes()
            )
        }
        newMetaList.forEach {
            whenever(mockEventMetaDeserializer.deserialize(it.toBytes())) doReturn it
        }

        val keptMaxDocEventRaw = viewEvents.find { it.metadata.contentEquals(keptMaxDocEvent.toBytes()) }!!
        val keptAccessibilityEventRaw = viewEvents.find {
            it.metadata.contentEquals(
                keptAccessibilityEvent.toBytes()
            )
        }!!
        val droppedEventRaw = viewEvents.find { it.metadata.contentEquals(droppedEvent.toBytes()) }!!

        // When
        val result = testedFilter.filterOutRedundantViewEvents(viewEvents)

        // Then
        assertThat(result).containsExactlyInAnyOrder(keptMaxDocEventRaw, keptAccessibilityEventRaw)
        assertThat(result).doesNotContain(droppedEventRaw)
    }

    // endregion

    // region ViewUpdate filtering

    @Test
    fun `M keep ViewUpdate W filterOutRedundantViewEvents() { no View event for same viewId }`(
        forge: Forge
    ) {
        // Given
        val viewUpdateMeta = RumEventMeta.ViewUpdate(
            viewId = forge.aString(),
            documentVersion = forge.aLong(min = 1)
        )
        val viewUpdateEvent = RawBatchEvent(
            data = forge.aString().toByteArray(),
            metadata = viewUpdateMeta.toBytes()
        )
        whenever(mockEventMetaDeserializer.deserialize(viewUpdateMeta.toBytes())) doReturn viewUpdateMeta

        // When
        val result = testedFilter.filterOutRedundantViewEvents(listOf(viewUpdateEvent))

        // Then
        assertThat(result).containsOnly(viewUpdateEvent)
    }

    @Test
    fun `M drop ViewUpdate W filterOutRedundantViewEvents() { View event has bigger docVersion }`(
        forge: Forge
    ) {
        // Given
        val viewId = forge.aString()
        val viewUpdateMeta = RumEventMeta.ViewUpdate(viewId = viewId, documentVersion = 5L)
        val viewMeta = RumEventMeta.View(viewId = viewId, documentVersion = 10L)

        val viewUpdateEvent = RawBatchEvent(
            data = forge.aString().toByteArray(),
            metadata = viewUpdateMeta.toBytes()
        )
        val viewEvent = RawBatchEvent(
            data = forge.aString().toByteArray(),
            metadata = viewMeta.toBytes()
        )
        whenever(mockEventMetaDeserializer.deserialize(viewUpdateMeta.toBytes())) doReturn viewUpdateMeta
        whenever(mockEventMetaDeserializer.deserialize(viewMeta.toBytes())) doReturn viewMeta

        // When
        val result = testedFilter.filterOutRedundantViewEvents(listOf(viewUpdateEvent, viewEvent))

        // Then
        assertThat(result).containsOnly(viewEvent)
        assertThat(result).doesNotContain(viewUpdateEvent)
    }

    @Test
    fun `M keep ViewUpdate W filterOutRedundantViewEvents() { View event has equal docVersion }`(
        forge: Forge
    ) {
        // Given
        val viewId = forge.aString()
        val viewUpdateMeta = RumEventMeta.ViewUpdate(viewId = viewId, documentVersion = 10L)
        val viewMeta = RumEventMeta.View(viewId = viewId, documentVersion = 10L)

        val viewUpdateEvent = RawBatchEvent(
            data = forge.aString().toByteArray(),
            metadata = viewUpdateMeta.toBytes()
        )
        val viewEvent = RawBatchEvent(
            data = forge.aString().toByteArray(),
            metadata = viewMeta.toBytes()
        )
        whenever(mockEventMetaDeserializer.deserialize(viewUpdateMeta.toBytes())) doReturn viewUpdateMeta
        whenever(mockEventMetaDeserializer.deserialize(viewMeta.toBytes())) doReturn viewMeta

        // When
        val result = testedFilter.filterOutRedundantViewEvents(listOf(viewUpdateEvent, viewEvent))

        // Then
        assertThat(result).contains(viewUpdateEvent)
    }

    @Test
    fun `M keep ViewUpdate W filterOutRedundantViewEvents() { View event has smaller docVersion }`(
        forge: Forge
    ) {
        // Given
        val viewId = forge.aString()
        val viewUpdateMeta = RumEventMeta.ViewUpdate(viewId = viewId, documentVersion = 10L)
        val viewMeta = RumEventMeta.View(viewId = viewId, documentVersion = 5L)

        val viewUpdateEvent = RawBatchEvent(
            data = forge.aString().toByteArray(),
            metadata = viewUpdateMeta.toBytes()
        )
        val viewEvent = RawBatchEvent(
            data = forge.aString().toByteArray(),
            metadata = viewMeta.toBytes()
        )
        whenever(mockEventMetaDeserializer.deserialize(viewUpdateMeta.toBytes())) doReturn viewUpdateMeta
        whenever(mockEventMetaDeserializer.deserialize(viewMeta.toBytes())) doReturn viewMeta

        // When
        val result = testedFilter.filterOutRedundantViewEvents(listOf(viewUpdateEvent, viewEvent))

        // Then
        assertThat(result).contains(viewUpdateEvent)
    }

    @Test
    fun `M drop only stale ViewUpdates W filterOutRedundantViewEvents() { mixed ViewUpdate versions }`(
        forge: Forge
    ) {
        // Given
        val viewId = forge.aString()
        val staleViewUpdateMeta = RumEventMeta.ViewUpdate(viewId = viewId, documentVersion = 3L)
        val freshViewUpdateMeta = RumEventMeta.ViewUpdate(viewId = viewId, documentVersion = 15L)
        val viewMeta = RumEventMeta.View(viewId = viewId, documentVersion = 10L)

        val staleViewUpdateEvent = RawBatchEvent(
            data = forge.aString().toByteArray(),
            metadata = staleViewUpdateMeta.toBytes()
        )
        val freshViewUpdateEvent = RawBatchEvent(
            data = forge.aString().toByteArray(),
            metadata = freshViewUpdateMeta.toBytes()
        )
        val viewEvent = RawBatchEvent(
            data = forge.aString().toByteArray(),
            metadata = viewMeta.toBytes()
        )
        whenever(mockEventMetaDeserializer.deserialize(staleViewUpdateMeta.toBytes())) doReturn staleViewUpdateMeta
        whenever(mockEventMetaDeserializer.deserialize(freshViewUpdateMeta.toBytes())) doReturn freshViewUpdateMeta
        whenever(mockEventMetaDeserializer.deserialize(viewMeta.toBytes())) doReturn viewMeta

        // When
        val result = testedFilter.filterOutRedundantViewEvents(
            listOf(staleViewUpdateEvent, freshViewUpdateEvent, viewEvent)
        )

        // Then
        assertThat(result).contains(viewEvent, freshViewUpdateEvent)
        assertThat(result).doesNotContain(staleViewUpdateEvent)
    }

    @Test
    fun `M not affect ViewUpdates for other viewIds W filterOutRedundantViewEvents()`(
        forge: Forge
    ) {
        // Given
        val viewIdA = forge.aString()
        val viewIdB = forge.aString()
        val viewUpdateMetaA = RumEventMeta.ViewUpdate(viewId = viewIdA, documentVersion = 5L)
        val viewMetaA = RumEventMeta.View(viewId = viewIdA, documentVersion = 10L)
        val viewUpdateMetaB = RumEventMeta.ViewUpdate(viewId = viewIdB, documentVersion = 5L)

        val viewUpdateEventA = RawBatchEvent(
            data = forge.aString().toByteArray(),
            metadata = viewUpdateMetaA.toBytes()
        )
        val viewEventA = RawBatchEvent(
            data = forge.aString().toByteArray(),
            metadata = viewMetaA.toBytes()
        )
        val viewUpdateEventB = RawBatchEvent(
            data = forge.aString().toByteArray(),
            metadata = viewUpdateMetaB.toBytes()
        )
        whenever(mockEventMetaDeserializer.deserialize(viewUpdateMetaA.toBytes())) doReturn viewUpdateMetaA
        whenever(mockEventMetaDeserializer.deserialize(viewMetaA.toBytes())) doReturn viewMetaA
        whenever(mockEventMetaDeserializer.deserialize(viewUpdateMetaB.toBytes())) doReturn viewUpdateMetaB

        // When
        val result = testedFilter.filterOutRedundantViewEvents(
            listOf(viewUpdateEventA, viewEventA, viewUpdateEventB)
        )

        // Then
        assertThat(result).contains(viewEventA, viewUpdateEventB)
        assertThat(result).doesNotContain(viewUpdateEventA)
    }

    // endregion

    // region Internal

    private fun Forge.aViewEventMetaList(
        viewIds: Set<String> = emptySet(),
        hasAccessibility: Boolean = false,
        size: Int = -1
    ): List<RumEventMeta.View> {
        return viewIds.ifEmpty { aList { getForgery<UUID>().toString() }.toSet() }
            .flatMap {
                aList(size = size) {
                    getForgery<RumEventMeta.View>().copy(
                        viewId = it,
                        hasAccessibility = hasAccessibility
                    )
                }
            }
    }

    private fun RumEventMeta.View.toBytes() = toJson().toString().toByteArray()

    private fun RumEventMeta.ViewUpdate.toBytes() = toJson().toString().toByteArray()

    // endregion
}
