/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import androidx.collection.LruCache
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.resources.ResourcesLRUCache.Companion.MAX_CACHE_MEMORY_SIZE_BYTES
import com.datadog.android.sessionreplay.internal.recorder.safeGetDrawable
import com.datadog.android.sessionreplay.internal.utils.InvocationUtils
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class ResourcesLRUCacheTest {
    private lateinit var testedCache: ResourcesLRUCache

    private lateinit var internalCache: LruCache<String, ByteArray>

    @Mock
    lateinit var mockDrawable: Drawable

    @Mock
    lateinit var mockInvocationUtils: InvocationUtils

    val argumentCaptor = argumentCaptor<String>()

    @BeforeEach
    fun setup() {
        internalCache = LruCache<String, ByteArray>(MAX_CACHE_MEMORY_SIZE_BYTES)
        testedCache = ResourcesLRUCache(
            invocationUtils = mockInvocationUtils,
            cache = internalCache
        )
    }

    @Test
    fun `M return null W get() { item not in cache }`() {
        // When
        val cacheItem = testedCache.get(mockDrawable)

        // Then
        assertThat(cacheItem).isNull()
    }

    @Test
    fun `M return item W get() { item in cache }`(
        @StringForgery fakeResourceId: String
    ) {
        // Given
        val fakeResourceIdByteArray = fakeResourceId.toByteArray(Charsets.UTF_8)
        testedCache.put(mockDrawable, fakeResourceIdByteArray)

        // When
        val cacheItem = testedCache.get(mockDrawable)

        // Then
        assertThat(cacheItem).isEqualTo(fakeResourceIdByteArray)
    }

    @Test
    fun `M not generate prefix W put() { animationDrawable }`(
        @StringForgery fakeResourceId: String
    ) {
        // Given
        val fakeResourceIdByteArray = fakeResourceId.toByteArray(Charsets.UTF_8)
        val mockAnimationDrawable: AnimationDrawable = mock()

        // When
        testedCache.put(mockAnimationDrawable, fakeResourceIdByteArray)

        // Then
        val key = testedCache.generateKey(mockAnimationDrawable)
        assertThat(key).doesNotContain("-")
    }

    @Test
    fun `M generate key prefix with state W put() { drawableContainer }`(
        @StringForgery fakeResourceId: String,
        forge: Forge
    ) {
        // Given
        val fakeResourceIdByteArray = fakeResourceId.toByteArray(Charsets.UTF_8)
        val mockStatelistDrawable: StateListDrawable = mock()
        val fakeStateArray = intArrayOf(forge.aPositiveInt())
        val expectedPrefix = fakeStateArray[0].toString() + "-"
        whenever(mockStatelistDrawable.state).thenReturn(fakeStateArray)

        // When
        testedCache.put(mockStatelistDrawable, fakeResourceIdByteArray)

        // Then
        val key = testedCache.generateKey(mockStatelistDrawable)
        assertThat(key).startsWith(expectedPrefix)
    }

    @Test
    fun `M generate key prefix with layer hash W put() { layerDrawable }`(
        @StringForgery fakeResourceId: String
    ) {
        // Given
        val fakeResourceIdByteArray = fakeResourceId.toByteArray(Charsets.UTF_8)
        val mockRippleDrawable: RippleDrawable = mock()
        val mockBackgroundLayer: Drawable = mock()
        val mockForegroundLayer: Drawable = mock()
        whenever(mockRippleDrawable.numberOfLayers).thenReturn(2)
        whenever(mockRippleDrawable.safeGetDrawable(0))
            .thenReturn(mockBackgroundLayer)
        whenever(mockRippleDrawable.safeGetDrawable(1))
            .thenReturn(mockForegroundLayer)
        whenever(mockRippleDrawable.numberOfLayers).thenReturn(2)

        testedCache.put(mockRippleDrawable, fakeResourceIdByteArray)

        val expectedPrefix = System.identityHashCode(mockBackgroundLayer).toString() + "-" +
            System.identityHashCode(mockForegroundLayer).toString() + "-"
        val expectedHash = System.identityHashCode(mockRippleDrawable).toString()

        // When
        val key = testedCache.generateKey(mockRippleDrawable)

        // Then
        assertThat(key).isEqualTo(expectedPrefix + expectedHash)
    }

    @Test
    fun `M not generate key prefix W put() { layerDrawable with only one layer }`(
        @StringForgery fakeResourceId: String,
        @Mock mockRippleDrawable: RippleDrawable,
        @Mock mockBackgroundLayer: Drawable
    ) {
        // Given
        val fakeResourceIdByteArray = fakeResourceId.toByteArray(Charsets.UTF_8)
        whenever(mockRippleDrawable.numberOfLayers).thenReturn(1)
        whenever(mockRippleDrawable.safeGetDrawable(0)).thenReturn(mockBackgroundLayer)
        testedCache.put(mockRippleDrawable, fakeResourceIdByteArray)

        val expectedPrefix = System.identityHashCode(mockBackgroundLayer).toString() + "-"
        val drawableHash = System.identityHashCode(mockRippleDrawable).toString()

        // When
        val key = testedCache.generateKey(mockRippleDrawable)

        // Then
        assertThat(key).isEqualTo(expectedPrefix + drawableHash)
    }
}
