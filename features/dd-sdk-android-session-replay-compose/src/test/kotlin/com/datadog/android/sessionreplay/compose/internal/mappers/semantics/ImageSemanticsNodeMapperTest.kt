/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.mappers.semantics

import android.graphics.Bitmap
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.SemanticsNode
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.compose.internal.data.BitmapInfo
import com.datadog.android.sessionreplay.compose.internal.data.UiContext
import com.datadog.android.sessionreplay.compose.test.elmyr.SessionReplayComposeForgeConfigurator
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.ImageWireframeHelper
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
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
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(SessionReplayComposeForgeConfigurator::class)
internal class ImageSemanticsNodeMapperTest : AbstractSemanticsNodeMapperTest() {

    private lateinit var testedMapper: ImageSemanticsNodeMapper

    @Mock
    private lateinit var mockSemanticsNode: SemanticsNode

    @Mock
    private lateinit var mockAsyncJobStatusCallback: AsyncJobStatusCallback

    @Mock
    private lateinit var mockImageWireframeHelper: ImageWireframeHelper

    @Mock
    private lateinit var mockBitmap: Bitmap

    @Forgery
    lateinit var fakeUiContext: UiContext

    @Forgery
    lateinit var fakeImageWireframe: MobileSegment.Wireframe.ImageWireframe

    @IntForgery(min = 100, max = 500)
    var fakeBitmapWidth: Int = 0

    @IntForgery(min = 100, max = 500)
    var fakeBitmapHeight: Int = 0

    @FloatForgery(min = 1f, max = 3f)
    var fakeDensityValue: Float = 1f

    @BeforeEach
    override fun `set up`(forge: Forge) {
        super.`set up`(forge)

        whenever(mockBitmap.width) doReturn fakeBitmapWidth
        whenever(mockBitmap.height) doReturn fakeBitmapHeight

        fakeUiContext = fakeUiContext.copy(
            imageWireframeHelper = mockImageWireframeHelper,
            density = fakeDensityValue
        )

        testedMapper = ImageSemanticsNodeMapper(
            colorStringFormatter = mockColorStringFormatter,
            semanticsUtils = mockSemanticsUtils
        )
    }

    // region calculateScaledImageBounds tests

    @Test
    fun `M return container bounds W calculateScaledImageBounds { ContentScale Fit, image fits exactly }`() {
        // Given
        val containerBounds = GlobalBounds(x = 0, y = 0, width = 100, height = 100)
        val bitmapInfo = BitmapInfo(
            bitmap = mockBitmap,
            isContextualImage = false,
            contentScale = ContentScale.Fit,
            alignment = Alignment.Center
        )
        whenever(mockBitmap.width) doReturn 100
        whenever(mockBitmap.height) doReturn 100

        // When
        val result = testedMapper.calculateScaledImageBounds(
            containerBounds = containerBounds,
            bitmapInfo = bitmapInfo,
            density = 1f
        )

        // Then
        assertThat(result.bounds.width).isEqualTo(100L)
        assertThat(result.bounds.height).isEqualTo(100L)
        assertThat(result.clipping).isNull()
    }

    @Test
    fun `M scale down maintaining aspect ratio W calculateScaledImageBounds { ContentScale Fit, wider image }`() {
        // Given
        val containerBounds = GlobalBounds(x = 0, y = 0, width = 100, height = 100)
        val bitmapInfo = BitmapInfo(
            bitmap = mockBitmap,
            isContextualImage = false,
            contentScale = ContentScale.Fit,
            alignment = Alignment.Center
        )
        whenever(mockBitmap.width) doReturn 200
        whenever(mockBitmap.height) doReturn 100

        // When
        val result = testedMapper.calculateScaledImageBounds(
            containerBounds = containerBounds,
            bitmapInfo = bitmapInfo,
            density = 1f
        )

        // Then
        assertThat(result.bounds.width).isEqualTo(100L)
        assertThat(result.bounds.height).isEqualTo(50L)
        assertThat(result.clipping).isNull()
    }

