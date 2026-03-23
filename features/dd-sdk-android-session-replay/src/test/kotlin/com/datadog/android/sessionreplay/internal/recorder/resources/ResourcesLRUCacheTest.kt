/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import androidx.collection.LruCache
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.resources.ResourcesLRUCache.Companion.MAX_CACHE_MEMORY_SIZE_BYTES
import com.datadog.android.sessionreplay.internal.utils.InvocationUtils
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
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class ResourcesLRUCacheTest {
    private lateinit var testedCache: ResourcesLRUCache

    @Mock
    lateinit var mockInvocationUtils: InvocationUtils

    @StringForgery
    lateinit var fakeResourceKey: String

    @BeforeEach
    fun setup() {
        val internalCache = LruCache<String, ByteArray>(MAX_CACHE_MEMORY_SIZE_BYTES)
        testedCache = ResourcesLRUCache(
            invocationUtils = mockInvocationUtils,
            cache = internalCache
        )
    }

    @Test
    fun `M return null W get() { item not in cache }`() {
        // When
        val cacheItem = testedCache.get(fakeResourceKey)

        // Then
        assertThat(cacheItem).isNull()
    }

    @Test
    fun `M return item W get() { item in cache }`(
        @StringForgery fakeResourceId: String
    ) {
        // Given
        val fakeResourceIdByteArray = fakeResourceId.toByteArray(Charsets.UTF_8)
        testedCache.put(fakeResourceKey, fakeResourceIdByteArray)

        // When
        val cacheItem = testedCache.get(fakeResourceKey)

        // Then
        assertThat(cacheItem).isEqualTo(fakeResourceIdByteArray)
    }
}
