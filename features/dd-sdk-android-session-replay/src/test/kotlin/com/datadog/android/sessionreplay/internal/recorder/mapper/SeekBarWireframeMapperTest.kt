package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.content.res.ColorStateList
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.SeekBar
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
import kotlin.math.max

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(ApiLevelExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(value = ForgeConfigurator::class)
internal class SeekBarWireframeMapperTest : AbstractWireframeMapperTest<SeekBar, SeekBarWireframeMapper>() {

    @LongForgery
    var fakeThumbTrackId: Long = 0L

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

    @IntForgery
    var fakeThumbColor: Int = 0

    @StringForgery(regex = "#[0-9A-Fa-f]{8}")
    lateinit var fakeThumbHtmlColor: String

    @StringForgery(regex = "#[0-9A-Fa-f]{8}")
    lateinit var fakeNonActiveTrackHtmlColor: String

    @StringForgery(regex = "#[0-9A-Fa-f]{8}")
    lateinit var fakeActiveTrackHtmlColor: String

    @IntForgery(min = 2, max = 512)
    var fakeThumbHRadius: Int = 0

    @IntForgery(min = 2, max = 512)
    var fakeThumbWRadius: Int = 0

    @Mock
    lateinit var mockThumbDrawable: Drawable

    @Mock
    lateinit var mockThumbTintList: ColorStateList

    @Mock
    lateinit var mockProgressTintList: ColorStateList

    @Mock
    lateinit var mockThumbBounds: Rect

    lateinit var expectedActiveTrackWireframe: MobileSegment.Wireframe
    lateinit var expectedNonActiveTrackWireframe: MobileSegment.Wireframe
    lateinit var expectedThumbWireframe: MobileSegment.Wireframe

    @BeforeEach
    fun `set up`() {
        testedWireframeMapper = SeekBarWireframeMapper(
            mockViewIdentifierResolver,
            mockColorStringFormatter,
            mockViewBoundsResolver,
            mockDrawableToColorMapper
        )
    }

    // region Android O+ (allows setting a min progress value)

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `M return partial wireframes W map {invalid thumb id, Android O+}`() {
        // Given
        withPrivacy(SessionReplayPrivacy.ALLOW)
        prepareMockSeekBar()
        mockChildUniqueIdentifier(SeekBarWireframeMapper.THUMB_KEY_NAME, null)

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
    fun `M return partial wireframes W map {invalid active track id, Android O+}`() {
        // Given
        withPrivacy(SessionReplayPrivacy.ALLOW)
        prepareMockSeekBar()
        mockChildUniqueIdentifier(SeekBarWireframeMapper.ACTIVE_TRACK_KEY_NAME, null)

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
        assertThatBoundsAreCloseEnough(wireframes[1], expectedThumbWireframe)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `M return partial wireframes W map {invalid non active track id, Android O+}`() {
        // Given
        withPrivacy(SessionReplayPrivacy.ALLOW)
        prepareMockSeekBar()
        mockChildUniqueIdentifier(SeekBarWireframeMapper.NON_ACTIVE_TRACK_KEY_NAME, null)

        // When
        val wireframes = testedWireframeMapper.map(
            mockMappedView,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        assertThat(wireframes).hasSize(2)
        assertThatBoundsAreCloseEnough(wireframes[0], expectedActiveTrackWireframe)
        assertThatBoundsAreCloseEnough(wireframes[1], expectedThumbWireframe)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `M return wireframes W map {privacy=ALLOW, Android O+}`() {
        // Given
        withPrivacy(SessionReplayPrivacy.ALLOW)
        prepareMockSeekBar()

        // When
        val wireframes = testedWireframeMapper.map(
            mockMappedView,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        assertThat(wireframes).hasSize(3)
        assertThatBoundsAreCloseEnough(wireframes[0], expectedNonActiveTrackWireframe)
        assertThatBoundsAreCloseEnough(wireframes[1], expectedActiveTrackWireframe)
        assertThatBoundsAreCloseEnough(wireframes[2], expectedThumbWireframe)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `M return wireframes W map {privacy=MASK, Android O+}`() {
        // Given
        withPrivacy(SessionReplayPrivacy.MASK)
        prepareMockSeekBar()

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
    fun `M return wireframes W map {privacy=MASK_USER_INPUT, Android O+}`() {
        // Given
        withPrivacy(SessionReplayPrivacy.MASK_USER_INPUT)
        prepareMockSeekBar()

        // When
        val wireframes = testedWireframeMapper.map(
            mockMappedView,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        assertThat(wireframes).hasSize(1)
        assertThat(wireframes[0]).isEqualTo(expectedNonActiveTrackWireframe)
    }

    // endregion

    // region Android < O

    @Test
    fun `M return partial wireframes W map { invalid thumb id }`() {
        // Given
        fakeMinValue = 0
        withPrivacy(SessionReplayPrivacy.ALLOW)
        prepareMockSeekBar()
        mockChildUniqueIdentifier(SeekBarWireframeMapper.THUMB_KEY_NAME, null)

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
    fun `M return partial wireframes W map { invalid active track id }`() {
        // Given
        fakeMinValue = 0
        withPrivacy(SessionReplayPrivacy.ALLOW)
        prepareMockSeekBar()
        mockChildUniqueIdentifier(SeekBarWireframeMapper.ACTIVE_TRACK_KEY_NAME, null)

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
        assertThatBoundsAreCloseEnough(wireframes[1], expectedThumbWireframe)
    }

    @Test
    fun `M return partial wireframes W map { invalid non active track id }`() {
        // Given
        fakeMinValue = 0
        withPrivacy(SessionReplayPrivacy.ALLOW)
        prepareMockSeekBar()
        mockChildUniqueIdentifier(SeekBarWireframeMapper.NON_ACTIVE_TRACK_KEY_NAME, null)

        // When
        val wireframes = testedWireframeMapper.map(
            mockMappedView,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        assertThat(wireframes).hasSize(2)
        assertThatBoundsAreCloseEnough(wireframes[0], expectedActiveTrackWireframe)
        assertThatBoundsAreCloseEnough(wireframes[1], expectedThumbWireframe)
    }

    @Test
    fun `M return wireframes W map {privacy=ALLOW}`() {
        // Given
        fakeMinValue = 0
        withPrivacy(SessionReplayPrivacy.ALLOW)
        prepareMockSeekBar()

        // When
        val wireframes = testedWireframeMapper.map(
            mockMappedView,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        assertThat(wireframes).hasSize(3)
        assertThatBoundsAreCloseEnough(wireframes[0], expectedNonActiveTrackWireframe)
        assertThatBoundsAreCloseEnough(wireframes[1], expectedActiveTrackWireframe)
        assertThatBoundsAreCloseEnough(wireframes[2], expectedThumbWireframe)
    }

    @Test
    fun `M return wireframes W map {privacy=MASK}`() {
        // Given
        fakeMinValue = 0
        withPrivacy(SessionReplayPrivacy.MASK)
        prepareMockSeekBar()

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
    fun `M return wireframes W map {privacy=MASK_USER_INPUT}`() {
        // Given
        fakeMinValue = 0
        withPrivacy(SessionReplayPrivacy.MASK_USER_INPUT)
        prepareMockSeekBar()

        // When
        val wireframes = testedWireframeMapper.map(
            mockMappedView,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        assertThat(wireframes).hasSize(1)
        assertThat(wireframes[0]).isEqualTo(expectedNonActiveTrackWireframe)
    }

    // endregion

    // region Internal

    private fun prepareMockSeekBar() {
        val fakeStateIntArray = fakeDrawableState.toIntArray()

        withUiMode(fakeUiTypeMode)

        prepareMockView<SeekBar> { mockView ->
            whenever(mockView.drawableState) doReturn fakeStateIntArray

            whenever(mockView.thumb) doReturn mockThumbDrawable
            whenever(mockView.thumbTintList) doReturn mockThumbTintList
            whenever(mockView.progressTintList) doReturn mockProgressTintList

            whenever(mockView.min) doReturn fakeMinValue
            whenever(mockView.max) doReturn fakeMaxValue
            whenever(mockView.progress) doReturn ((fakeMaxValue - fakeMinValue) * fakeProgress).toInt() + fakeMinValue
        }

        whenever(mockThumbTintList.getColorForState(eq(fakeStateIntArray), any())) doReturn fakeThumbColor
        whenever(mockProgressTintList.getColorForState(eq(fakeStateIntArray), any())) doReturn fakeTrackColor

        whenever(mockThumbDrawable.bounds) doReturn mockThumbBounds

        mockChildUniqueIdentifier(SeekBarWireframeMapper.THUMB_KEY_NAME, fakeThumbTrackId)
        mockChildUniqueIdentifier(SeekBarWireframeMapper.ACTIVE_TRACK_KEY_NAME, fakeActiveTrackId)
        mockChildUniqueIdentifier(SeekBarWireframeMapper.NON_ACTIVE_TRACK_KEY_NAME, fakeNonActiveTrackId)

        mockColorAndAlphaAsHexString(fakeTrackColor, OPAQUE_ALPHA_VALUE, fakeActiveTrackHtmlColor)
        mockColorAndAlphaAsHexString(fakeTrackColor, PARTIALLY_OPAQUE_ALPHA_VALUE, fakeNonActiveTrackHtmlColor)
        mockColorAndAlphaAsHexString(fakeThumbColor, OPAQUE_ALPHA_VALUE, fakeThumbHtmlColor)

        val screenDensity = fakeMappingContext.systemInformation.screenDensity
        val fakeTrackHeight = TRACK_HEIGHT_IN_PX.densityNormalized(screenDensity)
        val fakeTrackY = fakeViewPaddedBounds.y + ((fakeViewPaddedBounds.height - fakeTrackHeight) / 2)
        whenever(mockThumbBounds.width()) doReturn (screenDensity * fakeThumbWRadius * 2).toInt()
        whenever(mockThumbBounds.height()) doReturn (screenDensity * fakeThumbHRadius * 2).toInt()

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
        expectedThumbWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = fakeThumbTrackId,
            x = fakeViewPaddedBounds.x + (fakeViewPaddedBounds.width * fakeProgress).toLong() - fakeThumbWRadius,
            y = fakeTrackY + (fakeTrackHeight / 2) - fakeThumbHRadius,
            width = fakeThumbWRadius * 2L,
            height = fakeThumbHRadius * 2L,
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = fakeThumbHtmlColor,
                opacity = fakeViewAlpha,
                cornerRadius = max(fakeThumbWRadius, fakeThumbHRadius)
            )
        )
    }

    // endregion
}