    @Test
    fun `M scale down maintaining aspect ratio W calculateScaledImageBounds { ContentScale Fit, taller image }`() {
        // Given
        val containerBounds = GlobalBounds(x = 0, y = 0, width = 100, height = 100)
        val bitmapInfo = BitmapInfo(
            bitmap = mockBitmap,
            isContextualImage = false,
            contentScale = ContentScale.Fit,
            alignment = Alignment.Center
        )
        whenever(mockBitmap.width) doReturn 100
        whenever(mockBitmap.height) doReturn 200

        // When
        val result = testedMapper.calculateScaledImageBounds(
            containerBounds = containerBounds,
            bitmapInfo = bitmapInfo,
            density = 1f
        )

        // Then
        assertThat(result.bounds.width).isEqualTo(50L)
        assertThat(result.bounds.height).isEqualTo(100L)
        assertThat(result.clipping).isNull()
    }

    @Test
    fun `M scale up to fill and clip W calculateScaledImageBounds { ContentScale Crop, smaller image }`() {
        // Given
        val containerBounds = GlobalBounds(x = 0, y = 0, width = 100, height = 100)
        val bitmapInfo = BitmapInfo(
            bitmap = mockBitmap,
            isContextualImage = false,
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center
        )
        whenever(mockBitmap.width) doReturn 200
        whenever(mockBitmap.height) doReturn 100

        // When
        val result = testedMapper.calculateScaledImageBounds(
            containerBounds = containerBounds,
            bitmapInfo = bitmapInfo,
            density = 1f
        )

        // Then
        assertThat(result.bounds.width).isEqualTo(200L)
        assertThat(result.bounds.height).isEqualTo(100L)
        assertThat(result.clipping).isNotNull
        assertThat(result.clipping?.left).isEqualTo(50L)
        assertThat(result.clipping?.right).isEqualTo(50L)
    }

    @Test
    fun `M fill bounds exactly W calculateScaledImageBounds { ContentScale FillBounds }`() {
        // Given
        val containerBounds = GlobalBounds(x = 0, y = 0, width = 100, height = 100)
        val bitmapInfo = BitmapInfo(
            bitmap = mockBitmap,
            isContextualImage = false,
            contentScale = ContentScale.FillBounds,
            alignment = Alignment.Center
        )
        whenever(mockBitmap.width) doReturn 200
        whenever(mockBitmap.height) doReturn 50

        // When
        val result = testedMapper.calculateScaledImageBounds(
            containerBounds = containerBounds,
            bitmapInfo = bitmapInfo,
            density = 1f
        )

        // Then
        assertThat(result.bounds.width).isEqualTo(100L)
        assertThat(result.bounds.height).isEqualTo(100L)
        assertThat(result.clipping).isNull()
    }

    @Test
    fun `M fill width and maintain aspect ratio W calculateScaledImageBounds { ContentScale FillWidth }`() {
        // Given
        val containerBounds = GlobalBounds(x = 0, y = 0, width = 100, height = 100)
        val bitmapInfo = BitmapInfo(
            bitmap = mockBitmap,
            isContextualImage = false,
            contentScale = ContentScale.FillWidth,
            alignment = Alignment.Center
        )
        whenever(mockBitmap.width) doReturn 200
        whenever(mockBitmap.height) doReturn 100

        // When
        val result = testedMapper.calculateScaledImageBounds(
            containerBounds = containerBounds,
            bitmapInfo = bitmapInfo,
            density = 1f
        )

        // Then
        assertThat(result.bounds.width).isEqualTo(100L)
        assertThat(result.bounds.height).isEqualTo(50L)
        assertThat(result.clipping).isNull()
    }

