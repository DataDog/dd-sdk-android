/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableContainer
import android.widget.Checkable
import android.widget.TextView
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.mapper.CheckableTextViewMapper.Companion.CHECK_BOX_CHECKED_DRAWABLE_INDEX
import com.datadog.android.sessionreplay.internal.recorder.mapper.CheckableTextViewMapper.Companion.CHECK_BOX_NOT_CHECKED_DRAWABLE_INDEX
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.mapper.TextViewMapper
import com.datadog.android.sessionreplay.recorder.resources.DrawableCopier
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.OPAQUE_ALPHA_VALUE
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.KArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal abstract class BaseCheckableTextViewMapperTest<T> :
    LegacyBaseWireframeMapperTest() where T : TextView, T : Checkable {

    private lateinit var testedCheckableTextViewMapper: CheckableTextViewMapper<T>

    @Mock
    lateinit var mockTextWireframeMapper: TextViewMapper<T>

    @Forgery
    lateinit var fakeTextWireframes: List<MobileSegment.Wireframe.TextWireframe>

    @LongForgery
    var fakeGeneratedIdentifier: Long = 0L

    @Forgery
    lateinit var fakeViewGlobalBounds: GlobalBounds

    @Mock
    lateinit var mockCheckableTextView: T

    @Mock
    lateinit var mockResources: Resources

    @Mock
    lateinit var mockButtonDrawable: Drawable

    @Mock
    lateinit var mockConstantState: DrawableContainer.DrawableContainerState

    @IntForgery(min = 0, max = 0xffffff)
    var fakeCurrentTextColor: Int = 0

    @StringForgery(regex = "#[0-9A-F]{8}")
    lateinit var fakeCurrentTextColorString: String

    @FloatForgery(min = 1f, max = 100f)
    var fakeTextSize: Float = 1f

    @IntForgery(min = CheckableCompoundButtonMapper.DEFAULT_CHECKABLE_HEIGHT_IN_DP.toInt(), max = 100)
    var fakeIntrinsicDrawableHeight = 1

    @Mock
    lateinit var mockCheckedDrawable: Drawable

    @Mock
    lateinit var mockNotCheckedDrawable: Drawable

    @Mock
    lateinit var mockDrawableCopier: DrawableCopier

    @Mock
    lateinit var mockCheckedConstantState: Drawable.ConstantState

    @Mock
    lateinit var mockCopiedDrawable: Drawable

    @Mock
    lateinit var mockInnerDrawable: Drawable

    @Mock
    lateinit var mockTintList: ColorStateList

    private lateinit var fakeDrawableState: IntArray

    @IntForgery
    var fakeDrawableIntrinsicHeight: Int = 0

    @IntForgery
    var fakeDrawableIntrinsicWidth: Int = 0

    @Forgery
    lateinit var fakeImagePrivacy: ImagePrivacy

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeDrawableState = IntArray(forge.anInt(min = 1, max = 8)) { forge.anInt() }
        mockCheckedDrawable = mock {
            whenever(it.intrinsicHeight) doReturn fakeDrawableIntrinsicHeight
            whenever(it.intrinsicWidth) doReturn fakeDrawableIntrinsicWidth
        }
        mockNotCheckedDrawable = mock {
            whenever(it.intrinsicHeight) doReturn fakeDrawableIntrinsicHeight
            whenever(it.intrinsicWidth) doReturn fakeDrawableIntrinsicWidth
        }
        mockConstantState = mock {
            whenever(it.getChild(CHECK_BOX_CHECKED_DRAWABLE_INDEX)) doReturn mockCheckedDrawable
            whenever(it.getChild(CHECK_BOX_NOT_CHECKED_DRAWABLE_INDEX)) doReturn mockNotCheckedDrawable
        }
        mockButtonDrawable = mock {
            whenever(it.constantState) doReturn mockConstantState
        }

        mockCheckableTextView = mockCheckableTextView()
        whenever(mockCheckableTextView.drawableState) doReturn fakeDrawableState
        whenever(
            mockViewIdentifierResolver.resolveChildUniqueIdentifier(
                mockCheckableTextView,
                CheckableTextViewMapper.CHECKABLE_KEY_NAME
            )
        ).thenReturn(fakeGeneratedIdentifier)

        whenever(
            mockTextWireframeMapper.map(
                eq(mockCheckableTextView),
                eq(fakeMappingContext),
                any(),
                eq(mockInternalLogger)
            )
        )
            .thenReturn(fakeTextWireframes)

        whenever(
            mockViewBoundsResolver.resolveViewGlobalBounds(
                mockCheckableTextView,
                fakeMappingContext.systemInformation.screenDensity
            )
        ).thenReturn(fakeViewGlobalBounds)

        whenever(
            mockColorStringFormatter.formatColorAndAlphaAsHexString(fakeCurrentTextColor, OPAQUE_ALPHA_VALUE)
        ) doReturn fakeCurrentTextColorString

        testedCheckableTextViewMapper = setupTestedMapper()
    }

    internal abstract fun setupTestedMapper(): CheckableTextViewMapper<T>

    internal abstract fun mockCheckableTextView(): T

    internal abstract fun stubCheckableTint(view: T, tintList: ColorStateList?)

    internal open fun expectedCheckedShapeStyle(checkBoxColor: String): MobileSegment.ShapeStyle? {
        return if (fakeMappingContext.textAndInputPrivacy == TextAndInputPrivacy.MASK_SENSITIVE_INPUTS) {
            MobileSegment.ShapeStyle(
                backgroundColor = checkBoxColor,
                opacity = mockCheckableTextView.alpha
            )
        } else {
            null
        }
    }

    // region Unit Tests

    @Test
    fun `M create ImageWireFrame W map() { checked }`() {
        // Given
        val allowedMappingContext =
            fakeMappingContext.copy(
                textAndInputPrivacy = TextAndInputPrivacy.MASK_SENSITIVE_INPUTS,
                imagePrivacy = ImagePrivacy.MASK_LARGE_ONLY
            )
        whenever(mockButtonDrawable.intrinsicHeight).thenReturn(fakeIntrinsicDrawableHeight)
        whenever(mockCheckableTextView.isChecked).thenReturn(true)

        // When
        testedCheckableTextViewMapper.map(
            mockCheckableTextView,
            allowedMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        val expectedX = testedCheckableTextViewMapper
            .resolveCheckableBounds(mockCheckableTextView, fakeMappingContext.systemInformation.screenDensity).x
        val expectedY = testedCheckableTextViewMapper
            .resolveCheckableBounds(mockCheckableTextView, fakeMappingContext.systemInformation.screenDensity).y
        // Then
        verify(fakeMappingContext.imageWireframeHelper).createImageWireframeByDrawable(
            view = eq(mockCheckableTextView),
            imagePrivacy = eq(ImagePrivacy.MASK_NONE),
            currentWireframeIndex = anyInt(),
            x = eq(expectedX),
            y = eq(expectedY),
            width = eq(fakeDrawableIntrinsicWidth),
            height = eq(fakeDrawableIntrinsicHeight),
            usePIIPlaceholder = anyBoolean(),
            drawable = any(),
            drawableCopier = any(),
            asyncJobStatusCallback = eq(mockAsyncJobStatusCallback),
            clipping = eq(MobileSegment.WireframeClip()),
            shapeStyle = isNull(),
            border = isNull(),
            prefix = anyString(),
            customResourceIdCacheKey = anyOrNull()
        )
    }

    @Test
    fun `M create ImageWireFrame W map() { not checked }`() {
        // Given
        val allowedMappingContext = fakeMappingContext.copy(
            textAndInputPrivacy = TextAndInputPrivacy.MASK_SENSITIVE_INPUTS,
            imagePrivacy = ImagePrivacy.MASK_LARGE_ONLY
        )
        whenever(mockButtonDrawable.intrinsicHeight).thenReturn(fakeIntrinsicDrawableHeight)
        whenever(mockCheckableTextView.isChecked).thenReturn(false)

        // When
        testedCheckableTextViewMapper.map(
            mockCheckableTextView,
            allowedMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        val expectedX = testedCheckableTextViewMapper
            .resolveCheckableBounds(mockCheckableTextView, fakeMappingContext.systemInformation.screenDensity).x
        val expectedY = testedCheckableTextViewMapper
            .resolveCheckableBounds(mockCheckableTextView, fakeMappingContext.systemInformation.screenDensity).y
        // Then
        verify(fakeMappingContext.imageWireframeHelper).createImageWireframeByDrawable(
            view = eq(mockCheckableTextView),
            imagePrivacy = eq(ImagePrivacy.MASK_NONE),
            currentWireframeIndex = anyInt(),
            x = eq(expectedX),
            y = eq(expectedY),
            width = eq(fakeDrawableIntrinsicWidth),
            height = eq(fakeDrawableIntrinsicHeight),
            usePIIPlaceholder = anyBoolean(),
            drawable = eq(mockNotCheckedDrawable),
            drawableCopier = any(),
            asyncJobStatusCallback = eq(mockAsyncJobStatusCallback),
            clipping = eq(MobileSegment.WireframeClip()),
            shapeStyle = isNull(),
            border = isNull(),
            prefix = anyString(),
            customResourceIdCacheKey = anyOrNull()
        )
    }

    @Test
    fun `M copy its drawable with the state and tint of mapping time W map() + copy() { view changes }`() {
        // Given
        val fakeMappedState = fakeDrawableState.copyOf()
        stubCheckableTint(mockCheckableTextView, mockTintList)
        whenever(mockCheckableTextView.isChecked).thenReturn(true)
        whenever(mockCheckedDrawable.constantState) doReturn mockCheckedConstantState
        whenever(mockCheckedConstantState.newDrawable(mockResources)) doReturn mockCopiedDrawable
        val drawableCopierCaptor = argumentCaptor<DrawableCopier>()
        testedCheckableTextViewMapper.map(
            mockCheckableTextView,
            fakeMappingContext.copy(
                textAndInputPrivacy = TextAndInputPrivacy.MASK_SENSITIVE_INPUTS,
                imagePrivacy = ImagePrivacy.MASK_LARGE_ONLY
            ),
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )
        verifyCheckableDrawableRequested(drawableCopierCaptor)
        fakeDrawableState[0] = fakeDrawableState[0] + 1
        stubCheckableTint(mockCheckableTextView, null)

        // When
        val copy = drawableCopierCaptor.firstValue.copy(mockInnerDrawable, mockResources)

        // Then
        assertThat(copy).isSameAs(mockCopiedDrawable)
        verify(mockCopiedDrawable).setState(fakeMappedState)
        verify(mockCopiedDrawable).setTintList(mockTintList)
    }

    @Test
    fun `M copy without a tint W map() + copy() { no tint declared }`() {
        // Given
        stubCheckableTint(mockCheckableTextView, null)
        whenever(mockCheckableTextView.isChecked).thenReturn(true)
        whenever(mockCheckedDrawable.constantState) doReturn mockCheckedConstantState
        whenever(mockCheckedConstantState.newDrawable(mockResources)) doReturn mockCopiedDrawable
        val drawableCopierCaptor = argumentCaptor<DrawableCopier>()
        testedCheckableTextViewMapper.map(
            mockCheckableTextView,
            fakeMappingContext.copy(
                textAndInputPrivacy = TextAndInputPrivacy.MASK_SENSITIVE_INPUTS,
                imagePrivacy = ImagePrivacy.MASK_LARGE_ONLY
            ),
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )
        verifyCheckableDrawableRequested(drawableCopierCaptor)

        // When
        drawableCopierCaptor.firstValue.copy(mockCheckedDrawable, mockResources)

        // Then
        verify(mockCopiedDrawable, never()).setTintList(any())
    }

    @Test
    fun `M ignore the checkbox W map() { unique id could not be generated }`() {
        // Given
        whenever(
            mockViewIdentifierResolver.resolveChildUniqueIdentifier(
                mockCheckableTextView,
                CheckableTextViewMapper.CHECKABLE_KEY_NAME
            )
        ).thenReturn(null)

        // When
        val resolvedWireframes = testedCheckableTextViewMapper.map(
            mockCheckableTextView,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        assertThat(resolvedWireframes).isEqualTo(fakeTextWireframes)
    }

    // endregion

    private fun verifyCheckableDrawableRequested(drawableCopierCaptor: KArgumentCaptor<DrawableCopier>) {
        verify(fakeMappingContext.imageWireframeHelper).createImageWireframeByDrawable(
            view = any(),
            imagePrivacy = any(),
            currentWireframeIndex = anyInt(),
            x = anyLong(),
            y = anyLong(),
            width = anyInt(),
            height = anyInt(),
            usePIIPlaceholder = anyBoolean(),
            drawable = any(),
            drawableCopier = drawableCopierCaptor.capture(),
            asyncJobStatusCallback = any(),
            clipping = anyOrNull(),
            shapeStyle = anyOrNull(),
            border = anyOrNull(),
            prefix = anyOrNull(),
            customResourceIdCacheKey = anyOrNull()
        )
    }
}
