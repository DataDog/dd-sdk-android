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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal class DefaultCompositionSourceImageResourcesTest {
    private val rawCache = LruCache<Long, String>(200)
    private val sdk: BuildSdkVersionProvider = mock {
        on { version }.thenReturn(36)
        on { isAtLeastO }.thenReturn(true)
    }
    private val testedResources = DefaultCompositionSourceImageResources(rawCache, buildSdkVersionProvider = sdk)

    private fun bitmap() = mock<Bitmap> {
        on { width }.thenReturn(120)
        on { height }.thenReturn(80)
        on { generationId }.thenReturn(1)
        on { config }.thenReturn(Bitmap.Config.ARGB_8888)
        on { hasAlpha() }.thenReturn(true)
        on { isPremultiplied }.thenReturn(true)
        on { density }.thenReturn(320)
    }

    private fun resolve(source: Bitmap, signature: Long = 1L): Bitmap {
        val pixels = bitmap()
        assertThat(testedResources.findResourceId(source)).isNull()
        testedResources.registerSourceCopy(source, pixels)
        rawCache.put(signature, "resource-$signature")
        testedResources.onSourceResolved(pixels, signature)
        return pixels
    }

    @Test
    fun `M reuse a resolved resource without retaining pixels W findResourceId`() {
        // Given
        val source = bitmap()
        val pixels = resolve(source)

        // When + Then
        assertThat(testedResources.findResourceId(source)).isEqualTo("resource-1")
        verify(source, never()).copy(any(), any())
        verify(source, never()).recycle()
        verify(pixels, never()).recycle()
    }

    @ParameterizedTest
    @ValueSource(
        strings = ["generation", "width", "height", "config", "alpha", "premultiplied", "density", "colorSpace"]
    )
    fun `M invalidate changed source metadata W findResourceId`(changed: String) {
        // Given
        val source = bitmap()
        resolve(source)
        when (changed) {
            "generation" -> whenever(source.generationId).thenReturn(2)
            "width" -> whenever(source.width).thenReturn(121)
            "height" -> whenever(source.height).thenReturn(81)
            "config" -> whenever(source.config).thenReturn(Bitmap.Config.RGB_565)
            "alpha" -> whenever(source.hasAlpha()).thenReturn(false)
            "premultiplied" -> whenever(source.isPremultiplied).thenReturn(false)
            "density" -> whenever(source.density).thenReturn(480)
            "colorSpace" -> whenever(source.colorSpace).thenReturn(mock<ColorSpace>())
        }

        // When + Then
        assertThat(testedResources.findResourceId(source)).isNull()
    }

    @Test
    fun `M distinguish identical metadata on different sources W findResourceId`() {
        // Given
        resolve(bitmap())

        // When + Then
        assertThat(testedResources.findResourceId(bitmap())).isNull()
    }

    @ParameterizedTest
    @ValueSource(strings = ["mutable", "recycled", "emptyWidth", "emptyHeight", "alpha8", "missingConfig", "gainmap"])
    fun `M reject ineligible source W findResourceId`(kind: String) {
        // Given
        val source = bitmap()
        when (kind) {
            "mutable" -> whenever(source.isMutable).thenReturn(true)
            "recycled" -> whenever(source.isRecycled).thenReturn(true)
            "emptyWidth" -> whenever(source.width).thenReturn(0)
            "emptyHeight" -> whenever(source.height).thenReturn(0)
            "alpha8" -> whenever(source.config).thenReturn(Bitmap.Config.ALPHA_8)
            "missingConfig" -> whenever(source.config).thenReturn(null)
            "gainmap" -> whenever(source.hasGainmap()).thenReturn(true)
        }

        // When
        val pixels = bitmap()
        testedResources.findResourceId(source)
        testedResources.registerSourceCopy(source, pixels)
        rawCache.put(1L, "resource-1")
        testedResources.onSourceResolved(pixels, 1L)

        // Then
        assertThat(testedResources.findResourceId(source)).isNull()
    }

    @Test
    fun `M require authoritative resource presence W findResourceId`() {
        // Given
        val source = bitmap()
        val pixels = resolve(source)
        rawCache.evictAll()

        // When + Then
        assertThat(testedResources.findResourceId(source)).isNull()
        testedResources.onSourceResolved(pixels, 1L)
        assertThat(testedResources.findResourceId(source)).isNull()
        assertThat(DefaultCompositionSourceImageResources(rawCache).findResourceId(source)).isNull()
    }

    @Test
    fun `M reject source changes during copy W registerSourceCopy`() {
        // Given
        val source = bitmap()
        val pixels = bitmap()
        testedResources.findResourceId(source)
        whenever(source.generationId).thenReturn(2)

        // When
        testedResources.registerSourceCopy(source, pixels)
        rawCache.put(1L, "resource-1")
        testedResources.onSourceResolved(pixels, 1L)

        // Then
        assertThat(testedResources.findResourceId(source)).isNull()
    }

    @Test
    fun `M ignore late publication for a replaced source state W onSourceResolved`() {
        // Given
        val source = bitmap()
        val pixels = bitmap()
        testedResources.findResourceId(source)
        testedResources.registerSourceCopy(source, pixels)
        whenever(source.generationId).thenReturn(2)
        testedResources.findResourceId(source)

        // When
        rawCache.put(1L, "resource-1")
        testedResources.onSourceResolved(pixels, 1L)

        // Then
        assertThat(testedResources.findResourceId(source)).isNull()
    }

    @Test
    fun `M reject unregistered or resized copies W onSourceResolved`() {
        // Given
        val source = bitmap()
        val resized = bitmap()
        whenever(resized.width).thenReturn(60)
        testedResources.findResourceId(source)

        // When
        testedResources.registerSourceCopy(source, resized)
        rawCache.put(1L, "resource-1")
        testedResources.onSourceResolved(resized, 1L)
        testedResources.onSourceResolved(bitmap(), 1L)

        // Then
        assertThat(testedResources.findResourceId(source)).isNull()
    }

    @Test
    fun `M reject ambiguous bindings W registerSourceCopy`() {
        // Given
        val first = bitmap()
        val second = bitmap()
        val pixels = bitmap()
        testedResources.findResourceId(first)
        testedResources.findResourceId(second)
        testedResources.registerSourceCopy(first, pixels)

        // When
        testedResources.registerSourceCopy(second, pixels)
        testedResources.registerSourceCopy(first, pixels)
        rawCache.put(1L, "resource-1")
        testedResources.onSourceResolved(pixels, 1L)

        // Then
        assertThat(testedResources.findResourceId(first)).isNull()
        assertThat(testedResources.findResourceId(second)).isNull()
    }

    @Test
    fun `M bound source aliases and copy bindings W registerSourceCopy`() {
        // Given
        val cache = DefaultCompositionSourceImageResources(rawCache, maxEntries = 2)
        val sources = List(3) { bitmap() }
        val pixels = List(3) { bitmap() }
        sources.forEachIndexed { index, source ->
            cache.findResourceId(source)
            cache.registerSourceCopy(source, pixels[index])
            rawCache.put(index.toLong(), "resource-$index")
            cache.onSourceResolved(pixels[index], index.toLong())
        }

        // When + Then
        assertThat(cache.findResourceId(sources[1])).isEqualTo("resource-1")
        assertThat(cache.findResourceId(sources[2])).isNull() // Copy bookkeeping was already full.
        assertThat(cache.findResourceId(sources[0])).isNull() // Source metadata was evicted.
        assertThat(weakMap(cache, "sources")).hasSize(2)
        assertThat(weakMap(cache, "copies")).hasSize(2)
    }

    @Test
    fun `M release source knowledge and ignore late callbacks W stop`() {
        // Given
        val source = bitmap()
        val pixels = resolve(source)

        // When
        testedResources.stop()
        testedResources.registerSourceCopy(source, pixels)
        testedResources.onSourceResolved(pixels, 1L)

        // Then
        assertThat(testedResources.findResourceId(source)).isNull()
        assertThat(weakMap(testedResources, "sources")).isEmpty()
        assertThat(weakMap(testedResources, "copies")).isEmpty()
    }

    @Test
    fun `M hold pending source identity weakly W onSourceResolved`() {
        // Given
        val source = bitmap()
        val pixels = bitmap()
        testedResources.findResourceId(source)
        testedResources.registerSourceCopy(source, pixels)
        val binding = weakMap(testedResources, "copies")[pixels]!!
        val sourceField = binding.javaClass.getDeclaredField("source").apply { isAccessible = true }
        val weakSource = sourceField.get(binding) as WeakReference<*>
        assertThat(weakSource.get()).isSameAs(source)
        weakSource.clear()

        // When
        rawCache.put(1L, "resource-1")
        testedResources.onSourceResolved(pixels, 1L)

        // Then
        assertThat(testedResources.findResourceId(source)).isNull()
    }

    @Test
    @Timeout(5)
    fun `M stop without waiting on resource lookup W stop`() {
        // Given
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val authoritative: LruCache<Long, String> = mock()
        whenever(authoritative.get(1L)) doAnswer {
            entered.countDown()
            release.await(3, TimeUnit.SECONDS)
            "resource-1"
        }
        val cache = DefaultCompositionSourceImageResources(authoritative)
        val source = bitmap()
        val pixels = bitmap()
        cache.findResourceId(source)
        cache.registerSourceCopy(source, pixels)
        cache.onSourceResolved(pixels, 1L)
        val executor = Executors.newSingleThreadExecutor()
        try {
            // When
            val lookup = executor.submit<String?> { cache.findResourceId(source) }
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue()
            cache.stop()
            release.countDown()

            // Then
            assertThat(lookup.get(1, TimeUnit.SECONDS)).isNull()
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    private fun weakMap(cache: DefaultCompositionSourceImageResources, name: String): Map<*, *> =
        cache.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(cache) as Map<*, *>
}
