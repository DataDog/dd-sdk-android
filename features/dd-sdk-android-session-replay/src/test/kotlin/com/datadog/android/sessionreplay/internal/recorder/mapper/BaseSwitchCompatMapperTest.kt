/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.content.res.Resources
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.Drawable.ConstantState
import androidx.appcompat.widget.SwitchCompat
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
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
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@ForgeConfiguration(value = ForgeConfigurator::class, seed = 0x27e4b032201e5L)
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

    @Mock
    lateinit var mockConstantState: ConstantState

    @Mock
    lateinit var mockCloneDrawable: Drawable

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

    lateinit var fakeThumbBounds: Rect

    lateinit var fakeTrackBounds: Rect

    var expectedThumbLeft: Long = 0
    var expectedThumbTop: Long = 0
    var expectedTrackLeft: Long = 0
    var expectedTrackTop: Long = 0

    private var normalizedThumbHeight: Long = 0
    protected var normalizedThumbWidth: Long = 0
    private var normalizedTrackWidth: Long = 0
    protected var normalizedTrackHeight: Long = 0
    protected var normalizedThumbLeftPadding: Long = 0
    protected var normalizedThumbRightPadding: Long = 0

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeThumbBounds = Rect(forge.aSmallInt(), forge.aSmallInt(), forge.aSmallInt(), forge.aSmallInt())
        fakeTrackBounds = Rect(forge.aSmallInt(), forge.aSmallInt(), forge.aSmallInt(), forge.aSmallInt())
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
        whenever(mockCloneDrawable.bounds).thenReturn(fakeTrackBounds)
        whenever(mockConstantState.newDrawable(anyVararg(Resources::class))).thenReturn(mockCloneDrawable)
        whenever(mockThumbDrawable.intrinsicHeight).thenReturn(fakeThumbHeight)
        whenever(mockThumbDrawable.intrinsicWidth).thenReturn(fakeThumbWidth)
        whenever(mockTrackDrawable.intrinsicHeight).thenReturn(fakeTrackHeight)
        whenever(mockTrackDrawable.intrinsicWidth).thenReturn(fakeTrackWidth)
        whenever(mockTrackDrawable.constantState).thenReturn(mockConstantState)
        whenever(mockTrackDrawable.bounds).thenReturn(fakeTrackBounds)
        whenever(mockThumbDrawable.bounds).thenReturn(fakeThumbBounds)
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
        whenever(mockTextWireframeMapper.map(eq(mockSwitch), any(), any(), eq(mockInternalLogger)))
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
        val pixelsDensity = fakeMappingContext.systemInformation.screenDensity
        expectedThumbLeft = (
            fakeViewGlobalBounds.x * pixelsDensity +
                fakeThumbBounds.left
            ).toLong().densityNormalized(density = pixelsDensity)
        expectedThumbTop = (
            fakeViewGlobalBounds.y * pixelsDensity +
                fakeThumbBounds.top
            ).toLong().densityNormalized(density = pixelsDensity)
        expectedTrackLeft = (
            fakeViewGlobalBounds.x * pixelsDensity +
                fakeTrackBounds.left
            ).toLong().densityNormalized(density = pixelsDensity)
        expectedTrackTop = (
            fakeViewGlobalBounds.y * pixelsDensity +
                fakeTrackBounds.top
            ).toLong().densityNormalized(density = pixelsDensity)
    }

    internal abstract fun setupTestedMapper(): SwitchCompatMapper

    @Test
    fun `M resolve the switch as wireframes W map() { no thumbDrawable }`(forge: Forge) {
        // Given
        whenever(mockSwitch.thumbDrawable).thenReturn(null)
        whenever(mockSwitch.isChecked).thenReturn(forge.aBool())
        val allowMappingContext = fakeMappingContext.copy(privacy = SessionReplayPrivacy.ALLOW)

        // When
        val resolvedWireframes = testedSwitchCompatMapper.map(
            mockSwitch,
            allowMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        assertThat(resolvedWireframes).isEqualTo(fakeTextWireframes)
    }

    @RepeatedTest(10)
    fun `M resolve the switch as wireframes W map() { no trackDrawable }`(forge: Forge) {
        // Given
        whenever(mockSwitch.trackDrawable).thenReturn(null)
        whenever(mockSwitch.isChecked).thenReturn(forge.aBool())
        val allowMappingContext = fakeMappingContext.copy(privacy = SessionReplayPrivacy.ALLOW)

        // When
        val resolvedWireframes = testedSwitchCompatMapper.map(
            mockSwitch,
            allowMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        assertThat(resolvedWireframes).isEqualTo(fakeTextWireframes)
    }
}
