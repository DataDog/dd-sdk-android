/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder.mapper

import android.content.res.Configuration
import android.content.res.Resources
import android.view.View
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.MappingContext
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.assertj.core.api.Assertions.withinPercentage
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever

internal abstract class AbstractWireframeMapperTest<V : View, WM : WireframeMapper<V>> {

    lateinit var testedWireframeMapper: WM

    lateinit var mockMappedView: V

    @Mock
    lateinit var mockViewIdentifierResolver: ViewIdentifierResolver

    @Mock
    lateinit var mockColorStringFormatter: ColorStringFormatter

    @Mock
    lateinit var mockViewBoundsResolver: ViewBoundsResolver

    @Mock
    lateinit var mockDrawableToColorMapper: DrawableToColorMapper

    @Mock
    lateinit var mockAsyncJobStatusCallback: AsyncJobStatusCallback

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Forgery
    lateinit var fakeMappingContext: MappingContext

    @Forgery
    lateinit var fakeViewGlobalBounds: GlobalBounds

    @Forgery
    lateinit var fakeViewPaddedBounds: GlobalBounds

    @Mock
    lateinit var mockResources: Resources

    @Mock
    lateinit var mockResourcesConfig: Configuration

    @FloatForgery(0f, 1f)
    var fakeViewAlpha: Float = 1f

    @LongForgery
    var fakeViewIdentifier: Long = 0L

    @IntForgery(min = Configuration.UI_MODE_TYPE_UNDEFINED, max = Configuration.UI_MODE_TYPE_VR_HEADSET)
    var fakeUiTypeMode: Int = 0

    // region MappingContext

    fun withTextAndInputPrivacy(textAndInputPrivacy: TextAndInputPrivacy) {
        fakeMappingContext = fakeMappingContext.copy(textAndInputPrivacy = textAndInputPrivacy)
    }

    fun withSystemThemeColor(themeColor: String?) {
        fakeMappingContext = fakeMappingContext.copy(
            systemInformation = fakeMappingContext.systemInformation.copy(themeColor = themeColor)
        )
    }

    // endregion

    // region ViewIdentifierResolver

    fun mockChildUniqueIdentifier(childName: String, identifier: Long?) {
        whenever(mockViewIdentifierResolver.resolveChildUniqueIdentifier(mockMappedView, childName)) doReturn identifier
    }

    // endregion

    // region ColorStringFormatter

    fun mockColorAndAlphaAsHexString(color: Int, alpha: Int, result: String) {
        whenever(mockColorStringFormatter.formatColorAndAlphaAsHexString(color, alpha)) doReturn result
    }

    // endregion

    // region Resources

    fun withUiMode(uiMode: Int) {
        mockResourcesConfig.uiMode = uiMode
        whenever(mockResources.configuration) doReturn mockResourcesConfig
    }

    fun withNightMode() = withUiMode(fakeUiTypeMode or Configuration.UI_MODE_NIGHT_YES)

    fun withDayMode() = withUiMode(fakeUiTypeMode or Configuration.UI_MODE_NIGHT_NO)

    // endregion

    // region View

    inline fun <reified MV : V> prepareMockView(configureMock: (MV) -> Unit = {}) {
        val mock: MV = mock()

        whenever(mock.alpha) doReturn fakeViewAlpha
        whenever(mock.resources) doReturn mockResources

        configureMock(mock)

        // generic mock stubbing
        val fakeDensity = fakeMappingContext.systemInformation.screenDensity
        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(mock, fakeDensity)) doReturn fakeViewGlobalBounds
        whenever(mockViewBoundsResolver.resolveViewPaddedBounds(mock, fakeDensity)) doReturn fakeViewPaddedBounds
        whenever(mockViewIdentifierResolver.resolveViewId(mock)) doReturn fakeViewIdentifier

        mockMappedView = mock
    }

    // endregion

    // region Assertions

    fun assertThatBoundsAreCloseEnough(actual: MobileSegment.Wireframe, expected: MobileSegment.Wireframe) {
        if (actual is MobileSegment.Wireframe.ShapeWireframe && expected is MobileSegment.Wireframe.ShapeWireframe) {
            assertThatShapeBoundsAreCloseEnough(actual, expected)
        } else {
            fail("Can't compare wireframes")
        }
    }

    fun assertThatShapeBoundsAreCloseEnough(
        actual: MobileSegment.Wireframe.ShapeWireframe,
        expected: MobileSegment.Wireframe.ShapeWireframe
    ) {
        assertThat(actual)
            .usingRecursiveComparison()
            .ignoringFields("x", "y", "width", "height", "shapeStyle.cornerRadius") // Ignored due to rounding errors
            .isEqualTo(expected)

        assertThat(actual.x).isCloseTo(expected.x, withinPercentage(BOUNDS_THRESHOLD_PERCENT))
        assertThat(actual.y).isCloseTo(expected.y, withinPercentage(BOUNDS_THRESHOLD_PERCENT))
        assertThat(actual.width).isCloseTo(expected.width, withinPercentage(BOUNDS_THRESHOLD_PERCENT))
        assertThat(actual.height).isCloseTo(expected.height, withinPercentage(BOUNDS_THRESHOLD_PERCENT))

        val actualCornerRadius = actual.shapeStyle?.cornerRadius?.toLong()
        val expectedCornerRadius = expected.shapeStyle?.cornerRadius?.toLong()
        if (actualCornerRadius != null && expectedCornerRadius != null) {
            assertThat(actualCornerRadius).isCloseTo(expectedCornerRadius, withinPercentage(BOUNDS_THRESHOLD_PERCENT))
        }
    }

    // endregion

    companion object {
        const val BOUNDS_THRESHOLD_PERCENT = 12.5
        const val BOUNDS_THRESHOLD_OFFSET = 5L
    }
}
