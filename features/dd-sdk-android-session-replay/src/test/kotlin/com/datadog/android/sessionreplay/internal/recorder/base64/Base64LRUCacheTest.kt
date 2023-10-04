/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.base64

import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import androidx.collection.LruCache
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.base64.Base64LRUCache.Companion.MAX_CACHE_MEMORY_SIZE_BYTES
import com.datadog.android.sessionreplay.internal.recorder.safeGetDrawable
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
internal class Base64LRUCacheTest {
    private lateinit var testedCache: Base64LRUCache

    private lateinit var internalCache: LruCache<String, ByteArray>

    @Mock
    lateinit var mockDrawable: Drawable

    @StringForgery
    lateinit var fakeBase64: String

    val argumentCaptor = argumentCaptor<String>()

    @BeforeEach
    fun setup() {
        internalCache = LruCache<String, ByteArray>(MAX_CACHE_MEMORY_SIZE_BYTES)
        testedCache = Base64LRUCache(
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
    fun `M return item W get() { item in cache }`(forge: Forge) {
        // Given
        val fakeBase64String = forge.aString()

        testedCache.put(mockDrawable, fakeBase64String)

        // When
        val cacheItem = testedCache.get(mockDrawable)

        // Then
        assertThat(cacheItem).isEqualTo(fakeBase64String)
    }

    @Test
    fun `M not generate prefix W put() { animationDrawable }`() {
        // Given
        val mockAnimationDrawable: AnimationDrawable = mock()

        // When
        testedCache.put(mockAnimationDrawable, fakeBase64)

        // Then
        val key = testedCache.generateKey(mockAnimationDrawable)
        assertThat(key).doesNotContain("-")
    }

    @Test
    fun `M generate key prefix with state W put() { drawableContainer }`(forge: Forge) {
        // Given
        val mockStatelistDrawable: StateListDrawable = mock()
        val fakeStateArray = intArrayOf(forge.aPositiveInt())
        val expectedPrefix = fakeStateArray[0].toString() + "-"
        whenever(mockStatelistDrawable.state).thenReturn(fakeStateArray)

        // When
        testedCache.put(mockStatelistDrawable, fakeBase64)

        // Then
        val key = testedCache.generateKey(mockStatelistDrawable)
        assertThat(key).startsWith(expectedPrefix)
    }

    @Test
    fun `M generate key prefix with layer hash W put() { layerDrawable }`(forge: Forge) {
        // Given
        val mockRippleDrawable: RippleDrawable = mock()
        val mockBgLayer: Drawable = mock()
        val mockFgLayer: Drawable = mock()
        whenever(mockRippleDrawable.numberOfLayers).thenReturn(2)
        whenever(mockRippleDrawable.safeGetDrawable(0))
            .thenReturn(mockBgLayer)
        whenever(mockRippleDrawable.safeGetDrawable(1))
            .thenReturn(mockFgLayer)
        whenever(mockRippleDrawable.numberOfLayers).thenReturn(2)

        val fakeBase64 = forge.aString()
        testedCache.put(mockRippleDrawable, fakeBase64)

        val expectedPrefix = System.identityHashCode(mockBgLayer).toString() + "-" +
            System.identityHashCode(mockFgLayer).toString() + "-"
        val expectedHash = System.identityHashCode(mockRippleDrawable).toString()

        // When
        val key = testedCache.generateKey(mockRippleDrawable)

        // Then
        assertThat(key).isEqualTo(expectedPrefix + expectedHash)
    }

    @Test
    fun `M not generate key prefix W put() { layerDrawable with only one layer }`(
        @Mock mockRippleDrawable: RippleDrawable,
        @Mock mockBgLayer: Drawable,
        @StringForgery fakeBase64: String
    ) {
        // Given
        whenever(mockRippleDrawable.numberOfLayers).thenReturn(1)
        whenever(mockRippleDrawable.safeGetDrawable(0)).thenReturn(mockBgLayer)
        testedCache.put(mockRippleDrawable, fakeBase64)

        val expectedPrefix = System.identityHashCode(mockBgLayer).toString() + "-"
        val drawableHash = System.identityHashCode(mockRippleDrawable).toString()

        // When
        val key = testedCache.generateKey(mockRippleDrawable)

        // Then
        assertThat(key).isEqualTo(expectedPrefix + drawableHash)
    }
}