    @Test
    fun `M fill height and maintain aspect ratio W calculateScaledImageBounds { ContentScale FillHeight }`() {
        // Given
        val containerBounds = GlobalBounds(x = 0, y = 0, width = 100, height = 100)
        val bitmapInfo = BitmapInfo(
            bitmap = mockBitmap,
            isContextualImage = false,
            contentScale = ContentScale.FillHeight,
            alignment = Alignment.Center
        )
        whenever(mockBitmap.width) doReturn 200
        whenever(mockBitmap.height) doReturn 100

        // When
        val result = testedMapper.calculateScaledImageBounds(
            containerBounds = containerBounds,
            bitmapInfo = bitmapInfo,
            density = 1f
        )

        // Then
        assertThat(result.bounds.width).isEqualTo(200L)
        assertThat(result.bounds.height).isEqualTo(100L)
        assertThat(result.clipping).isNotNull
    }

    @Test
    fun `M not scale up small image W calculateScaledImageBounds { ContentScale Inside, image smaller }`() {
        // Given
        val containerBounds = GlobalBounds(x = 0, y = 0, width = 100, height = 100)
        val bitmapInfo = BitmapInfo(
            bitmap = mockBitmap,
            isContextualImage = false,
            contentScale = ContentScale.Inside,
            alignment = Alignment.Center
        )
        whenever(mockBitmap.width) doReturn 50
        whenever(mockBitmap.height) doReturn 50

        // When
        val result = testedMapper.calculateScaledImageBounds(
            containerBounds = containerBounds,
            bitmapInfo = bitmapInfo,
            density = 1f
        )

        // Then
        assertThat(result.bounds.width).isEqualTo(50L)
        assertThat(result.bounds.height).isEqualTo(50L)
        assertThat(result.bounds.x).isEqualTo(25L)
        assertThat(result.bounds.y).isEqualTo(25L)
        assertThat(result.clipping).isNull()
    }

    @Test
    fun `M scale down large image W calculateScaledImageBounds { ContentScale Inside, image larger }`() {
        // Given
        val containerBounds = GlobalBounds(x = 0, y = 0, width = 100, height = 100)
        val bitmapInfo = BitmapInfo(
            bitmap = mockBitmap,
            isContextualImage = false,
            contentScale = ContentScale.Inside,
            alignment = Alignment.Center
        )
        whenever(mockBitmap.width) doReturn 200
        whenever(mockBitmap.height) doReturn 200

        // When
        val result = testedMapper.calculateScaledImageBounds(
            containerBounds = containerBounds,
            bitmapInfo = bitmapInfo,
            density = 1f
        )

        // Then
        assertThat(result.bounds.width).isEqualTo(100L)
        assertThat(result.bounds.height).isEqualTo(100L)
        assertThat(result.clipping).isNull()
    }

    @Test
    fun `M use original size W calculateScaledImageBounds { ContentScale None }`() {
        // Given
        val containerBounds = GlobalBounds(x = 0, y = 0, width = 100, height = 100)
        val bitmapInfo = BitmapInfo(
            bitmap = mockBitmap,
            isContextualImage = false,
            contentScale = ContentScale.None,
            alignment = Alignment.Center
        )
        whenever(mockBitmap.width) doReturn 200
        whenever(mockBitmap.height) doReturn 200

        // When
        val result = testedMapper.calculateScaledImageBounds(
            containerBounds = containerBounds,
            bitmapInfo = bitmapInfo,
            density = 1f
        )

        // Then
        assertThat(result.bounds.width).isEqualTo(200L)
        assertThat(result.bounds.height).isEqualTo(200L)
        assertThat(result.clipping).isNotNull
        assertThat(result.clipping?.left).isEqualTo(50L)
        assertThat(result.clipping?.top).isEqualTo(50L)
        assertThat(result.clipping?.right).isEqualTo(50L)
        assertThat(result.clipping?.bottom).isEqualTo(50L)
    }

    @Test
    fun `M align to top start W calculateScaledImageBounds { Alignment TopStart }`() {
        // Given
        val containerBounds = GlobalBounds(x = 0, y = 0, width = 100, height = 100)
        val bitmapInfo = BitmapInfo(
            bitmap = mockBitmap,
            isContextualImage = false,
            contentScale = ContentScale.None,
            alignment = Alignment.TopStart
        )
        whenever(mockBitmap.width) doReturn 50
        whenever(mockBitmap.height) doReturn 50

        // When
        val result = testedMapper.calculateScaledImageBounds(
            containerBounds = containerBounds,
            bitmapInfo = bitmapInfo,
            density = 1f
        )

        // Then
        assertThat(result.bounds.x).isEqualTo(0L)
        assertThat(result.bounds.y).isEqualTo(0L)
        assertThat(result.bounds.width).isEqualTo(50L)
        assertThat(result.bounds.height).isEqualTo(50L)
    }

