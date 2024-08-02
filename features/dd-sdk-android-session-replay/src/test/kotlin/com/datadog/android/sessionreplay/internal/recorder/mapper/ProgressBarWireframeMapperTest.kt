package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.content.res.ColorStateList
import android.graphics.Rect
import android.os.Build
import android.widget.ProgressBar
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.densityNormalized
import com.datadog.android.sessionreplay.internal.recorder.mapper.SeekBarWireframeMapper.Companion.TRACK_HEIGHT_IN_PX
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.mapper.AbstractWireframeMapperTest
import com.datadog.android.sessionreplay.utils.OPAQUE_ALPHA_VALUE
import com.datadog.android.sessionreplay.utils.PARTIALLY_OPAQUE_ALPHA_VALUE
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
import fr.xgouchet.elmyr.annotation.FloatForgery
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
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(ApiLevelExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class ProgressBarWireframeMapperTest :
    AbstractWireframeMapperTest<ProgressBar, ProgressBarWireframeMapper<ProgressBar>>() {

    @LongForgery
    var fakeActiveTrackId: Long = 0L

    @LongForgery
    var fakeNonActiveTrackId: Long = 0L

    @IntForgery(min = 0, max = 512)
    var fakeMinValue: Int = 0

    @IntForgery(min = 512, max = 65536)
    var fakeMaxValue: Int = 0

    @FloatForgery(min = 0f, max = 1f)
    var fakeProgress: Float = 0f

    @IntForgery
    lateinit var fakeDrawableState: List<Int>

    @IntForgery
    var fakeTrackColor: Int = 0

    @StringForgery(regex = "#[0-9A-Fa-f]{8}")
    lateinit var fakeNonActiveTrackHtmlColor: String

    @StringForgery(regex = "#[0-9A-Fa-f]{8}")
    lateinit var fakeActiveTrackHtmlColor: String

    @Mock
    lateinit var mockProgressTintList: ColorStateList

    @Mock
    lateinit var mockThumbBounds: Rect

    lateinit var expectedActiveTrackWireframe: MobileSegment.Wireframe
    lateinit var expectedNonActiveTrackWireframe: MobileSegment.Wireframe

    @BeforeEach
    fun `set up`() {
        testedWireframeMapper = ProgressBarWireframeMapper(
            mockViewIdentifierResolver,
            mockColorStringFormatter,
            mockViewBoundsResolver,
            mockDrawableToColorMapper,
            showProgressWhenMaskUserInput = true
        )
    }

    // region Indeterminate

    @Test
    fun `M return generic wireframes W map {indeterminate}`() {
        // Given
        withPrivacy(SessionReplayPrivacy.ALLOW)
        prepareMockProgressBar(isIndeterminate = true)

        // When
        val wireframes = testedWireframeMapper.map(
            mockMappedView,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        assertThat(wireframes).hasSize(1)
        assertThatBoundsAreCloseEnough(wireframes[0], expectedNonActiveTrackWireframe)
    }

    // endregion

    // region Android O+, determinate

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `M return partial wireframes W map {determinate, invalid track id, Android 0+}`() {
        // Given
        withPrivacy(SessionReplayPrivacy.ALLOW)
        prepareMockProgressBar(isIndeterminate = false)
        mockChildUniqueIdentifier(SeekBarWireframeMapper.ACTIVE_TRACK_KEY_NAME, null)

        // When
        val wireframes = testedWireframeMapper.map(
            mockMappedView,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        assertThat(wireframes).hasSize(1)
        assertThatBoundsAreCloseEnough(wireframes[0], expectedNonActiveTrackWireframe)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `M return partial wireframes W map {determinate, invalid non active track id, Android 0+}`() {
        // Given
        withPrivacy(SessionReplayPrivacy.ALLOW)
        prepareMockProgressBar(isIndeterminate = false)
        mockChildUniqueIdentifier(SeekBarWireframeMapper.NON_ACTIVE_TRACK_KEY_NAME, null)

        // When
        val wireframes = testedWireframeMapper.map(
            mockMappedView,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        assertThat(wireframes).hasSize(1)
        assertThatBoundsAreCloseEnough(wireframes[0], expectedActiveTrackWireframe)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `M return wireframes W map {determinate, privacy=ALLOW, Android 0+}`() {
        // Given
        withPrivacy(SessionReplayPrivacy.ALLOW)
        prepareMockProgressBar(isIndeterminate = false)

        // When
        val wireframes = testedWireframeMapper.map(
            mockMappedView,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        assertThat(wireframes).hasSize(2)
        assertThatBoundsAreCloseEnough(wireframes[0], expectedNonActiveTrackWireframe)
        assertThatBoundsAreCloseEnough(wireframes[1], expectedActiveTrackWireframe)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `M return wireframes W map {determinate, privacy=MASK, Android 0+}`() {
        // Given
        withPrivacy(SessionReplayPrivacy.MASK)
        prepareMockProgressBar(isIndeterminate = false)

        // When
        val wireframes = testedWireframeMapper.map(
            mockMappedView,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        assertThat(wireframes).hasSize(1)
        assertThatBoundsAreCloseEnough(wireframes[0], expectedNonActiveTrackWireframe)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `M return wireframes W map {determinate, privacy=MASK_USER_INPUT, Android 0+}`() {
        // Given
        withPrivacy(SessionReplayPrivacy.MASK_USER_INPUT)
        prepareMockProgressBar(isIndeterminate = false)

        // When
        val wireframes = testedWireframeMapper.map(
            mockMappedView,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        assertThat(wireframes).hasSize(2)
        assertThatBoundsAreCloseEnough(wireframes[0], expectedNonActiveTrackWireframe)
        assertThatBoundsAreCloseEnough(wireframes[1], expectedActiveTrackWireframe)
    }

    // endregion

    // region API < Android O, determinate

    @Test
    fun `M return partial wireframes W map {determinate, invalid track id}`() {
        // Given
        fakeMinValue = 0
        withPrivacy(SessionReplayPrivacy.ALLOW)
        prepareMockProgressBar(isIndeterminate = false)
        mockChildUniqueIdentifier(SeekBarWireframeMapper.ACTIVE_TRACK_KEY_NAME, null)

        // When
        val wireframes = testedWireframeMapper.map(
            mockMappedView,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        assertThat(wireframes).hasSize(1)
        assertThatBoundsAreCloseEnough(wireframes[0], expectedNonActiveTrackWireframe)
    }

    @Test
    fun `M return partial wireframes W map {determinate, invalid non active track id}`() {
        // Given
        fakeMinValue = 0
        withPrivacy(SessionReplayPrivacy.ALLOW)
        prepareMockProgressBar(isIndeterminate = false)
        mockChildUniqueIdentifier(SeekBarWireframeMapper.NON_ACTIVE_TRACK_KEY_NAME, null)

        // When
        val wireframes = testedWireframeMapper.map(
            mockMappedView,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        assertThat(wireframes).hasSize(1)
        assertThatBoundsAreCloseEnough(wireframes[0], expectedActiveTrackWireframe)
    }

    @Test
    fun `M return wireframes W map {determinate, privacy=ALLOW}`() {
        // Given
        fakeMinValue = 0
        withPrivacy(SessionReplayPrivacy.ALLOW)
        prepareMockProgressBar(isIndeterminate = false)

        // When
        val wireframes = testedWireframeMapper.map(
            mockMappedView,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        assertThat(wireframes).hasSize(2)
        assertThatBoundsAreCloseEnough(wireframes[0], expectedNonActiveTrackWireframe)
        assertThatBoundsAreCloseEnough(wireframes[1], expectedActiveTrackWireframe)
    }

    @Test
    fun `M return wireframes W map {determinate, privacy=MASK}`() {
        // Given
        fakeMinValue = 0
        withPrivacy(SessionReplayPrivacy.MASK)
        prepareMockProgressBar(isIndeterminate = false)

        // When
        val wireframes = testedWireframeMapper.map(
            mockMappedView,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        assertThat(wireframes).hasSize(1)
        assertThatBoundsAreCloseEnough(wireframes[0], expectedNonActiveTrackWireframe)
    }

    @Test
    fun `M return wireframes W map {determinate, privacy=MASK_USER_INPUT}`() {
        // Given
        fakeMinValue = 0
        withPrivacy(SessionReplayPrivacy.MASK_USER_INPUT)
        prepareMockProgressBar(isIndeterminate = false)

        // When
        val wireframes = testedWireframeMapper.map(
            mockMappedView,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        assertThat(wireframes).hasSize(2)
        assertThatBoundsAreCloseEnough(wireframes[0], expectedNonActiveTrackWireframe)
        assertThatBoundsAreCloseEnough(wireframes[1], expectedActiveTrackWireframe)
    }

    // endregion

    // region Internal

    private fun prepareMockProgressBar(isIndeterminate: Boolean) {
        val fakeStateIntArray = fakeDrawableState.toIntArray()

        withUiMode(fakeUiTypeMode)

        prepareMockView<ProgressBar> { mockView ->
            whenever(mockView.drawableState) doReturn fakeStateIntArray

            whenever(mockView.progressTintList) doReturn mockProgressTintList

            whenever(mockView.min) doReturn fakeMinValue
            whenever(mockView.max) doReturn fakeMaxValue
            whenever(mockView.progress) doReturn ((fakeMaxValue - fakeMinValue) * fakeProgress).toInt() + fakeMinValue
            whenever(mockView.isIndeterminate) doReturn isIndeterminate
        }

        whenever(mockProgressTintList.getColorForState(eq(fakeStateIntArray), any())) doReturn fakeTrackColor

        mockChildUniqueIdentifier(SeekBarWireframeMapper.ACTIVE_TRACK_KEY_NAME, fakeActiveTrackId)
        mockChildUniqueIdentifier(SeekBarWireframeMapper.NON_ACTIVE_TRACK_KEY_NAME, fakeNonActiveTrackId)

        mockColorAndAlphaAsHexString(fakeTrackColor, OPAQUE_ALPHA_VALUE, fakeActiveTrackHtmlColor)
        mockColorAndAlphaAsHexString(fakeTrackColor, PARTIALLY_OPAQUE_ALPHA_VALUE, fakeNonActiveTrackHtmlColor)

        val screenDensity = fakeMappingContext.systemInformation.screenDensity
        val fakeTrackHeight = TRACK_HEIGHT_IN_PX.densityNormalized(screenDensity)
        val fakeTrackY = fakeViewPaddedBounds.y + ((fakeViewPaddedBounds.height - fakeTrackHeight) / 2)

        expectedNonActiveTrackWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = fakeNonActiveTrackId,
            x = fakeViewPaddedBounds.x,
            y = fakeTrackY,
            width = fakeViewPaddedBounds.width,
            height = fakeTrackHeight,
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = fakeNonActiveTrackHtmlColor,
                opacity = fakeViewAlpha
            )
        )
        expectedActiveTrackWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = fakeActiveTrackId,
            x = fakeViewPaddedBounds.x,
            y = fakeTrackY,
            width = (fakeViewPaddedBounds.width * fakeProgress).toLong(),
            height = fakeTrackHeight,
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = fakeActiveTrackHtmlColor,
                opacity = fakeViewAlpha
            )
        )
    }

    // endregion
}
