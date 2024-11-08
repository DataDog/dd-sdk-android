/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Drawable.ConstantState
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.util.DisplayMetrics
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueHandler
import com.datadog.android.sessionreplay.internal.utils.DrawableUtils
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
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
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class ResourceResolverTest {
    private lateinit var testedResourceResolver: ResourceResolver

    @Mock
    lateinit var mockDrawableUtils: DrawableUtils

    @Mock
    lateinit var mockWebPImageCompression: ImageCompression

    @Mock
    lateinit var mockLogger: InternalLogger

    @Mock
    lateinit var mockApplicationContext: Context

    @Mock
    lateinit var mockMD5HashGenerator: MD5HashGenerator

    @Mock
    lateinit var mockSerializerCallback: ResourceResolverCallback

    @Mock
    lateinit var mockRecordedDataQueueHandler: RecordedDataQueueHandler

    @Mock
    lateinit var mockExecutorService: ExecutorService

    @Mock
    lateinit var mockResourcesLRUCache: ResourcesLRUCache

    @Mock
    lateinit var mockDisplayMetrics: DisplayMetrics

    @Mock
    lateinit var mockDrawable: Drawable

    @Mock
    lateinit var mockDrawableCopier: DrawableCopier

    @Mock
    lateinit var mockBitmap: Bitmap

    @Mock
    lateinit var mockStateListDrawable: StateListDrawable

    @Mock
    lateinit var mockBitmapCachesManager: BitmapCachesManager

    @Mock
    lateinit var mockBitmapDrawable: BitmapDrawable

    @Mock
    lateinit var mockResources: Resources

    @Mock
    lateinit var mockBitmapConstantState: ConstantState

    private var fakeBitmapWidth: Int = 1

    private var fakeBitmapHeight: Int = 1

    @Forgery
    lateinit var fakeApplicationid: UUID

    @StringForgery
    lateinit var fakeResourceId: String

    private lateinit var fakeImageCompressionByteArray: ByteArray

    @BeforeEach
    fun setup(forge: Forge) {
        whenever(mockDrawableCopier.copy(eq(mockBitmapDrawable), any())).thenReturn(
            mockBitmapDrawable
        )
        whenever(mockDrawableCopier.copy(eq(mockDrawable), any())).thenReturn(mockDrawable)
        fakeImageCompressionByteArray = forge.aString().toByteArray()

        fakeBitmapWidth = forge.anInt(min = 1)
        fakeBitmapHeight = forge.anInt(min = 1)

        whenever(mockMD5HashGenerator.generate(any())).thenReturn(fakeResourceId)

        whenever(mockWebPImageCompression.compressBitmap(any()))
            .thenReturn(fakeImageCompressionByteArray)

        whenever(
            mockDrawableUtils.createBitmapOfApproxSizeFromDrawable(
                resources = any(),
                drawable = any(),
                drawableWidth = any(),
                drawableHeight = any(),
                displayMetrics = any(),
                requestedSizeInBytes = anyOrNull(),
                config = anyOrNull(),
                bitmapCreationCallback = any()
            )
        ).then {
            (it.arguments[7] as ResourceResolver.BitmapCreationCallback).onReady(mockBitmap)
        }

        // executeSafe is an extension so we have to mock the internal execute function
        whenever(
            mockExecutorService.execute(
                any()
            )
        ).then {
            (it.arguments[0] as Runnable).run()
            mock<Future<Boolean>>()
        }

        whenever(mockBitmap.isRecycled).thenReturn(false)
        whenever(mockBitmap.width).thenReturn(fakeBitmapWidth)
        whenever(mockBitmap.height).thenReturn(fakeBitmapHeight)
        whenever(mockBitmapDrawable.bitmap).thenReturn(mockBitmap)

        testedResourceResolver = createResourceResolver()
    }

    @Test
    fun `M get data from cache W resolveResourceId() { cache hit with resourceId }`() {
        // Given
        whenever(mockBitmapCachesManager.getFromResourceCache(mockDrawable)).thenReturn(fakeResourceId)

        whenever(mockWebPImageCompression.compressBitmap(any()))
            .thenReturn(fakeImageCompressionByteArray)

        // When
        testedResourceResolver.resolveResourceId(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        verifyNoInteractions(mockDrawableUtils)
        verify(mockSerializerCallback).onSuccess(fakeResourceId)
    }

    @Test
    fun `M retry image creation only once W resolveResourceId() { image was recycled while working on it }`() {
        // Given
        whenever(mockDrawableUtils.createScaledBitmap(any(), anyOrNull()))
            .thenReturn(mockBitmap)
        whenever(mockBitmapDrawable.bitmap).thenReturn(mockBitmap)

        whenever(mockBitmap.isRecycled)
            .thenReturn(false)
            .thenReturn(true)

        val emptyByteArray = ByteArray(0)

        whenever(mockBitmapCachesManager.getFromResourceCache(mockBitmapDrawable))
            .thenReturn(null)

        whenever(mockWebPImageCompression.compressBitmap(any()))
            .thenReturn(emptyByteArray)
            .thenReturn(fakeImageCompressionByteArray)

        // When
        testedResourceResolver.resolveResourceId(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockBitmapDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        verify(mockDrawableUtils).createBitmapOfApproxSizeFromDrawable(
            resources = any(),
            drawable = any(),
            drawableWidth = any(),
            drawableHeight = any(),
            displayMetrics = any(),
            requestedSizeInBytes = anyOrNull(),
            config = anyOrNull(),
            bitmapCreationCallback = any()
        )
    }

    @Test
    fun `M send onReady W resolveResourceId(Drawable) { failed to get image data }`() {
        // Given
        whenever(mockBitmap.isRecycled)
            .thenReturn(true)
            .thenReturn(false)

        val emptyByteArray = ByteArray(0)

        whenever(mockWebPImageCompression.compressBitmap(any()))
            .thenReturn(emptyByteArray)

        // When
        testedResourceResolver.resolveResourceId(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        verify(mockSerializerCallback).onFailure()
    }

    @Test
    fun `M call onFailure W copy bitmap return null`() {
        // Given
        whenever(
            mockDrawableCopier.copy(
                originalDrawable = mockDrawable,
                resources = mockResources
            )
        ).doReturn(null)

        // When
        testedResourceResolver.resolveResourceId(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        verify(mockSerializerCallback).onFailure()
    }

    @Test
    fun `M send onReady W resolveResourceId(Bitmap) { failed to get image data }`() {
        // Given
        whenever(mockBitmap.isRecycled)
            .thenReturn(true)
            .thenReturn(false)

        val emptyByteArray = ByteArray(0)

        whenever(mockWebPImageCompression.compressBitmap(any()))
            .thenReturn(emptyByteArray)

        // When
        testedResourceResolver.resolveResourceId(
            bitmap = mockBitmap,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        verify(mockSerializerCallback).onFailure()
    }

    @Test
    fun `M calculate resourceId W resolveResourceId() { cache miss }`() {
        // Given
        whenever(mockResourcesLRUCache.get(mockDrawable)).thenReturn(null)

        // When
        testedResourceResolver.resolveResourceId(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        verify(mockDrawableUtils).createBitmapOfApproxSizeFromDrawable(
            resources = any(),
            drawable = any(),
            drawableWidth = any(),
            drawableHeight = any(),
            displayMetrics = any(),
            requestedSizeInBytes = anyOrNull(),
            config = anyOrNull(),
            bitmapCreationCallback = any()
        )
    }

    @Test
    fun `M return failure W resolveResourceId { createBitmapOfApproxSizeFromDrawable failed }`() {
        // Given
        whenever(mockResourcesLRUCache.get(mockDrawable)).thenReturn(null)
        whenever(
            mockDrawableUtils.createBitmapOfApproxSizeFromDrawable(
                resources = any(),
                drawable = any(),
                drawableWidth = any(),
                drawableHeight = any(),
                displayMetrics = any(),
                requestedSizeInBytes = anyOrNull(),
                config = anyOrNull(),
                bitmapCreationCallback = any()
            )
        ).then {
            (it.arguments[7] as ResourceResolver.BitmapCreationCallback).onFailure()
        }

        // When
        testedResourceResolver.resolveResourceId(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        verify(mockSerializerCallback).onFailure()
    }

    @Test
    fun `M use the same ThreadPoolExecutor W build()`() {
        // When
        val instance1 = ResourceResolver(
            recordedDataQueueHandler = mockRecordedDataQueueHandler,
            applicationId = fakeApplicationid.toString(),
            webPImageCompression = mockWebPImageCompression,
            drawableUtils = mockDrawableUtils,
            logger = mockLogger,
            md5HashGenerator = mockMD5HashGenerator,
            bitmapCachesManager = mockBitmapCachesManager
        )
        val instance2 = ResourceResolver(
            recordedDataQueueHandler = mockRecordedDataQueueHandler,
            applicationId = fakeApplicationid.toString(),
            webPImageCompression = mockWebPImageCompression,
            drawableUtils = mockDrawableUtils,
            logger = mockLogger,
            md5HashGenerator = mockMD5HashGenerator,
            bitmapCachesManager = mockBitmapCachesManager
        )

        // Then
        assertThat(instance1.threadPoolExecutor).isEqualTo(
            instance2.threadPoolExecutor
        )
    }

    @Test
    fun `M not try to cache resourceId W resolveResourceId() { and did not get resourceId }`() {
        // Given
        whenever(mockMD5HashGenerator.generate(any())).thenReturn(null)

        // When
        testedResourceResolver.resolveResourceId(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockStateListDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        verify(mockResourcesLRUCache, times(0)).put(any(), any())
    }

    @Test
    fun `M not use bitmap from bitmapDrawable W resolveResourceId() { no bitmap }`() {
        // Given
        whenever(mockBitmapDrawable.bitmap).thenReturn(null)

        // When
        testedResourceResolver.resolveResourceId(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockBitmapDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        verify(mockDrawableUtils, times(1)).createBitmapOfApproxSizeFromDrawable(
            resources = any(),
            drawable = any(),
            drawableWidth = any(),
            drawableHeight = any(),
            displayMetrics = any(),
            requestedSizeInBytes = anyOrNull(),
            config = anyOrNull(),
            bitmapCreationCallback = any()
        )
    }

    @Test
    fun `M not use bitmap from bitmapDrawable W resolveResourceId() { bitmap was recycled }`() {
        // Given
        whenever(mockBitmap.isRecycled).thenReturn(true)

        // When
        testedResourceResolver.resolveResourceId(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockBitmapDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        verify(mockDrawableUtils, times(1)).createBitmapOfApproxSizeFromDrawable(
            resources = any(),
            drawable = any(),
            drawableWidth = any(),
            drawableHeight = any(),
            displayMetrics = any(),
            requestedSizeInBytes = anyOrNull(),
            config = anyOrNull(),
            bitmapCreationCallback = any()
        )
    }

    @Test
    fun `M use scaled bitmap from bitmapDrawable W resolveResourceId() { has bitmap }`() {
        // When
        testedResourceResolver.resolveResourceId(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockBitmapDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        verify(mockDrawableUtils).createScaledBitmap(
            any(),
            anyOrNull()
        )
    }

    @Test
    fun `M draw bitmap W resolveResourceId() { bitmapDrawable where bitmap has no width }`() {
        // Given
        whenever(mockBitmap.width).thenReturn(0)

        // When
        testedResourceResolver.resolveResourceId(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockBitmapDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        verify(mockDrawableUtils, never()).createScaledBitmap(
            any(),
            anyOrNull()
        )
        verify(mockDrawableUtils, times(1)).createBitmapOfApproxSizeFromDrawable(
            resources = any(),
            drawable = any(),
            drawableWidth = any(),
            drawableHeight = any(),
            displayMetrics = any(),
            requestedSizeInBytes = anyOrNull(),
            config = anyOrNull(),
            bitmapCreationCallback = any()
        )
    }

    @Test
    fun `M draw bitmap W resolveResourceId() { bitmapDrawable where bitmap has no height }`() {
        // Given
        whenever(mockBitmap.height).thenReturn(0)

        // When
        testedResourceResolver.resolveResourceId(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockBitmapDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        verify(mockDrawableUtils, never()).createScaledBitmap(
            any(),
            anyOrNull()
        )
        verify(mockDrawableUtils, times(1)).createBitmapOfApproxSizeFromDrawable(
            resources = any(),
            drawable = any(),
            drawableWidth = any(),
            drawableHeight = any(),
            displayMetrics = any(),
            requestedSizeInBytes = anyOrNull(),
            config = anyOrNull(),
            bitmapCreationCallback = any()
        )
    }

    @Test
    fun `M not cache bitmap W resolveResourceId() { BitmapDrawable with bitmap not resized }`() {
        // Given
        whenever(mockDrawableUtils.createScaledBitmap(any(), anyOrNull()))
            .thenReturn(mockBitmap)

        // When
        testedResourceResolver.resolveResourceId(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockBitmapDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        verify(mockBitmapCachesManager, never()).putInBitmapPool(any())
    }

    @Test
    fun `M cache bitmap W resolveResourceId() { BitmapDrawable width was resized }`(
        @Mock mockResizedBitmap: Bitmap,
        @StringForgery fakeString: String
    ) {
        // Given
        val fakeByteArray = fakeString.toByteArray()
        assertThat(fakeByteArray).isNotEmpty()

        whenever(mockBitmap.isRecycled).thenReturn(false)
        whenever(mockResizedBitmap.width).thenReturn(fakeBitmapWidth - 1)
        whenever(mockResizedBitmap.height).thenReturn(fakeBitmapHeight)

        whenever(mockWebPImageCompression.compressBitmap(mockResizedBitmap)).thenReturn(fakeByteArray)
        whenever(mockDrawableUtils.createScaledBitmap(any(), anyOrNull())).thenReturn(mockResizedBitmap)

        // When
        testedResourceResolver.resolveResourceId(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockBitmapDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        verify(mockBitmapCachesManager).putInBitmapPool(any())
    }

    @Test
    fun `M cache bitmap W resolveResourceId() { BitmapDrawable height was resized }`(
        @Mock mockResizedBitmap: Bitmap,
        @StringForgery fakeString: String
    ) {
        // Given
        val fakeByteArray = fakeString.toByteArray()
        assertThat(fakeByteArray).isNotEmpty()

        whenever(mockBitmap.isRecycled).thenReturn(false)
        whenever(mockResizedBitmap.width).thenReturn(fakeBitmapWidth)
        whenever(mockResizedBitmap.height).thenReturn(fakeBitmapHeight - 1)

        whenever(mockWebPImageCompression.compressBitmap(mockResizedBitmap)).thenReturn(fakeByteArray)
        whenever(mockDrawableUtils.createScaledBitmap(any(), anyOrNull())).thenReturn(mockResizedBitmap)

        // When
        testedResourceResolver.resolveResourceId(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockBitmapDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        verify(mockBitmapCachesManager).putInBitmapPool(any())
    }

    @Test
    fun `M cache bitmap W resolveResourceId() { from BitmapDrawable with null bitmap }`() {
        // Given
        whenever(mockBitmapCachesManager.getFromResourceCache(mockBitmapDrawable))
            .thenReturn(null)
        whenever(mockBitmapDrawable.bitmap).thenReturn(null)

        // When
        testedResourceResolver.resolveResourceId(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockBitmapDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        verify(mockBitmapCachesManager, times(1)).putInBitmapPool(any())
    }

    @Test
    fun `M cache bitmap W resolveResourceId() { not a BitmapDrawable }`() {
        // Given
        val mockLayerDrawable = mock<LayerDrawable>()
        whenever(mockDrawableCopier.copy(any(), any())).thenReturn(mockLayerDrawable)
        // When
        testedResourceResolver.resolveResourceId(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockLayerDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = mockDrawable.intrinsicWidth,
            drawableHeight = mockDrawable.intrinsicHeight,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        verify(mockBitmapCachesManager, times(1)).putInBitmapPool(any())
    }

    @Test
    fun `M return all callbacks W resolveResourceId(Drawable) { multiple threads, first takes longer }`(
        @Mock mockFirstCallback: ResourceResolverCallback,
        @Mock mockSecondCallback: ResourceResolverCallback,
        @Mock mockFirstDrawable: Drawable,
        @Mock mockSecondDrawable: Drawable,
        @StringForgery fakeFirstResourceId: String,
        @StringForgery fakeSecondResourceId: String
    ) {
        // Given
        whenever(mockBitmapCachesManager.getFromResourceCache(mockFirstDrawable))
            .thenReturn(fakeFirstResourceId)
        whenever(mockBitmapCachesManager.getFromResourceCache(mockSecondDrawable))
            .thenReturn(fakeSecondResourceId)

        val countDownLatch = CountDownLatch(2)
        val thread1 = Thread {
            testedResourceResolver.resolveResourceId(
                resources = mockResources,
                applicationContext = mockApplicationContext,
                displayMetrics = mockDisplayMetrics,
                originalDrawable = mockFirstDrawable,
                drawableCopier = mockDrawableCopier,
                drawableWidth = fakeBitmapWidth,
                drawableHeight = fakeBitmapHeight,
                resourceResolverCallback = mockFirstCallback
            )
            Thread.sleep(1500)
            countDownLatch.countDown()
        }
        val thread2 = Thread {
            testedResourceResolver.resolveResourceId(
                resources = mockResources,
                applicationContext = mockApplicationContext,
                displayMetrics = mockDisplayMetrics,
                originalDrawable = mockSecondDrawable,
                drawableCopier = mockDrawableCopier,
                drawableWidth = fakeBitmapWidth,
                drawableHeight = fakeBitmapHeight,
                resourceResolverCallback = mockSecondCallback
            )
            Thread.sleep(500)
            countDownLatch.countDown()
        }

        // When
        thread1.start()
        thread2.start()

        // Then
        countDownLatch.await()
        verify(mockFirstCallback).onSuccess(fakeFirstResourceId)
        verify(mockSecondCallback).onSuccess(fakeSecondResourceId)
    }

    @Test
    fun `M return all callbacks W resolveResourceId(Bitmap) { multiple threads, first takes longer }`(
        @Mock mockFirstCallback: ResourceResolverCallback,
        @Mock mockSecondCallback: ResourceResolverCallback,
        @Mock mockFirstBitmap: Bitmap,
        @Mock mockSecondBitmap: Bitmap,
        @StringForgery fakeFirstResourceId: String,
        @StringForgery fakeSecondResourceId: String,
        forge: Forge
    ) {
        // Given
        val firstBitmapCompression = forge.aString().toByteArray()
        val secondBitmapCompression = forge.aString().toByteArray()
        whenever(mockWebPImageCompression.compressBitmap(mockFirstBitmap)).thenReturn(firstBitmapCompression)
        whenever(mockWebPImageCompression.compressBitmap(mockSecondBitmap)).thenReturn(secondBitmapCompression)
        whenever(mockMD5HashGenerator.generate(firstBitmapCompression)).thenReturn(fakeFirstResourceId)
        whenever(mockMD5HashGenerator.generate(secondBitmapCompression)).thenReturn(fakeSecondResourceId)
        val countDownLatch = CountDownLatch(2)
        val thread1 = Thread {
            testedResourceResolver.resolveResourceId(
                bitmap = mockFirstBitmap,
                resourceResolverCallback = mockFirstCallback
            )
            Thread.sleep(1500)
            countDownLatch.countDown()
        }
        val thread2 = Thread {
            testedResourceResolver.resolveResourceId(
                bitmap = mockSecondBitmap,
                resourceResolverCallback = mockSecondCallback
            )
            Thread.sleep(500)
            countDownLatch.countDown()
        }

        // When
        thread1.start()
        thread2.start()

        // Then
        countDownLatch.await()
        verify(mockFirstCallback).onSuccess(fakeFirstResourceId)
        verify(mockSecondCallback).onSuccess(fakeSecondResourceId)
    }

    @Test
    fun `M failover to bitmap creation W resolveResourceId() { bitmapDrawable returned empty bytearray }`(
        @Mock mockCreatedBitmap: Bitmap
    ) {
        // Given
        whenever(mockBitmapDrawable.bitmap).thenReturn(mockBitmap)
        whenever(mockBitmap.width).thenReturn(fakeBitmapWidth)
        whenever(mockBitmap.height).thenReturn(fakeBitmapHeight)

        whenever(mockBitmap.isRecycled)
            .thenReturn(true)
            .thenReturn(false)

        val emptyByteArray = ByteArray(0)
        whenever(mockWebPImageCompression.compressBitmap(mockBitmap))
            .thenReturn(emptyByteArray)

        whenever(mockWebPImageCompression.compressBitmap(mockCreatedBitmap))
            .thenReturn(fakeImageCompressionByteArray)

        whenever(mockDrawableUtils.createScaledBitmap(mockBitmap))
            .thenReturn(mockBitmap)
            .thenReturn(mockCreatedBitmap)

        // When
        testedResourceResolver.resolveResourceId(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockBitmapDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = fakeBitmapWidth,
            drawableHeight = fakeBitmapHeight,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then
        verify(mockDrawableUtils).createBitmapOfApproxSizeFromDrawable(
            resources = any(),
            drawable = any(),
            drawableWidth = any(),
            drawableHeight = any(),
            displayMetrics = any(),
            requestedSizeInBytes = anyOrNull(),
            config = anyOrNull(),
            bitmapCreationCallback = any()
        )
    }

    @Test
    fun `M only send resource once W resolveResourceId() { call twice on the same image }`(
        @Mock mockCreatedBitmap: Bitmap,
        @StringForgery fakeResourceId: String,
        @StringForgery fakeResource: String
    ) {
        // Given
        whenever(mockBitmapDrawable.bitmap).thenReturn(mockBitmap)
        whenever(mockBitmap.width).thenReturn(fakeBitmapWidth)
        whenever(mockBitmap.height).thenReturn(fakeBitmapHeight)
        whenever(mockMD5HashGenerator.generate(any())).thenReturn(fakeResourceId)

        whenever(mockBitmap.isRecycled)
            .thenReturn(true)
            .thenReturn(false)

        val fakeByteArray = fakeResource.toByteArray()
        whenever(mockWebPImageCompression.compressBitmap(mockBitmap))
            .thenReturn(fakeByteArray)

        whenever(mockWebPImageCompression.compressBitmap(mockCreatedBitmap))
            .thenReturn(fakeImageCompressionByteArray)

        whenever(mockDrawableUtils.createScaledBitmap(mockBitmap))
            .thenReturn(mockBitmap)
            .thenReturn(mockCreatedBitmap)

        // When
        testedResourceResolver.resolveResourceId(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockBitmapDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = fakeBitmapWidth,
            drawableHeight = fakeBitmapHeight,
            resourceResolverCallback = mockSerializerCallback
        )

        // Then

        // second time
        testedResourceResolver.resolveResourceId(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockBitmapDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = fakeBitmapWidth,
            drawableHeight = fakeBitmapHeight,
            resourceResolverCallback = mockSerializerCallback
        )

        verify(mockRecordedDataQueueHandler, times(1)).addResourceItem(
            identifier = eq(fakeResourceId),
            applicationId = eq(fakeApplicationid.toString()),
            resourceData = eq(fakeByteArray)
        )

        // second time
        testedResourceResolver.resolveResourceId(
            resources = mockResources,
            applicationContext = mockApplicationContext,
            displayMetrics = mockDisplayMetrics,
            originalDrawable = mockBitmapDrawable,
            drawableCopier = mockDrawableCopier,
            drawableWidth = fakeBitmapWidth,
            drawableHeight = fakeBitmapHeight,
            resourceResolverCallback = mockSerializerCallback
        )

        verify(mockRecordedDataQueueHandler, times(1)).addResourceItem(
            identifier = eq(fakeResourceId),
            applicationId = eq(fakeApplicationid.toString()),
            resourceData = eq(fakeByteArray)
        )
    }

    private fun createResourceResolver(): ResourceResolver = ResourceResolver(
        logger = mockLogger,
        threadPoolExecutor = mockExecutorService,
        drawableUtils = mockDrawableUtils,
        webPImageCompression = mockWebPImageCompression,
        md5HashGenerator = mockMD5HashGenerator,
        recordedDataQueueHandler = mockRecordedDataQueueHandler,
        applicationId = fakeApplicationid.toString(),
        bitmapCachesManager = mockBitmapCachesManager
    )
}