    @Test
    fun `M align to bottom end W calculateScaledImageBounds { Alignment BottomEnd }`() {
        // Given
        val containerBounds = GlobalBounds(x = 0, y = 0, width = 100, height = 100)
        val bitmapInfo = BitmapInfo(
            bitmap = mockBitmap,
            isContextualImage = false,
            contentScale = ContentScale.None,
            alignment = Alignment.BottomEnd
        )
        whenever(mockBitmap.width) doReturn 50
        whenever(mockBitmap.height) doReturn 50

        // When
        val result = testedMapper.calculateScaledImageBounds(
            containerBounds = containerBounds,
            bitmapInfo = bitmapInfo,
            density = 1f
        )

        // Then
        assertThat(result.bounds.x).isEqualTo(50L)
        assertThat(result.bounds.y).isEqualTo(50L)
        assertThat(result.bounds.width).isEqualTo(50L)
        assertThat(result.bounds.height).isEqualTo(50L)
    }

    @Test
    fun `M account for density W calculateScaledImageBounds { with density }`() {
        // Given
        val containerBounds = GlobalBounds(x = 0, y = 0, width = 100, height = 100)
        val bitmapInfo = BitmapInfo(
            bitmap = mockBitmap,
            isContextualImage = false,
            contentScale = ContentScale.None,
            alignment = Alignment.Center
        )
        whenever(mockBitmap.width) doReturn 200
        whenever(mockBitmap.height) doReturn 200

        // When
        val result = testedMapper.calculateScaledImageBounds(
            containerBounds = containerBounds,
            bitmapInfo = bitmapInfo,
            density = 2f
        )

        // Then
        assertThat(result.bounds.width).isEqualTo(100L)
        assertThat(result.bounds.height).isEqualTo(100L)
    }

    @Test
    fun `M use default Fit behavior W calculateScaledImageBounds { null contentScale }`() {
        // Given
        val containerBounds = GlobalBounds(x = 0, y = 0, width = 100, height = 100)
        val bitmapInfo = BitmapInfo(
            bitmap = mockBitmap,
            isContextualImage = false,
            contentScale = null,
            alignment = Alignment.Center
        )
        whenever(mockBitmap.width) doReturn 200
        whenever(mockBitmap.height) doReturn 100

        // When
        val result = testedMapper.calculateScaledImageBounds(
            containerBounds = containerBounds,
            bitmapInfo = bitmapInfo,
            density = 1f
        )

        // Then
        assertThat(result.bounds.width).isEqualTo(100L)
        assertThat(result.bounds.height).isEqualTo(50L)
    }

    // endregion

    // region map tests

