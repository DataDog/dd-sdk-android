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
import android.util.LruCache
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.tools.unit.extensions.ApiLevelExtension
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
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(ApiLevelExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class Base64LRUCacheTest {
    private val testedCache = Base64LRUCache

    @Mock
    lateinit var mockLruCache: LruCache<String, ByteArray>

    @Mock
    lateinit var mockDrawable: Drawable

    @StringForgery
    lateinit var fakeBase64: String

    val argumentCaptor = argumentCaptor<String>()

    @BeforeEach
    fun setup() {
        testedCache.setBackingCache(mockLruCache)
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
        val drawableID = System.identityHashCode(mockDrawable).toString()
        val fakeBase64String = forge.aString()
        val fakeValue = forge.anAsciiString().toByteArray()

        whenever(mockLruCache.get(drawableID)).thenReturn(fakeValue)
        testedCache.put(mockDrawable, fakeBase64String)

        // When
        val cacheItem = testedCache.get(mockDrawable)

        // Then
        verify(mockLruCache).get(drawableID)
        assertThat(cacheItem).isEqualTo(String(fakeValue))
    }

    @Test
    fun `M call LruCache put W put()`() {
        // Given
        val key = System.identityHashCode(mockDrawable).toString()

        // When
        testedCache.put(mockDrawable, fakeBase64)

        // Then
        verify(mockLruCache).put(key, fakeBase64.toByteArray())
    }

    @Test
    fun `M return LruCache size W size()`() {
        // Given
        whenever(mockLruCache.size()).thenReturn(3)

        // When
        val size = testedCache.size()

        // Then
        verify(mockLruCache).size()
        assertThat(size).isEqualTo(3)
    }

    @Test
    fun `M clear LRUCache W clear()`() {
        // When
        testedCache.clear()

        // Then
        verify(mockLruCache).evictAll()
    }

    @Test
    fun `M not generate prefix W put() { animationDrawable }`() {
        // Given
        val mockAnimationDrawable: AnimationDrawable = mock()

        // When
        testedCache.put(mockAnimationDrawable, fakeBase64)

        // Then
        verify(mockLruCache).put(argumentCaptor.capture(), any())
        assertThat(argumentCaptor.firstValue).doesNotContain("-")
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
        verify(mockLruCache).put(argumentCaptor.capture(), any())
        assertThat(argumentCaptor.firstValue).startsWith(expectedPrefix)
    }

    @Test
    fun `M generate key prefix with layer hash W put() { layerDrawable }`(forge: Forge) {
        // Given
        val mockRippleDrawable: RippleDrawable = mock()
        val mockBgLayer: Drawable = mock()
        val mockFgLayer: Drawable = mock()
        whenever(mockRippleDrawable.numberOfLayers).thenReturn(2)
        whenever(mockRippleDrawable.getDrawable(0)).thenReturn(mockBgLayer)
        whenever(mockRippleDrawable.getDrawable(1)).thenReturn(mockFgLayer)

        val fakeBase64 = forge.aString()
        val captor = argumentCaptor<String>()

        // When
        testedCache.put(mockRippleDrawable, fakeBase64)

        // Then
        verify(mockLruCache).put(captor.capture(), any())
        assertThat(captor.firstValue).contains(System.identityHashCode(mockBgLayer).toString())
    }

    @Test
    fun `M not generate key prefix W put() { layerDrawable with only one layer }`(forge: Forge) {
        // Given
        val mockRippleDrawable: RippleDrawable = mock()
        val mockBgLayer: Drawable = mock()
        whenever(mockRippleDrawable.numberOfLayers).thenReturn(1)
        whenever(mockRippleDrawable.getDrawable(0)).thenReturn(mockBgLayer)

        val fakeBase64 = forge.aString()
        val captor = argumentCaptor<String>()

        // When
        testedCache.put(mockRippleDrawable, fakeBase64)

        // Then
        verify(mockLruCache).put(captor.capture(), any())
        assertThat(captor.firstValue).isEqualTo(System.identityHashCode(mockRippleDrawable).toString())
    }
}
