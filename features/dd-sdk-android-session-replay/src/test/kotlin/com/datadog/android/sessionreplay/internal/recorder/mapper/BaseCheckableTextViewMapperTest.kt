/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableContainer
import android.os.Build
import android.widget.Checkable
import android.widget.TextView
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.mapper.CheckableTextViewMapper.Companion.CHECK_BOX_CHECKED_DRAWABLE_INDEX
import com.datadog.android.sessionreplay.internal.recorder.mapper.CheckableTextViewMapper.Companion.CHECK_BOX_NOT_CHECKED_DRAWABLE_INDEX
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.mapper.TextViewMapper
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.OPAQUE_ALPHA_VALUE
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
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
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
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

    @Mock
    lateinit var mockCheckedConstantState: DrawableContainer.DrawableContainerState

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
    lateinit var mockClonedDrawable: Drawable

    @IntForgery
    var mockCloneDrawableIntrinsicHeight: Int = 0

    @IntForgery
    var mockCloneDrawableIntrinsicWidth: Int = 0

    @Forgery
    lateinit var fakeImagePrivacy: ImagePrivacy

    @BeforeEach
    fun `set up`() {
        mockClonedDrawable = mock {
            whenever(it.intrinsicHeight) doReturn mockCloneDrawableIntrinsicHeight
            whenever(it.intrinsicWidth) doReturn mockCloneDrawableIntrinsicWidth
        }
        mockCheckedConstantState = mock {
            whenever(it.newDrawable(mockResources)) doReturn mockClonedDrawable
        }
        mockCheckedDrawable = mock {
            whenever(it.constantState) doReturn mockCheckedConstantState
        }
        mockNotCheckedDrawable = mock {
            whenever(it.constantState) doReturn mockCheckedConstantState
        }
        mockConstantState = mock {
            whenever(it.getChild(CHECK_BOX_CHECKED_DRAWABLE_INDEX)) doReturn mockCheckedDrawable
            whenever(it.getChild(CHECK_BOX_NOT_CHECKED_DRAWABLE_INDEX)) doReturn mockNotCheckedDrawable
        }
        mockButtonDrawable = mock {
            whenever(it.constantState) doReturn mockConstantState
        }

        mockCheckableTextView = mockCheckableTextView()
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

    internal open fun expectedCheckedShapeStyle(checkBoxColor: String): MobileSegment.ShapeStyle? {
        return if (fakeMappingContext.privacy == SessionReplayPrivacy.ALLOW) {
            MobileSegment.ShapeStyle(
                backgroundColor = checkBoxColor,
                opacity = mockCheckableTextView.alpha
            )
        } else {
            null
        }
    }

    // region Unit Tests

    @TestTargetApi(Build.VERSION_CODES.M)
    @Test
    fun `M create ImageWireFrame W map() { checked, above M }`() {
        // Given
        val allowedMappingContext =
            fakeMappingContext.copy(privacy = SessionReplayPrivacy.ALLOW, imagePrivacy = ImagePrivacy.MASK_LARGE_ONLY)
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
        verify(fakeMappingContext.imageWireframeHelper).createImageWireframe(
            view = eq(mockCheckableTextView),
            imagePrivacy = eq(ImagePrivacy.MASK_LARGE_ONLY),
            currentWireframeIndex = anyInt(),
            x = eq(expectedX),
            y = eq(expectedY),
            width = eq(mockCloneDrawableIntrinsicWidth),
            height = eq(mockCloneDrawableIntrinsicHeight),
            usePIIPlaceholder = anyBoolean(),
            drawable = eq(mockClonedDrawable),
            asyncJobStatusCallback = eq(mockAsyncJobStatusCallback),
            clipping = eq(MobileSegment.WireframeClip()),
            shapeStyle = isNull(),
            border = isNull(),
            prefix = anyString()
        )
    }

    @TestTargetApi(Build.VERSION_CODES.M)
    @Test
    fun `M create ImageWireFrame W map() { not checked, above M }`() {
        // Given
        val allowedMappingContext = fakeMappingContext.copy(
            privacy = SessionReplayPrivacy.ALLOW,
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
        verify(fakeMappingContext.imageWireframeHelper).createImageWireframe(
            view = eq(mockCheckableTextView),
            imagePrivacy = eq(ImagePrivacy.MASK_LARGE_ONLY),
            currentWireframeIndex = anyInt(),
            x = eq(expectedX),
            y = eq(expectedY),
            width = eq(mockCloneDrawableIntrinsicWidth),
            height = eq(mockCloneDrawableIntrinsicHeight),
            usePIIPlaceholder = anyBoolean(),
            drawable = eq(mockClonedDrawable),
            asyncJobStatusCallback = eq(mockAsyncJobStatusCallback),
            clipping = eq(MobileSegment.WireframeClip()),
            shapeStyle = isNull(),
            border = isNull(),
            prefix = anyString()
        )
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
}