    @Test
    fun `M call imageWireframeHelper with scaled bounds W map { with bitmapInfo }`() {
        // Given
        val mockSemanticsNode = mockSemanticsNodeWithBound()
        val containerBounds = GlobalBounds(x = 10, y = 20, width = 100, height = 100)
        val bitmapInfo = BitmapInfo(
            bitmap = mockBitmap,
            isContextualImage = false,
            contentScale = ContentScale.Fit,
            alignment = Alignment.Center
        )
        whenever(mockBitmap.width) doReturn 100
        whenever(mockBitmap.height) doReturn 100
        whenever(mockSemanticsUtils.resolveInnerBounds(any())) doReturn containerBounds
        whenever(mockSemanticsUtils.resolveSemanticsPainter(any())) doReturn bitmapInfo
        whenever(mockSemanticsUtils.getImagePrivacyOverride(any())) doReturn null
        whenever(mockSemanticsUtils.resolveBackgroundInfo(any())) doReturn emptyList()
        whenever(
            mockImageWireframeHelper.createImageWireframeByBitmap(
                id = any(),
                globalBounds = any(),
                bitmap = any(),
                density = any(),
                isContextualImage = any(),
                imagePrivacy = any(),
                asyncJobStatusCallback = any(),
                clipping = anyOrNull(),
                shapeStyle = anyOrNull(),
                border = anyOrNull()
            )
        ) doReturn fakeImageWireframe

        // When
        val result = testedMapper.map(mockSemanticsNode, fakeUiContext, mockAsyncJobStatusCallback)

        // Then
        verify(mockImageWireframeHelper).createImageWireframeByBitmap(
            id = eq(fakeSemanticsId.toLong()),
            globalBounds = any(),
            bitmap = eq(mockBitmap),
            density = eq(fakeDensityValue),
            isContextualImage = eq(false),
            imagePrivacy = eq(fakeUiContext.imagePrivacy),
            asyncJobStatusCallback = eq(mockAsyncJobStatusCallback),
            clipping = anyOrNull(),
            shapeStyle = anyOrNull(),
            border = anyOrNull()
        )
        assertThat(result.wireframes).contains(fakeImageWireframe)
    }

    @Test
    fun `M return empty wireframes W map { no bitmapInfo }`() {
        // Given
        val mockSemanticsNode = mockSemanticsNodeWithBound()
        val containerBounds = rectToBounds(fakeBounds, fakeDensity)
        whenever(mockSemanticsUtils.resolveInnerBounds(mockSemanticsNode)) doReturn containerBounds
        whenever(mockSemanticsUtils.resolveSemanticsPainter(mockSemanticsNode)) doReturn null
        whenever(mockSemanticsUtils.resolveBackgroundInfo(mockSemanticsNode)) doReturn emptyList()

        // When
        val result = testedMapper.map(mockSemanticsNode, fakeUiContext, mockAsyncJobStatusCallback)

        // Then
        assertThat(result.wireframes).isEmpty()
    }

    @Test
    fun `M use image privacy override W map { privacy override set }`() {
        // Given
        val mockSemanticsNode = mockSemanticsNodeWithBound()
        val containerBounds = GlobalBounds(x = 10, y = 20, width = 100, height = 100)
        val bitmapInfo = BitmapInfo(
            bitmap = mockBitmap,
            isContextualImage = false,
            contentScale = ContentScale.Fit,
            alignment = Alignment.Center
        )
        whenever(mockBitmap.width) doReturn 100
        whenever(mockBitmap.height) doReturn 100
        whenever(mockSemanticsUtils.resolveInnerBounds(any())) doReturn containerBounds
        whenever(mockSemanticsUtils.resolveSemanticsPainter(any())) doReturn bitmapInfo
        whenever(mockSemanticsUtils.getImagePrivacyOverride(any())) doReturn ImagePrivacy.MASK_ALL
        whenever(mockSemanticsUtils.resolveBackgroundInfo(any())) doReturn emptyList()
        whenever(
            mockImageWireframeHelper.createImageWireframeByBitmap(
                id = any(),
                globalBounds = any(),
                bitmap = any(),
                density = any(),
                isContextualImage = any(),
                imagePrivacy = any(),
                asyncJobStatusCallback = any(),
                clipping = anyOrNull(),
                shapeStyle = anyOrNull(),
                border = anyOrNull()
            )
        ) doReturn fakeImageWireframe

        // When
        testedMapper.map(mockSemanticsNode, fakeUiContext, mockAsyncJobStatusCallback)

        // Then
        verify(mockImageWireframeHelper).createImageWireframeByBitmap(
            id = any(),
            globalBounds = any(),
            bitmap = any(),
            density = any(),
            isContextualImage = any(),
            imagePrivacy = eq(ImagePrivacy.MASK_ALL),
            asyncJobStatusCallback = any(),
            clipping = anyOrNull(),
            shapeStyle = anyOrNull(),
            border = anyOrNull()
        )
    }

    // endregion
}
