/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.graphics.Bitmap
import android.graphics.ColorSpace
import androidx.collection.LruCache
import com.datadog.android.internal.system.BuildSdkVersionProvider
import com.datadog.android.sessionreplay.recorder.composition.CompositionTextResourceKey
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal class DefaultCompositionTextResourcesTest {
    private val rawCache = LruCache<Long, String>(200)
    private val stubSdk: BuildSdkVersionProvider = mock {
        on { version }.thenReturn(36)
        on { isAtLeastO }.thenReturn(true)
    }
    private val testedResources = DefaultCompositionTextResources(rawCache, buildSdkVersionProvider = stubSdk)

    private fun bitmap() = mock<Bitmap> {
        on { width }.thenReturn(120)
        on { height }.thenReturn(80)
        on { generationId }.thenReturn(1)
        on { config }.thenReturn(Bitmap.Config.ARGB_8888)
        on { hasAlpha() }.thenReturn(true)
        on { isPremultiplied }.thenReturn(true)
        on { density }.thenReturn(320)
        on { isMutable }.thenReturn(true)
    }

    private fun resolve(key: CompositionTextResourceKey, pixels: Bitmap, signature: Long = 1L) {
        testedResources.registerCapture(key, pixels)
        assertThat(testedResources.findResourceId(key)).isNull()
        rawCache.put(signature, "resource-$signature")
        testedResources.onTextResolved(pixels, signature)
    }

    @Test
    fun `M reuse only a successfully resolved glyph resource W findResourceId`() {
        // Given
        val fakeKey = CompositionTextResourceKey()
        val stubPixels = bitmap()
        resolve(fakeKey, stubPixels)

        // When + Then
        assertThat(testedResources.findResourceId(fakeKey)).isEqualTo("resource-1")
        assertThat(testedResources.findResourceId(CompositionTextResourceKey())).isNull()
        assertThat(weakMap(testedResources, "captures").containsKey(stubPixels)).isTrue()
    }

    @Test
    fun `M require authoritative resource presence W findResourceId`() {
        // Given
        val fakeKey = CompositionTextResourceKey()
        val stubPixels = bitmap()
        resolve(fakeKey, stubPixels)
        rawCache.evictAll()

        // When + Then
        assertThat(testedResources.findResourceId(fakeKey)).isNull()
        testedResources.onTextResolved(stubPixels, 1L)
        assertThat(testedResources.findResourceId(fakeKey)).isNull()
        assertThat(DefaultCompositionTextResources(rawCache).findResourceId(fakeKey)).isNull()
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "generation", "width", "height", "config", "alpha", "premultiplied",
            "density", "colorSpace", "recycled", "gainmap"
        ]
    )
    fun `M reject changed output before publication W onTextResolved`(changed: String) {
        // Given
        val fakeKey = CompositionTextResourceKey()
        val stubPixels = bitmap()
        testedResources.registerCapture(fakeKey, stubPixels)
        when (changed) {
            "generation" -> whenever(stubPixels.generationId).thenReturn(2)
            "width" -> whenever(stubPixels.width).thenReturn(121)
            "height" -> whenever(stubPixels.height).thenReturn(81)
            "config" -> whenever(stubPixels.config).thenReturn(Bitmap.Config.RGB_565)
            "alpha" -> whenever(stubPixels.hasAlpha()).thenReturn(false)
            "premultiplied" -> whenever(stubPixels.isPremultiplied).thenReturn(false)
            "density" -> whenever(stubPixels.density).thenReturn(480)
            "colorSpace" -> whenever(stubPixels.colorSpace).thenReturn(mock<ColorSpace>())
            "recycled" -> whenever(stubPixels.isRecycled).thenReturn(true)
            "gainmap" -> whenever(stubPixels.hasGainmap()).thenReturn(true)
        }

        // When
        rawCache.put(1L, "resource-1")
        testedResources.onTextResolved(stubPixels, 1L)

        // Then
        assertThat(testedResources.findResourceId(fakeKey)).isNull()
    }

    @ParameterizedTest
    @ValueSource(strings = ["recycled", "emptyWidth", "emptyHeight", "alpha8", "missingConfig", "gainmap"])
    fun `M reject ineligible pixels W registerCapture`(kind: String) {
        // Given
        val fakeKey = CompositionTextResourceKey()
        val stubPixels = bitmap()
        when (kind) {
            "recycled" -> whenever(stubPixels.isRecycled).thenReturn(true)
            "emptyWidth" -> whenever(stubPixels.width).thenReturn(0)
            "emptyHeight" -> whenever(stubPixels.height).thenReturn(0)
            "alpha8" -> whenever(stubPixels.config).thenReturn(Bitmap.Config.ALPHA_8)
            "missingConfig" -> whenever(stubPixels.config).thenReturn(null)
            "gainmap" -> whenever(stubPixels.hasGainmap()).thenReturn(true)
        }

        // When
        testedResources.registerCapture(fakeKey, stubPixels)
        rawCache.put(1L, "resource-1")
        testedResources.onTextResolved(stubPixels, 1L)

        // Then
        assertThat(testedResources.findResourceId(fakeKey)).isNull()
        assertThat(weakMap(testedResources, "captures")).isEmpty()
    }

    @Test
    fun `M invalidate ambiguous bitmap bindings W registerCapture`() {
        // Given
        val fakeFirst = CompositionTextResourceKey()
        val fakeSecond = CompositionTextResourceKey()
        val stubPixels = bitmap()
        resolve(fakeFirst, stubPixels)

        // When
        testedResources.registerCapture(fakeSecond, stubPixels)
        testedResources.registerCapture(fakeFirst, stubPixels)
        testedResources.onTextResolved(stubPixels, 1L)

        // Then
        assertThat(testedResources.findResourceId(fakeFirst)).isNull()
        assertThat(testedResources.findResourceId(fakeSecond)).isNull()
    }

    @Test
    fun `M ignore a replaced entry callback W onTextResolved`() {
        // Given
        val fakeKey = CompositionTextResourceKey()
        val stubOld = bitmap()
        val stubNew = bitmap()
        testedResources.registerCapture(fakeKey, stubOld)
        testedResources.registerCapture(fakeKey, stubNew)
        rawCache.put(1L, "old")
        rawCache.put(2L, "new")

        // When + Then
        testedResources.onTextResolved(stubOld, 1L)
        assertThat(testedResources.findResourceId(fakeKey)).isNull()
        testedResources.onTextResolved(stubNew, 2L)
        assertThat(testedResources.findResourceId(fakeKey)).isEqualTo("new")
        testedResources.onTextResolved(stubOld, 1L)
        assertThat(testedResources.findResourceId(fakeKey)).isEqualTo("new")
    }

    @Test
    fun `M preserve idempotent registration W registerCapture`() {
        // Given
        val fakeKey = CompositionTextResourceKey()
        val stubPixels = bitmap()
        resolve(fakeKey, stubPixels)

        // When
        testedResources.registerCapture(fakeKey, stubPixels)

        // Then
        assertThat(testedResources.findResourceId(fakeKey)).isEqualTo("resource-1")
    }

    @Test
    fun `M bound metadata and capture bookkeeping W registerCapture`() {
        // Given
        val testedBounded = DefaultCompositionTextResources(rawCache, maxEntries = 2)
        val fakeKeys = List(3) { CompositionTextResourceKey() }
        val stubPixels = List(3) { bitmap() }

        // When
        fakeKeys.forEachIndexed { index, key ->
            testedBounded.registerCapture(key, stubPixels[index])
            rawCache.put(index.toLong(), "resource-$index")
            testedBounded.onTextResolved(stubPixels[index], index.toLong())
        }

        // Then
        assertThat(testedBounded.findResourceId(fakeKeys[0])).isEqualTo("resource-0")
        assertThat(testedBounded.findResourceId(fakeKeys[2])).isNull()
        assertThat(weakMap(testedBounded, "entries")).hasSize(2)
        assertThat(weakMap(testedBounded, "captures")).hasSize(2)
    }

    @Test
    fun `M hold pending keys weakly W onTextResolved`() {
        // Given
        val fakeKey = CompositionTextResourceKey()
        val stubPixels = bitmap()
        testedResources.registerCapture(fakeKey, stubPixels)
        val binding = weakMap(testedResources, "captures")[stubPixels]!!
        val field = binding.javaClass.getDeclaredField("key").apply { isAccessible = true }
        val weakKey = field.get(binding) as WeakReference<*>
        assertThat(weakKey.get()).isSameAs(fakeKey)
        weakKey.clear()

        // When
        rawCache.put(1L, "resource-1")
        testedResources.onTextResolved(stubPixels, 1L)

        // Then
        assertThat(testedResources.findResourceId(fakeKey)).isNull()
    }

    @Test
    fun `M clear knowledge and ignore late callbacks W stop`() {
        // Given
        val fakeKey = CompositionTextResourceKey()
        val stubPixels = bitmap()
        resolve(fakeKey, stubPixels)

        // When
        testedResources.stop()
        testedResources.registerCapture(fakeKey, stubPixels)
        testedResources.onTextResolved(stubPixels, 1L)

        // Then
        assertThat(testedResources.findResourceId(fakeKey)).isNull()
        assertThat(weakMap(testedResources, "entries")).isEmpty()
        assertThat(weakMap(testedResources, "captures")).isEmpty()
    }

    @Test
    @Timeout(5)
    fun `M stop without waiting on authoritative cache lookup W stop`() {
        // Given
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val stubAuthoritative: LruCache<Long, String> = mock()
        whenever(stubAuthoritative.get(1L)) doAnswer {
            entered.countDown()
            release.await(3, TimeUnit.SECONDS)
            "resource-1"
        }
        val testedConcurrent = DefaultCompositionTextResources(stubAuthoritative)
        val fakeKey = CompositionTextResourceKey()
        val stubPixels = bitmap()
        testedConcurrent.registerCapture(fakeKey, stubPixels)
        testedConcurrent.onTextResolved(stubPixels, 1L)
        val executor = Executors.newSingleThreadExecutor()
        try {
            // When
            val lookup = executor.submit<String?> { testedConcurrent.findResourceId(fakeKey) }
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue()
            testedConcurrent.stop()
            release.countDown()

            // Then
            assertThat(lookup.get(1, TimeUnit.SECONDS)).isNull()
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    private fun weakMap(cache: DefaultCompositionTextResources, name: String): Map<*, *> =
        cache.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(cache) as Map<*, *>
}
