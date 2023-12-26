/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.processor

import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.model.MobileSegment
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
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class WireframeUtilsTest {

    lateinit var testedWireframeUtils: WireframeUtils

    @Mock
    private lateinit var mockBoundsUtils: BoundsUtils

    @BeforeEach
    fun `set up`() {
        testedWireframeUtils = WireframeUtils(mockBoundsUtils)
    }

    // region Clipping resolver

    @Test
    fun `M correctly resolve the Wireframe clip W resolveWireframeClip(smaller parent)`(
        forge: Forge
    ) {
        // Given
        val fakeWireframe: MobileSegment.Wireframe = forge.getForgery<MobileSegment.Wireframe>()
            .copy(
                clip = null
            )
        val fakeWireframeBounds: WireframeBounds = forge.getForgery<WireframeBounds>().apply {
            whenever(mockBoundsUtils.resolveBounds(fakeWireframe)).thenReturn(this)
        }
        val fakeExpectedClipTop = forge.aLong(min = 0, max = 100)
        val fakeExpectedClipLeft = forge.aLong(min = 0, max = 100)
        val top = fakeWireframeBounds.top + fakeExpectedClipTop
        val left = fakeWireframeBounds.left + fakeExpectedClipLeft
        val fakeExpectedClipRight = forge.aLong(min = 0, max = fakeWireframeBounds.right)
        val fakeExpectedClipBottom = forge.aLong(min = 0, max = fakeWireframeBounds.bottom)
        val fakeParentBounds = fakeWireframeBounds.copy(
            left = left,
            top = top,
            right = fakeWireframeBounds.right - fakeExpectedClipRight,
            bottom = fakeWireframeBounds.bottom - fakeExpectedClipBottom
        )
        val fakeParentWireframe: MobileSegment.Wireframe =
            forge.getForgery<MobileSegment.Wireframe>().apply {
                whenever(mockBoundsUtils.resolveBounds(this)).thenReturn(fakeParentBounds)
            }
        val fakeExpectedClip = MobileSegment.WireframeClip(
            top = fakeExpectedClipTop,
            bottom = fakeExpectedClipBottom,
            right = fakeExpectedClipRight,
            left = fakeExpectedClipLeft
        )

        val fakeRandomParents: List<MobileSegment.Wireframe> = forge.aList {
            forge.getForgery<MobileSegment.Wireframe>().apply {
                whenever(mockBoundsUtils.resolveBounds(this)).thenReturn(fakeWireframeBounds)
            }
        }.toMutableList()
        val randomIndex = forge.anInt(min = 0, max = fakeRandomParents.size)
        val fakeParents = fakeRandomParents.toMutableList().apply {
            add(randomIndex, fakeParentWireframe)
        }

        // When
        val resultClip = testedWireframeUtils.resolveWireframeClip(fakeWireframe, fakeParents)

        // Then
        assertThat(resultClip).isEqualTo(fakeExpectedClip)
    }

    @Test
    fun `M return Wireframe clip W resolveWireframeClip(bigger parent, clip is not empty)`(
        forge: Forge
    ) {
        // Given
        val fakeWireframe: MobileSegment.Wireframe = forge.getForgery<MobileSegment.Wireframe>().copy(
            clip = MobileSegment.WireframeClip(
                left = forge.aPositiveLong(true),
                right = forge.aPositiveLong(true),
                top = forge.aPositiveLong(true),
                bottom = forge.aPositiveLong(true)
            )
        )
        val fakeWireframeBounds: WireframeBounds = forge.getForgery<WireframeBounds>().apply {
            whenever(mockBoundsUtils.resolveBounds(fakeWireframe)).thenReturn(this)
        }
        val fakeParentBounds = fakeWireframeBounds.copy(
            left = fakeWireframeBounds.left - forge.aLong(min = 0, max = fakeWireframeBounds.left),
            top = fakeWireframeBounds.top - forge.aLong(min = 0, max = fakeWireframeBounds.top),
            right = fakeWireframeBounds.right +
                forge.aLong(min = 0, max = fakeWireframeBounds.right),
            bottom = fakeWireframeBounds.bottom +
                forge.aLong(min = 0, max = fakeWireframeBounds.bottom)
        )
        val fakeRandomParents: List<MobileSegment.Wireframe> = forge.aList {
            forge.getForgery<MobileSegment.Wireframe>().apply {
                whenever(mockBoundsUtils.resolveBounds(this)).thenReturn(fakeParentBounds)
            }
        }.toMutableList()
        // Then
        assertThat(testedWireframeUtils.resolveWireframeClip(fakeWireframe, fakeRandomParents))
            .isEqualTo(fakeWireframe.clip())
    }

    @Test
    fun `M return Wireframe clip W resolveWireframeClip(bigger parent, clip left is null)`(
        forge: Forge
    ) {
        // Given
        val fakeWireframe: MobileSegment.Wireframe = forge.getForgery<MobileSegment.Wireframe>().copy(
            clip = MobileSegment.WireframeClip(
                left = null,
                right = forge.aPositiveLong(true),
                top = forge.aPositiveLong(true),
                bottom = forge.aPositiveLong(true)
            )
        )
        val fakeWireframeBounds: WireframeBounds = forge.getForgery<WireframeBounds>().apply {
            whenever(mockBoundsUtils.resolveBounds(fakeWireframe)).thenReturn(this)
        }
        val fakeParentBounds = fakeWireframeBounds.copy(
            left = fakeWireframeBounds.left - forge.aLong(min = 0, max = fakeWireframeBounds.left),
            top = fakeWireframeBounds.top - forge.aLong(min = 0, max = fakeWireframeBounds.top),
            right = fakeWireframeBounds.right +
                forge.aLong(min = 0, max = fakeWireframeBounds.right),
            bottom = fakeWireframeBounds.bottom +
                forge.aLong(min = 0, max = fakeWireframeBounds.bottom)
        )
        val fakeRandomParents: List<MobileSegment.Wireframe> = forge.aList {
            forge.getForgery<MobileSegment.Wireframe>().apply {
                whenever(mockBoundsUtils.resolveBounds(this)).thenReturn(fakeParentBounds)
            }
        }.toMutableList()
        // Then
        assertThat(testedWireframeUtils.resolveWireframeClip(fakeWireframe, fakeRandomParents))
            .isEqualTo(fakeWireframe.clip())
    }

    @Test
    fun `M return Wireframe clip W resolveWireframeClip(bigger parent, clip right is null)`(
        forge: Forge
    ) {
        // Given
        val fakeWireframe: MobileSegment.Wireframe = forge.getForgery<MobileSegment.Wireframe>().copy(
            clip = MobileSegment.WireframeClip(
                left = forge.aPositiveLong(true),
                right = null,
                top = forge.aPositiveLong(true),
                bottom = forge.aPositiveLong(true)
            )
        )
        val fakeWireframeBounds: WireframeBounds = forge.getForgery<WireframeBounds>().apply {
            whenever(mockBoundsUtils.resolveBounds(fakeWireframe)).thenReturn(this)
        }
        val fakeParentBounds = fakeWireframeBounds.copy(
            left = fakeWireframeBounds.left - forge.aLong(min = 0, max = fakeWireframeBounds.left),
            top = fakeWireframeBounds.top - forge.aLong(min = 0, max = fakeWireframeBounds.top),
            right = fakeWireframeBounds.right +
                forge.aLong(min = 0, max = fakeWireframeBounds.right),
            bottom = fakeWireframeBounds.bottom +
                forge.aLong(min = 0, max = fakeWireframeBounds.bottom)
        )
        val fakeRandomParents: List<MobileSegment.Wireframe> = forge.aList {
            forge.getForgery<MobileSegment.Wireframe>().apply {
                whenever(mockBoundsUtils.resolveBounds(this)).thenReturn(fakeParentBounds)
            }
        }.toMutableList()
        // Then
        assertThat(testedWireframeUtils.resolveWireframeClip(fakeWireframe, fakeRandomParents))
            .isEqualTo(fakeWireframe.clip())
    }

    @Test
    fun `M return Wireframe clip W resolveWireframeClip(bigger parent, clip top is null)`(
        forge: Forge
    ) {
        // Given
        val fakeWireframe: MobileSegment.Wireframe = forge.getForgery<MobileSegment.Wireframe>().copy(
            clip = MobileSegment.WireframeClip(
                left = forge.aPositiveLong(true),
                right = forge.aPositiveLong(true),
                top = null,
                bottom = forge.aPositiveLong(true)
            )
        )
        val fakeWireframeBounds: WireframeBounds = forge.getForgery<WireframeBounds>().apply {
            whenever(mockBoundsUtils.resolveBounds(fakeWireframe)).thenReturn(this)
        }
        val fakeParentBounds = fakeWireframeBounds.copy(
            left = fakeWireframeBounds.left - forge.aLong(min = 0, max = fakeWireframeBounds.left),
            top = fakeWireframeBounds.top - forge.aLong(min = 0, max = fakeWireframeBounds.top),
            right = fakeWireframeBounds.right +
                forge.aLong(min = 0, max = fakeWireframeBounds.right),
            bottom = fakeWireframeBounds.bottom +
                forge.aLong(min = 0, max = fakeWireframeBounds.bottom)
        )
        val fakeRandomParents: List<MobileSegment.Wireframe> = forge.aList {
            forge.getForgery<MobileSegment.Wireframe>().apply {
                whenever(mockBoundsUtils.resolveBounds(this)).thenReturn(fakeParentBounds)
            }
        }.toMutableList()
        // Then
        assertThat(testedWireframeUtils.resolveWireframeClip(fakeWireframe, fakeRandomParents))
            .isEqualTo(fakeWireframe.clip())
    }

    @Test
    fun `M return Wireframe clip W resolveWireframeClip(bigger parent, clip bottom is null)`(
        forge: Forge
    ) {
        // Given
        val fakeWireframe: MobileSegment.Wireframe = forge.getForgery<MobileSegment.Wireframe>().copy(
            clip = MobileSegment.WireframeClip(
                left = forge.aPositiveLong(true),
                right = forge.aPositiveLong(true),
                top = forge.aPositiveLong(true),
                bottom = null
            )
        )
        val fakeWireframeBounds: WireframeBounds = forge.getForgery<WireframeBounds>().apply {
            whenever(mockBoundsUtils.resolveBounds(fakeWireframe)).thenReturn(this)
        }
        val fakeParentBounds = fakeWireframeBounds.copy(
            left = fakeWireframeBounds.left - forge.aLong(min = 0, max = fakeWireframeBounds.left),
            top = fakeWireframeBounds.top - forge.aLong(min = 0, max = fakeWireframeBounds.top),
            right = fakeWireframeBounds.right +
                forge.aLong(min = 0, max = fakeWireframeBounds.right),
            bottom = fakeWireframeBounds.bottom +
                forge.aLong(min = 0, max = fakeWireframeBounds.bottom)
        )
        val fakeRandomParents: List<MobileSegment.Wireframe> = forge.aList {
            forge.getForgery<MobileSegment.Wireframe>().apply {
                whenever(mockBoundsUtils.resolveBounds(this)).thenReturn(fakeParentBounds)
            }
        }.toMutableList()
        // Then
        assertThat(testedWireframeUtils.resolveWireframeClip(fakeWireframe, fakeRandomParents))
            .isEqualTo(fakeWireframe.clip())
    }

    @Test
    fun `M return null W resolveWireframeClip(bigger parent, clip is empty)`(
        forge: Forge
    ) {
        // Given
        val fakeWireframe: MobileSegment.Wireframe = forge.getForgery<MobileSegment.Wireframe>()
            .copy(
                clip = MobileSegment.WireframeClip(
                    left = 0,
                    right = 0,
                    top = 0,
                    bottom = 0
                )
            )
        val fakeWireframeBounds: WireframeBounds = forge.getForgery<WireframeBounds>().apply {
            whenever(mockBoundsUtils.resolveBounds(fakeWireframe)).thenReturn(this)
        }
        val fakeParentBounds = fakeWireframeBounds.copy(
            left = fakeWireframeBounds.left - forge.aLong(min = 0, max = fakeWireframeBounds.left),
            top = fakeWireframeBounds.top - forge.aLong(min = 0, max = fakeWireframeBounds.top),
            right = fakeWireframeBounds.right +
                forge.aLong(min = 0, max = fakeWireframeBounds.right),
            bottom = fakeWireframeBounds.bottom +
                forge.aLong(min = 0, max = fakeWireframeBounds.bottom)
        )
        val fakeRandomParents: List<MobileSegment.Wireframe> = forge.aList {
            forge.getForgery<MobileSegment.Wireframe>().apply {
                whenever(mockBoundsUtils.resolveBounds(this)).thenReturn(fakeParentBounds)
            }
        }.toMutableList()
        // Then
        assertThat(testedWireframeUtils.resolveWireframeClip(fakeWireframe, fakeRandomParents))
            .isNull()
    }

    @Test
    fun `M return null W resolveWireframeClip(bigger parent, clip is null)`(
        forge: Forge
    ) {
        // Given
        val fakeWireframe: MobileSegment.Wireframe =
            forge.getForgery<MobileSegment.Wireframe>().copy(clip = null)
        val fakeWireframeBounds: WireframeBounds = forge.getForgery<WireframeBounds>().apply {
            whenever(mockBoundsUtils.resolveBounds(fakeWireframe)).thenReturn(this)
        }
        val fakeParentBounds = fakeWireframeBounds.copy(
            left = fakeWireframeBounds.left - forge.aLong(min = 0, max = fakeWireframeBounds.left),
            top = fakeWireframeBounds.top - forge.aLong(min = 0, max = fakeWireframeBounds.top),
            right = fakeWireframeBounds.right +
                forge.aLong(min = 0, max = fakeWireframeBounds.right),
            bottom = fakeWireframeBounds.bottom +
                forge.aLong(min = 0, max = fakeWireframeBounds.bottom)
        )
        val fakeRandomParents: List<MobileSegment.Wireframe> = forge.aList {
            forge.getForgery<MobileSegment.Wireframe>().apply {
                whenever(mockBoundsUtils.resolveBounds(this)).thenReturn(fakeParentBounds)
            }
        }.toMutableList()
        // Then
        assertThat(testedWireframeUtils.resolveWireframeClip(fakeWireframe, fakeRandomParents))
            .isNull()
    }

    @Test
    fun `M return null W resolveWireframeClip{no parents, clip is empty}`(forge: Forge) {
        // Given
        val wireframe: MobileSegment.Wireframe = forge.getForgery<MobileSegment.Wireframe>().copy(
            clip = MobileSegment.WireframeClip(
                left = 0,
                right = 0,
                top = 0,
                bottom = 0
            )
        )

        // Then
        assertThat(testedWireframeUtils.resolveWireframeClip(wireframe, emptyList())).isNull()
    }

    @Test
    fun `M return null W resolveWireframeClip{no parents, clip is null}`(forge: Forge) {
        // Given
        val wireframe: MobileSegment.Wireframe = forge.getForgery<MobileSegment.Wireframe>().copy(
            clip = null
        )

        // Then
        assertThat(testedWireframeUtils.resolveWireframeClip(wireframe, emptyList())).isNull()
    }

    // endregion

    // region is checkWireframeIsCovered

    @Test
    fun `M return true W checkWireframeIsCovered(){top wireframe with solid background }`(
        @Forgery fakeWireframe: MobileSegment.Wireframe,
        forge: Forge
    ) {
        // Given
        val fakeWireframeBounds: WireframeBounds = forge.getForgery<WireframeBounds>().apply {
            whenever(mockBoundsUtils.resolveBounds(fakeWireframe)).thenReturn(this)
        }
        val topWireframes = forge.opaqueWireframes()
        topWireframes.forEach {
            val topWireframeBounds: WireframeBounds = forge.getForgery<WireframeBounds>()
            whenever(mockBoundsUtils.resolveBounds(it)).thenReturn(topWireframeBounds)
            whenever(mockBoundsUtils.isCovering(topWireframeBounds, fakeWireframeBounds))
                .thenReturn(true)
        }

        // Then
        assertThat(testedWireframeUtils.checkWireframeIsCovered(fakeWireframe, topWireframes))
            .isTrue
    }

    @Test
    fun `M return false W checkWireframeIsCovered(){top wireframes with transparent back}`(
        @Forgery fakeWireframe: MobileSegment.Wireframe,
        forge: Forge
    ) {
        // Given
        val fakeWireframeBounds: WireframeBounds = forge.getForgery<WireframeBounds>().apply {
            whenever(mockBoundsUtils.resolveBounds(fakeWireframe)).thenReturn(this)
        }
        val topWireframes = forge.wireframesWithNoBackground()
        topWireframes.forEach {
            val topWireframeBounds: WireframeBounds = forge.getForgery()
            whenever(mockBoundsUtils.resolveBounds(it)).thenReturn(topWireframeBounds)
            whenever(mockBoundsUtils.isCovering(topWireframeBounds, fakeWireframeBounds))
                .thenReturn(true)
        }

        // Then
        assertThat(testedWireframeUtils.checkWireframeIsCovered(fakeWireframe, topWireframes))
            .isFalse
    }

    @Test
    fun `M return false W checkWireframeIsCovered(){top wireframes with no background color}`(
        @Forgery fakeWireframe: MobileSegment.Wireframe,
        forge: Forge
    ) {
        // Given
        val fakeWireframeBounds: WireframeBounds = forge.getForgery<WireframeBounds>().apply {
            whenever(mockBoundsUtils.resolveBounds(fakeWireframe)).thenReturn(this)
        }
        val topWireframes = forge.wireframesWithNoBackgroundColor()

        topWireframes.forEach {
            val topWireframeBounds: WireframeBounds = forge.getForgery()
            whenever(mockBoundsUtils.resolveBounds(it)).thenReturn(topWireframeBounds)
            whenever(mockBoundsUtils.isCovering(topWireframeBounds, fakeWireframeBounds))
                .thenReturn(true)
        }

        // Then
        assertThat(testedWireframeUtils.checkWireframeIsCovered(fakeWireframe, topWireframes))
            .isFalse
    }

    @Test
    fun `M return false W checkWireframeIsCovered(){top wireframes with translucent background}`(
        @Forgery fakeWireframe: MobileSegment.Wireframe,
        forge: Forge
    ) {
        // Given
        val fakeWireframeBounds: WireframeBounds = forge.getForgery<WireframeBounds>().apply {
            whenever(mockBoundsUtils.resolveBounds(fakeWireframe)).thenReturn(this)
        }
        val topWireframes = forge.wireframesWithTranslucentBackgroundColor()
        topWireframes.forEach {
            val topWireframeBounds: WireframeBounds = forge.getForgery()
            whenever(mockBoundsUtils.resolveBounds(it)).thenReturn(topWireframeBounds)
            whenever(mockBoundsUtils.isCovering(topWireframeBounds, fakeWireframeBounds))
                .thenReturn(true)
        }

        // Then
        assertThat(testedWireframeUtils.checkWireframeIsCovered(fakeWireframe, topWireframes))
            .isFalse
    }

    @Test
    fun `M return true W checkWireframeIsCovered(){top Placeholder wireframe, no shapeStyle}`(
        @Forgery fakeWireframe: MobileSegment.Wireframe,
        forge: Forge
    ) {
        // Given
        val fakeWireframeBounds: WireframeBounds = forge.getForgery<WireframeBounds>().apply {
            whenever(mockBoundsUtils.resolveBounds(fakeWireframe)).thenReturn(this)
        }
        val topWireframes = forge.aList {
            forge.getForgery<MobileSegment.Wireframe.PlaceholderWireframe>()
        }.map {
            val topWireframeBounds: WireframeBounds = forge.getForgery()
            it.copy(shapeStyle = null).apply {
                whenever(mockBoundsUtils.resolveBounds(this)).thenReturn(topWireframeBounds)
                whenever(mockBoundsUtils.isCovering(topWireframeBounds, fakeWireframeBounds))
                    .thenReturn(true)
            }
        }

        // Then
        assertThat(testedWireframeUtils.checkWireframeIsCovered(fakeWireframe, topWireframes))
            .isTrue
    }

    @Test
    fun `M return true W checkWireframeIsCovered(){top WebView wireframe, no shapeStyle}`(
        @Forgery fakeWireframe: MobileSegment.Wireframe,
        forge: Forge
    ) {
        // Given
        val fakeWireframeBounds: WireframeBounds = forge.getForgery<WireframeBounds>().apply {
            whenever(mockBoundsUtils.resolveBounds(fakeWireframe)).thenReturn(this)
        }
        val topWireframes = forge.aList {
            forge.getForgery<MobileSegment.Wireframe.WebviewWireframe>()
        }.map {
            val topWireframeBounds: WireframeBounds = forge.getForgery()
            it.copy(shapeStyle = null).apply {
                whenever(mockBoundsUtils.resolveBounds(this)).thenReturn(topWireframeBounds)
                whenever(mockBoundsUtils.isCovering(topWireframeBounds, fakeWireframeBounds))
                    .thenReturn(true)
            }
        }

        // Then
        assertThat(testedWireframeUtils.checkWireframeIsCovered(fakeWireframe, topWireframes))
            .isTrue
    }

    @Test
    fun `M return true W checkWireframeIsCovered(){top WebView wireframe, transparent shapeStyle}`(
        @Forgery fakeWireframe: MobileSegment.Wireframe,
        forge: Forge
    ) {
        // Given
        val fakeWireframeBounds: WireframeBounds = forge.getForgery<WireframeBounds>().apply {
            whenever(mockBoundsUtils.resolveBounds(fakeWireframe)).thenReturn(this)
        }
        val topWireframes = forge.aList {
            forge.getForgery<MobileSegment.Wireframe.WebviewWireframe>()
        }.map {
            val topWireframeBounds: WireframeBounds = forge.getForgery()
            val shapeStyle = forge.forgeNonTransparentShapeStyle().copy(opacity = 0)
            it.copy(shapeStyle = shapeStyle).apply {
                whenever(mockBoundsUtils.resolveBounds(this)).thenReturn(topWireframeBounds)
                whenever(mockBoundsUtils.isCovering(topWireframeBounds, fakeWireframeBounds))
                    .thenReturn(true)
            }
        }

        // Then
        assertThat(testedWireframeUtils.checkWireframeIsCovered(fakeWireframe, topWireframes))
            .isTrue
    }

    @Test
    fun `M return false W checkWireframeIsCovered { top empty ImageWireframe }`(
        @Forgery fakeWireframe: MobileSegment.Wireframe.ImageWireframe,
        forge: Forge
    ) {
        // Given
        val fakeWireframeBounds: WireframeBounds = forge.getForgery<WireframeBounds>().apply {
            whenever(mockBoundsUtils.resolveBounds(fakeWireframe)).thenReturn(this)
        }
        val topWireframes = forge.aList {
            forge.getForgery<MobileSegment.Wireframe.ImageWireframe>()
        }.map {
            val topWireframeBounds: WireframeBounds = forge.getForgery()
            it.copy(shapeStyle = null).apply {
                whenever(mockBoundsUtils.resolveBounds(this))
                    .thenReturn(topWireframeBounds)
                whenever(mockBoundsUtils.isCovering(topWireframeBounds, fakeWireframeBounds))
                    .thenReturn(true)
            }
        }

        // Then
        assertThat(testedWireframeUtils.checkWireframeIsCovered(fakeWireframe, topWireframes))
            .isFalse
    }

    @Test
    fun `M return false W checkWireframeIsCovered { top base64 ImageWireframe, no ShapeStyle}`(
        @Forgery fakeWireframe: MobileSegment.Wireframe.ImageWireframe,
        forge: Forge
    ) {
        // Given
        val fakeWireframeBounds: WireframeBounds = forge.getForgery<WireframeBounds>().apply {
            whenever(mockBoundsUtils.resolveBounds(fakeWireframe)).thenReturn(this)
        }
        val topWireframes = forge.aList {
            forge.getForgery<MobileSegment.Wireframe.ImageWireframe>()
                .copy(shapeStyle = null, base64 = forge.anAlphabeticalString())
        }
        topWireframes.forEach {
            val topWireframeBounds: WireframeBounds = forge.getForgery()
            whenever(mockBoundsUtils.resolveBounds(it))
                .thenReturn(topWireframeBounds)
            whenever(mockBoundsUtils.isCovering(topWireframeBounds, fakeWireframeBounds))
                .thenReturn(true)
        }

        // Then
        assertThat(testedWireframeUtils.checkWireframeIsCovered(fakeWireframe, topWireframes))
            .isFalse
    }

    // endregion

    // region checkWireframesIsValid

    @Test
    fun `M return false W checkWireframeIsValid(){ wireframe width is 0 }`(
        @Forgery fakeWireframe: MobileSegment.Wireframe,
        forge: Forge
    ) {
        // Given
        forge.getForgery<WireframeBounds>().copy(width = 0).apply {
            whenever(mockBoundsUtils.resolveBounds(fakeWireframe)).thenReturn(this)
        }

        // Then
        assertThat(testedWireframeUtils.checkWireframeIsValid(fakeWireframe)).isFalse
    }

    @Test
    fun `M return false W checkWireframeIsValid(){ wireframe height is 0 }`(
        @Forgery fakeWireframe: MobileSegment.Wireframe,
        forge: Forge
    ) {
        // Given
        forge.getForgery<WireframeBounds>().copy(height = 0).apply {
            whenever(mockBoundsUtils.resolveBounds(fakeWireframe)).thenReturn(this)
        }

        // Then
        assertThat(testedWireframeUtils.checkWireframeIsValid(fakeWireframe)).isFalse
    }

    @Test
    fun `M return false W checkWireframeIsValid(){ shape wireframe with no border and background }`(
        forge: Forge
    ) {
        // Given
        val fakeWireframe = forge.getForgery<MobileSegment.Wireframe.ShapeWireframe>()
            .copy(shapeStyle = null, border = null)
        forge.getForgery<WireframeBounds>().copy(
            width = forge.aPositiveLong(true),
            height = forge.aPositiveLong(true)
        ).apply {
            whenever(mockBoundsUtils.resolveBounds(fakeWireframe)).thenReturn(this)
        }

        // Then
        assertThat(testedWireframeUtils.checkWireframeIsValid(fakeWireframe)).isFalse
    }

    @Test
    fun `M return true W checkWireframeIsValid(){ shape wireframe with no border }`(
        forge: Forge
    ) {
        // Given
        val fakeWireframe = forge.getForgery<MobileSegment.Wireframe.ShapeWireframe>()
            .copy(shapeStyle = forge.getForgery(), border = null)
        forge.getForgery<WireframeBounds>().copy(
            width = forge.aPositiveLong(true),
            height = forge.aPositiveLong(true)
        ).apply {
            whenever(mockBoundsUtils.resolveBounds(fakeWireframe)).thenReturn(this)
        }

        // Then
        assertThat(testedWireframeUtils.checkWireframeIsValid(fakeWireframe)).isTrue
    }

    @Test
    fun `M return true W checkWireframeIsValid(){ shape wireframe with no background }`(
        forge: Forge
    ) {
        // Given
        val fakeWireframe = forge.getForgery<MobileSegment.Wireframe.ShapeWireframe>()
            .copy(shapeStyle = null, border = forge.getForgery())
        forge.getForgery<WireframeBounds>().copy(
            width = forge.aPositiveLong(true),
            height = forge.aPositiveLong(true)
        ).apply {
            whenever(mockBoundsUtils.resolveBounds(fakeWireframe)).thenReturn(this)
        }

        // Then
        assertThat(testedWireframeUtils.checkWireframeIsValid(fakeWireframe)).isTrue
    }

    @Test
    fun `M return true W checkWireframeIsValid(){ text wireframe with no border and background }`(
        forge: Forge
    ) {
        // Given
        val fakeWireframe = forge.getForgery<MobileSegment.Wireframe.TextWireframe>()
            .copy(shapeStyle = null, border = null)
        forge.getForgery<WireframeBounds>().copy(
            width = forge.aPositiveLong(true),
            height = forge.aPositiveLong(true)
        ).apply {
            whenever(mockBoundsUtils.resolveBounds(fakeWireframe)).thenReturn(this)
        }

        // Then
        assertThat(testedWireframeUtils.checkWireframeIsValid(fakeWireframe)).isTrue
    }

    // endregion

    // region Internal

    private fun MobileSegment.Wireframe.clip(): MobileSegment.WireframeClip? {
        return when (this) {
            is MobileSegment.Wireframe.ShapeWireframe -> clip?.normalized()
            is MobileSegment.Wireframe.TextWireframe -> clip?.normalized()
            is MobileSegment.Wireframe.ImageWireframe -> clip?.normalized()
            is MobileSegment.Wireframe.PlaceholderWireframe -> clip?.normalized()
            is MobileSegment.Wireframe.WebviewWireframe -> clip?.normalized()
        }
    }

    private fun Forge.forgeNonTransparentShapeStyle(): MobileSegment.ShapeStyle {
        return MobileSegment.ShapeStyle(
            backgroundColor = aStringMatching("#[0-9A-Fa-f]{6}[fF]{2}"),
            opacity = 1f,
            cornerRadius = aPositiveLong()
        )
    }

    private fun Long?.toLong(): Long {
        return this ?: 0L
    }

    private fun MobileSegment.WireframeClip.normalized(): MobileSegment.WireframeClip {
        return MobileSegment.WireframeClip(
            top = top.toLong(),
            bottom = bottom.toLong(),
            left = left.toLong(),
            right = right.toLong()
        )
    }

    private fun MobileSegment.Wireframe.copy(
        shapeStyle: MobileSegment.ShapeStyle?
    ):
        MobileSegment.Wireframe {
        return when (this) {
            is MobileSegment.Wireframe.ShapeWireframe -> copy(
                shapeStyle = shapeStyle
            )

            is MobileSegment.Wireframe.TextWireframe -> copy(
                shapeStyle = shapeStyle
            )

            is MobileSegment.Wireframe.ImageWireframe -> copy(
                shapeStyle = shapeStyle
            )

            is MobileSegment.Wireframe.PlaceholderWireframe -> this
            is MobileSegment.Wireframe.WebviewWireframe -> this
        }
    }

    private fun Forge.opaqueWireframes(): List<MobileSegment.Wireframe> {
        return listOf(
            getForgery<MobileSegment.Wireframe.ShapeWireframe>()
                .copy(shapeStyle = getForgery(), border = getForgery()),
            getForgery<MobileSegment.Wireframe.TextWireframe>()
                .copy(shapeStyle = getForgery(), border = getForgery()),
            getForgery<MobileSegment.Wireframe.ImageWireframe>().copy(
                shapeStyle = getForgery(),
                border = getForgery()
            ),
            getForgery<MobileSegment.Wireframe.PlaceholderWireframe>(),
            getForgery<MobileSegment.Wireframe.WebviewWireframe>()
        )
    }

    private fun Forge.wireframesWithNoBackground(): List<MobileSegment.Wireframe> {
        return listOf(
            getForgery<MobileSegment.Wireframe.ShapeWireframe>()
                .copy(
                    shapeStyle = forgeNonTransparentShapeStyle()
                        .copy(opacity = aFloat(min = 0f, max = 1f))
                ),
            getForgery<MobileSegment.Wireframe.TextWireframe>()
                .copy(
                    shapeStyle = forgeNonTransparentShapeStyle()
                        .copy(opacity = aFloat(min = 0f, max = 1f))
                ),
            getForgery<MobileSegment.Wireframe.ImageWireframe>()
                .copy(
                    shapeStyle = forgeNonTransparentShapeStyle()
                        .copy(opacity = aFloat(min = 0f, max = 1f)),
                    base64 = null
                )
        )
    }

    private fun Forge.wireframesWithNoBackgroundColor(): List<MobileSegment.Wireframe> {
        return listOf(
            getForgery<MobileSegment.Wireframe.ShapeWireframe>()
                .copy(
                    shapeStyle = forgeNonTransparentShapeStyle()
                        .copy(backgroundColor = null)
                ),
            getForgery<MobileSegment.Wireframe.TextWireframe>()
                .copy(
                    shapeStyle = forgeNonTransparentShapeStyle()
                        .copy(backgroundColor = null)
                ),
            getForgery<MobileSegment.Wireframe.ImageWireframe>()
                .copy(
                    shapeStyle = forgeNonTransparentShapeStyle()
                        .copy(backgroundColor = null),
                    base64 = null
                )
        )
    }

    private fun Forge.wireframesWithTranslucentBackgroundColor():
        List<MobileSegment.Wireframe> {
        return listOf(
            getForgery<MobileSegment.Wireframe.ShapeWireframe>()
                .copy(
                    shapeStyle = forgeNonTransparentShapeStyle()
                        .copy(backgroundColor = aStringMatching("#[0-9A-Fa-f]{6}[a-eA-E]{2}"))
                ),
            getForgery<MobileSegment.Wireframe.TextWireframe>()
                .copy(
                    shapeStyle = forgeNonTransparentShapeStyle()
                        .copy(backgroundColor = aStringMatching("#[0-9A-Fa-f]{6}[a-eA-E]{2}"))
                )
        )
    }

    // endregion
}
