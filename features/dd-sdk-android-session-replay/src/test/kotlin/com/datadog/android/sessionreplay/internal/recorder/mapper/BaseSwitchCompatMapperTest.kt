/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.graphics.Rect
import android.graphics.drawable.Drawable
import androidx.appcompat.widget.SwitchCompat
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.android.sessionreplay.internal.recorder.densityNormalized
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.mapper.TextViewMapper
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.OPAQUE_ALPHA_VALUE
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal abstract class BaseSwitchCompatMapperTest : LegacyBaseWireframeMapperTest() {

    lateinit var testedSwitchCompatMapper: SwitchCompatMapper

    @Mock
    lateinit var mockTextWireframeMapper: TextViewMapper<SwitchCompat>

    lateinit var fakeTextWireframes: List<MobileSegment.Wireframe.TextWireframe>

    @LongForgery
    var fakeThumbIdentifier: Long = 0L

    @LongForgery
    var fakeTrackIdentifier: Long = 0L

    @Forgery
    lateinit var fakeViewGlobalBounds: GlobalBounds

    lateinit var mockSwitch: SwitchCompat

    @Mock
    lateinit var mockThumbDrawable: Drawable

    @Mock
    lateinit var mockTrackDrawable: Drawable

    @IntForgery(min = 20, max = 200)
    var fakeThumbHeight: Int = 0

    @IntForgery(min = 20, max = 200)
    var fakeThumbWidth: Int = 0

    @IntForgery(min = 20, max = 200)
    var fakeTrackHeight: Int = 0

    @IntForgery(min = 20, max = 200)
    var fakeTrackWidth: Int = 0

    @IntForgery(min = 0, max = 20)
    var fakeThumbLeftPadding: Int = 0

    @IntForgery(min = 0, max = 20)
    var fakeThumbRightPadding: Int = 0

    @IntForgery(min = 0, max = 0xffffff)
    var fakeCurrentTextColor: Int = 0

    @StringForgery(regex = "#[0-9A-F]{8}")
    lateinit var fakeCurrentTextColorString: String

    private var normalizedThumbHeight: Long = 0
    protected var normalizedThumbWidth: Long = 0
    private var normalizedTrackWidth: Long = 0
    protected var normalizedTrackHeight: Long = 0
    protected var normalizedThumbLeftPadding: Long = 0
    protected var normalizedThumbRightPadding: Long = 0

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeTextWireframes = forge.aList(size = 1) { getForgery() }
        normalizedThumbHeight = fakeThumbHeight.toLong()
            .densityNormalized(fakeMappingContext.systemInformation.screenDensity)
        normalizedThumbWidth = fakeThumbWidth.toLong()
            .densityNormalized(fakeMappingContext.systemInformation.screenDensity)
        normalizedTrackWidth = fakeTrackWidth.toLong()
            .densityNormalized(fakeMappingContext.systemInformation.screenDensity)
        normalizedTrackHeight = fakeTrackHeight.toLong()
            .densityNormalized(fakeMappingContext.systemInformation.screenDensity)
        normalizedThumbLeftPadding = fakeThumbLeftPadding.toLong()
            .densityNormalized(fakeMappingContext.systemInformation.screenDensity)
        normalizedThumbRightPadding = fakeThumbRightPadding.toLong()
            .densityNormalized(fakeMappingContext.systemInformation.screenDensity)
        whenever(mockThumbDrawable.intrinsicHeight).thenReturn(fakeThumbHeight)
        whenever(mockThumbDrawable.intrinsicWidth).thenReturn(fakeThumbWidth)
        whenever(mockTrackDrawable.intrinsicHeight).thenReturn(fakeTrackHeight)
        whenever(mockTrackDrawable.intrinsicWidth).thenReturn(fakeTrackWidth)
        whenever(mockThumbDrawable.getPadding(any())).thenAnswer {
            val paddingRect = it.getArgument<Rect>(0)
            paddingRect.left = fakeThumbLeftPadding
            paddingRect.right = fakeThumbRightPadding
            true
        }
        mockSwitch = mock {
            whenever(it.currentTextColor).thenReturn(fakeCurrentTextColor)
            whenever(it.trackDrawable).thenReturn(mockTrackDrawable)
            whenever(it.thumbDrawable).thenReturn(mockThumbDrawable)
        }
        whenever(
            mockViewIdentifierResolver.resolveChildUniqueIdentifier(
                mockSwitch,
                SwitchCompatMapper.TRACK_KEY_NAME
            )
        ).thenReturn(fakeTrackIdentifier)
        whenever(
            mockViewIdentifierResolver.resolveChildUniqueIdentifier(
                mockSwitch,
                SwitchCompatMapper.THUMB_KEY_NAME
            )
        ).thenReturn(fakeThumbIdentifier)
        whenever(mockTextWireframeMapper.map(eq(mockSwitch), eq(fakeMappingContext), any(), eq(mockInternalLogger)))
            .thenReturn(fakeTextWireframes)
        whenever(
            mockViewBoundsResolver.resolveViewGlobalBounds(
                mockSwitch,
                fakeMappingContext.systemInformation.screenDensity
            )
        ).thenReturn(fakeViewGlobalBounds)

        whenever(
            mockColorStringFormatter.formatColorAndAlphaAsHexString(fakeCurrentTextColor, OPAQUE_ALPHA_VALUE)
        ).thenReturn(fakeCurrentTextColorString)

        testedSwitchCompatMapper = setupTestedMapper()
    }

    internal abstract fun setupTestedMapper(): SwitchCompatMapper

    @Test
    fun `M resolve the switch as wireframes W map() { no thumbDrawable }`(forge: Forge) {
        // Given
        whenever(mockSwitch.thumbDrawable).thenReturn(null)
        whenever(mockSwitch.isChecked).thenReturn(forge.aBool())

        // When
        val resolvedWireframes = testedSwitchCompatMapper.map(
            mockSwitch,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        assertThat(resolvedWireframes).isEqualTo(fakeTextWireframes)
    }

    @Test
    fun `M resolve the switch as wireframes W map() { no trackDrawable }`(forge: Forge) {
        // Given
        whenever(mockSwitch.trackDrawable).thenReturn(null)
        whenever(mockSwitch.isChecked).thenReturn(forge.aBool())

        // When
        val resolvedWireframes = testedSwitchCompatMapper.map(
            mockSwitch,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        assertThat(resolvedWireframes).isEqualTo(fakeTextWireframes)
    }

    @RepeatedTest(8)
    fun `M resolve the switch as wireframes W map() { can't generate id for trackWireframe }`(
        forge: Forge
    ) {
        // Given
        whenever(
            mockViewIdentifierResolver.resolveChildUniqueIdentifier(
                mockSwitch,
                SwitchCompatMapper.TRACK_KEY_NAME
            )
        ).thenReturn(null)
        val isChecked = forge.aBool()
        whenever(mockSwitch.isChecked).thenReturn(isChecked)
        val expectedThumbWidth =
            normalizedThumbWidth - normalizedThumbRightPadding - normalizedThumbLeftPadding
        val expectedTrackWidth = expectedThumbWidth * 2
        val expectedX = if (isChecked) {
            fakeViewGlobalBounds.x + fakeViewGlobalBounds.width - expectedThumbWidth
        } else {
            fakeViewGlobalBounds.x + fakeViewGlobalBounds.width - expectedTrackWidth
        }
        val expectedThumbWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = fakeThumbIdentifier,
            x = expectedX,
            y = fakeViewGlobalBounds.y + (fakeViewGlobalBounds.height - expectedThumbWidth) / 2,
            width = expectedThumbWidth,
            height = expectedThumbWidth,
            border = null,
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = fakeCurrentTextColorString,
                mockSwitch.alpha,
                cornerRadius = SwitchCompatMapper.THUMB_CORNER_RADIUS
            )
        )

        // When
        val resolvedWireframes = testedSwitchCompatMapper.map(
            mockSwitch,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        if (fakeMappingContext.privacy == SessionReplayPrivacy.ALLOW) {
            assertThat(resolvedWireframes)
                .isEqualTo(fakeTextWireframes + expectedThumbWireframe)
        } else {
            assertThat(resolvedWireframes)
                .isEqualTo(fakeTextWireframes)
        }
    }
}
