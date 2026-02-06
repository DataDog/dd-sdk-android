/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.rum.domain

import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class WebViewNativeRumViewsCacheTest {

    private lateinit var fakeIdGenerator: FakeIdGenerator

    @LongForgery(min = 0L)
    private var fakeCurrentTimeMs: Long = 0L

    @Mock
    private lateinit var mockTimeProvider: TimeProvider

    private lateinit var testedCache: WebViewNativeRumViewsCache

    // region Unit Tests

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeIdGenerator = FakeIdGenerator(forge)
        whenever(mockTimeProvider.getDeviceTimestampMillis()).thenReturn(fakeCurrentTimeMs)
        testedCache = WebViewNativeRumViewsCache(mockTimeProvider)
    }

    private fun nextTimestamp(): Long {
        fakeCurrentTimeMs++
        whenever(mockTimeProvider.getDeviceTimestampMillis()).thenReturn(fakeCurrentTimeMs)
        return fakeCurrentTimeMs
    }

    @Test
    fun `M return the first entry from cache that matches the criteria W resolveLastParentIdForBrowserEvent()`(
        forge: Forge
    ) {
        // Given
        val fakeEntries = forge.aList(size = forge.anInt(min = 1, max = 10)) {
            mapOf(
                WebViewNativeRumViewsCache.VIEW_ID_KEY to fakeIdGenerator.generateId(),
                WebViewNativeRumViewsCache.VIEW_TIMESTAMP_KEY to nextTimestamp(),
                WebViewNativeRumViewsCache.VIEW_HAS_REPLAY_KEY to true
            )
        }
        val fakeNoReplayEntries = forge.aList(size = forge.anInt(min = 1, max = 10)) {
            mapOf(
                WebViewNativeRumViewsCache.VIEW_ID_KEY to fakeIdGenerator.generateId(),
                WebViewNativeRumViewsCache.VIEW_TIMESTAMP_KEY to nextTimestamp(),
                WebViewNativeRumViewsCache.VIEW_HAS_REPLAY_KEY to false
            )
        }
        val fakeBrowserEventTimestampInMs = nextTimestamp()
        fakeEntries.forEach {
            testedCache.addToCache(it)
        }
        fakeNoReplayEntries.forEach {
            testedCache.addToCache(it)
        }

        // When
        val resolvedParentId = testedCache.resolveLastParentIdForBrowserEvent(fakeBrowserEventTimestampInMs)

        // Then
        assertThat(resolvedParentId).isEqualTo(fakeEntries.last()[WebViewNativeRumViewsCache.VIEW_ID_KEY])
    }

    @Test
    fun `M return the first entry from cache W resolveLastParentIdForBrowserEvent() { hasReplay is false }`(
        forge: Forge
    ) {
        // Given
        val fakeEntries = forge.aList {
            mapOf(
                WebViewNativeRumViewsCache.VIEW_ID_KEY to fakeIdGenerator.generateId(),
                WebViewNativeRumViewsCache.VIEW_TIMESTAMP_KEY to nextTimestamp(),
                WebViewNativeRumViewsCache.VIEW_HAS_REPLAY_KEY to false
            )
        }
        fakeEntries.forEach {
            testedCache.addToCache(it)
        }
        val fakeBrowserEventTimestampInMs = nextTimestamp()

        // When
        val resolvedParentId = testedCache.resolveLastParentIdForBrowserEvent(fakeBrowserEventTimestampInMs)

        // Then
        assertThat(resolvedParentId).isEqualTo(fakeEntries.last()[WebViewNativeRumViewsCache.VIEW_ID_KEY])
    }

    @Test
    fun `M return null W resolveLastParentIdForBrowserEvent() { no matching candidate }`(
        forge: Forge
    ) {
        // Given
        val fakeBrowserEventTimestampInMs = nextTimestamp()
        val fakeEntries = forge.aList {
            mapOf(
                WebViewNativeRumViewsCache.VIEW_ID_KEY to fakeIdGenerator.generateId(),
                WebViewNativeRumViewsCache.VIEW_TIMESTAMP_KEY to nextTimestamp(),
                WebViewNativeRumViewsCache.VIEW_HAS_REPLAY_KEY to forge.aBool()
            )
        }
        fakeEntries.forEach {
            testedCache.addToCache(it)
        }

        // When
        val resolvedParentId = testedCache.resolveLastParentIdForBrowserEvent(fakeBrowserEventTimestampInMs)

        // Then
        assertThat(resolvedParentId).isNull()
    }

    @Test
    fun `M return null W resolveLastParentIdForBrowserEvent() { no data in cache }`() {
        // Given
        val fakeBrowserEventTimestampInMs = nextTimestamp()

        // When
        val resolvedParentId = testedCache.resolveLastParentIdForBrowserEvent(fakeBrowserEventTimestampInMs)

        // Then
        assertThat(resolvedParentId).isNull()
    }

    @Test
    fun `M not throw W resolveLastParentIdForBrowserEvent() { concurrent access }`(
        forge: Forge
    ) {
        // Given
        val fakeEntries = forge.aList {
            mapOf(
                WebViewNativeRumViewsCache.VIEW_ID_KEY to fakeIdGenerator.generateId(),
                WebViewNativeRumViewsCache.VIEW_TIMESTAMP_KEY to nextTimestamp(),
                WebViewNativeRumViewsCache.VIEW_HAS_REPLAY_KEY to forge.aBool()
            )
        }
        fakeEntries.forEach {
            Thread { testedCache.addToCache(it) }.apply { start() }.join(5000)
        }
        val fakeBrowserEventTimestampInMs = nextTimestamp()

        // Then
        assertDoesNotThrow { testedCache.resolveLastParentIdForBrowserEvent(fakeBrowserEventTimestampInMs) }
    }

    @Test
    fun `M purge the entries with expired TTL W addToCache()`(
        forge: Forge
    ) {
        // Given
        val entriesTtlLimitInMs = TimeUnit.SECONDS.toMillis(1)
        val oldEntriesTimestamp = fakeCurrentTimeMs
        testedCache = WebViewNativeRumViewsCache(mockTimeProvider, entriesTtlLimitInMs)
        var oldEntryTimestamp = oldEntriesTimestamp
        val fakeOldEntries = forge.aList(size = forge.anInt(min = 1, max = 10)) {
            mapOf<String, Any>(
                WebViewNativeRumViewsCache.VIEW_ID_KEY to fakeIdGenerator.generateId(),
                WebViewNativeRumViewsCache.VIEW_TIMESTAMP_KEY to ++oldEntryTimestamp,
                WebViewNativeRumViewsCache.VIEW_HAS_REPLAY_KEY to forge.aBool()
            )
        }
        whenever(mockTimeProvider.getDeviceTimestampMillis()).thenReturn(oldEntriesTimestamp)
        fakeOldEntries.forEach { testedCache.addToCache(it) }

        // When
        val newEntriesTimestamp = oldEntryTimestamp + entriesTtlLimitInMs + 1
        var newEntryTimestamp = newEntriesTimestamp
        val fakeNewEntries = forge.aList(
            size = forge.anInt(
                min = 1,
                max = WebViewNativeRumViewsCache.DATA_CACHE_ENTRIES_LIMIT
            )
        ) {
            mapOf<String, Any>(
                WebViewNativeRumViewsCache.VIEW_ID_KEY to fakeIdGenerator.generateId(),
                WebViewNativeRumViewsCache.VIEW_TIMESTAMP_KEY to ++newEntryTimestamp,
                WebViewNativeRumViewsCache.VIEW_HAS_REPLAY_KEY to forge.aBool()
            )
        }
        val expectedCachedEntries = fakeNewEntries.reversed().map {
            WebViewNativeRumViewsCache.ViewEntry(
                it[WebViewNativeRumViewsCache.VIEW_ID_KEY] as String,
                it[WebViewNativeRumViewsCache.VIEW_TIMESTAMP_KEY] as Long,
                it[WebViewNativeRumViewsCache.VIEW_HAS_REPLAY_KEY] as Boolean
            )
        }
        // Simulate time passing: device time is beyond TTL for old entries
        whenever(mockTimeProvider.getDeviceTimestampMillis()).thenReturn(newEntriesTimestamp)
        fakeNewEntries.forEach { testedCache.addToCache(it) }

        // Then
        assertThat(testedCache.parentViewsHistoryQueue).containsExactlyElementsOf(expectedCachedEntries)
    }

    @Test
    fun `M purge the old entries W addToCache(){ cache size limit reached }`(
        forge: Forge
    ) {
        // Given
        var index = 0
        val fakeEntries = forge.aList(
            size = forge.anInt(
                min = WebViewNativeRumViewsCache.DATA_CACHE_ENTRIES_LIMIT,
                max = WebViewNativeRumViewsCache.DATA_CACHE_ENTRIES_LIMIT * 2
            )
        ) {
            mapOf<String, Any>(
                WebViewNativeRumViewsCache.VIEW_ID_KEY to fakeIdGenerator.generateId() + index++,
                WebViewNativeRumViewsCache.VIEW_TIMESTAMP_KEY to nextTimestamp(),
                WebViewNativeRumViewsCache.VIEW_HAS_REPLAY_KEY to forge.aBool()
            )
        }

        // When
        val expectedCachedEntries = fakeEntries
            .takeLast(WebViewNativeRumViewsCache.DATA_CACHE_ENTRIES_LIMIT)
            .reversed()
            .map {
                WebViewNativeRumViewsCache.ViewEntry(
                    it[WebViewNativeRumViewsCache.VIEW_ID_KEY] as String,
                    it[WebViewNativeRumViewsCache.VIEW_TIMESTAMP_KEY] as Long,
                    it[WebViewNativeRumViewsCache.VIEW_HAS_REPLAY_KEY] as Boolean
                )
            }
        fakeEntries.forEach { testedCache.addToCache(it) }

        // Then
        assertThat(testedCache.parentViewsHistoryQueue).containsExactlyElementsOf(expectedCachedEntries)
    }

    @Test
    fun `M keep only one entry W addToCache() { same view id }`(
        forge: Forge
    ) {
        // Given
        val fakeViewId = fakeIdGenerator.generateId()
        val fakeEntries = forge.aList(size = forge.anInt(min = 1, max = 10)) {
            mapOf<String, Any>(
                WebViewNativeRumViewsCache.VIEW_ID_KEY to fakeViewId,
                WebViewNativeRumViewsCache.VIEW_TIMESTAMP_KEY to nextTimestamp(),
                WebViewNativeRumViewsCache.VIEW_HAS_REPLAY_KEY to forge.aBool()
            )
        }
        fakeEntries.forEach { testedCache.addToCache(it) }

        // When
        // Then
        assertThat(testedCache.parentViewsHistoryQueue).containsOnly(
            WebViewNativeRumViewsCache.ViewEntry(
                fakeViewId,
                fakeEntries.last()[WebViewNativeRumViewsCache.VIEW_TIMESTAMP_KEY] as Long,
                fakeEntries.last()[WebViewNativeRumViewsCache.VIEW_HAS_REPLAY_KEY] as Boolean
            )
        )
    }

    @Test
    fun `M drop the entry W addToCache { timestamp is older }`(forge: Forge) {
        // Given
        val fakeOldEntries = forge.aList(
            size = forge.anInt(
                min = 1,
                max = 10
            )
        ) {
            mapOf<String, Any>(
                WebViewNativeRumViewsCache.VIEW_ID_KEY to fakeIdGenerator.generateId(),
                WebViewNativeRumViewsCache.VIEW_TIMESTAMP_KEY to nextTimestamp(),
                WebViewNativeRumViewsCache.VIEW_HAS_REPLAY_KEY to forge.aBool()
            )
        }
        val fakeEntries = forge.aList(
            size = forge.anInt(
                min = 1,
                max = 10
            )
        ) {
            mapOf<String, Any>(
                WebViewNativeRumViewsCache.VIEW_ID_KEY to fakeIdGenerator.generateId(),
                WebViewNativeRumViewsCache.VIEW_TIMESTAMP_KEY to nextTimestamp(),
                WebViewNativeRumViewsCache.VIEW_HAS_REPLAY_KEY to forge.aBool()
            )
        }

        // When
        fakeEntries.forEach { testedCache.addToCache(it) }
        fakeOldEntries.forEach { testedCache.addToCache(it) }

        // Then
        assertThat(testedCache.parentViewsHistoryQueue).containsExactlyElementsOf(
            fakeEntries
                .reversed()
                .map {
                    WebViewNativeRumViewsCache.ViewEntry(
                        it[WebViewNativeRumViewsCache.VIEW_ID_KEY] as String,
                        it[WebViewNativeRumViewsCache.VIEW_TIMESTAMP_KEY] as Long,
                        it[WebViewNativeRumViewsCache.VIEW_HAS_REPLAY_KEY] as Boolean
                    )
                }
        )
    }

    // endregion

    // region Utils

    private class FakeIdGenerator(private val forge: Forge) {
        var index = 0
        fun generateId(): String {
            val newId = forge.anAlphabeticalString() + index
            index++
            return newId
        }
    }

    // endregion
}
