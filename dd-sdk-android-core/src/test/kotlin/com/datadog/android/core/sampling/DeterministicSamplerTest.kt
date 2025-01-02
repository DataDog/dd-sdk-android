package com.datadog.android.core.sampling

import com.datadog.android.utils.forge.Configurator
import com.datadog.trace.sampling.JavaDeterministicSampler
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.stream.Stream

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DeterministicSamplerTest {

    private lateinit var testedSampler: Sampler<ULong>

    private var stubIdConverter: (ULong) -> ULong = { it }

    private lateinit var fakeTraceIds: List<Long>

    @Mock
    lateinit var mockSampleRateProvider: () -> Float

    @BeforeEach
    fun `set up`(forge: Forge) {
        val listSize = forge.anInt(256, 1024)
        fakeTraceIds = forge.aList(listSize) { aLong() }
        testedSampler = DeterministicSampler(
            stubIdConverter,
            mockSampleRateProvider
        )
    }

    @ParameterizedTest
    @MethodSource("hardcodedFixtures")
    fun `M return consistent results W sample() {hardcodedFixtures}`(
        input: Fixture,
        expectedDecision: Boolean
    ) {
        // Given
        whenever(mockSampleRateProvider.invoke()) doReturn input.samplingRate

        // When
        val sampled = testedSampler.sample(input.traceId)

        //
        assertThat(sampled).isEqualTo(expectedDecision)
    }

    @RepeatedTest(128)
    fun `M return consistent results W sample() {java implementation}`(
        @FloatForgery(min = 0f, max = 100f) fakeSampleRate: Float
    ) {
        // Given
        whenever(mockSampleRateProvider.invoke()) doReturn fakeSampleRate
        val javaSampler = JavaDeterministicSampler(fakeSampleRate / 100f)

        // When
        fakeTraceIds.forEach {
            val result = testedSampler.sample(it.toULong())
            val expectedResult = javaSampler.sample(it)

            assertThat(result).isEqualTo(expectedResult)
        }
    }

    @RepeatedTest(128)
    fun `the sampler will sample the values based on the fixed sample rate`(
        @FloatForgery(min = 0f, max = 100f) fakeSampleRate: Float
    ) {
        // Given
        whenever(mockSampleRateProvider.invoke()) doReturn fakeSampleRate
        var sampledIn = 0

        // When
        fakeTraceIds.forEach {
            if (testedSampler.sample(it.toULong())) {
                sampledIn++
            }
        }

        // Then
        val offset = 2.5f * fakeTraceIds.size
        assertThat(sampledIn.toFloat()).isCloseTo(fakeTraceIds.size * fakeSampleRate / 100f, Offset.offset(offset))
    }

    @Test
    fun `when sample rate is 0 all values will be dropped`() {
        // Given
        whenever(mockSampleRateProvider.invoke()) doReturn 0f
        var sampledIn = 0

        // When
        fakeTraceIds.forEach {
            if (testedSampler.sample(it.toULong())) {
                sampledIn++
            }
        }

        // Then
        assertThat(sampledIn).isEqualTo(0)
    }

    @Test
    fun `when sample rate is 100 all values will pass`() {
        // Given
        whenever(mockSampleRateProvider.invoke()) doReturn 100f
        var sampledIn = 0

        // When
        fakeTraceIds.forEach {
            if (testedSampler.sample(it.toULong())) {
                sampledIn++
            }
        }

        // Then
        assertThat(sampledIn).isEqualTo(fakeTraceIds.size)
    }

    @Test
    fun `when sample rate is below 0 it is normalized to 0`(
        @FloatForgery(max = 0f) fakeSampleRate: Float
    ) {
        // Given
        whenever(mockSampleRateProvider.invoke()) doReturn fakeSampleRate

        // When
        val effectiveSampleRate = testedSampler.getSampleRate()

        // Then
        assertThat(effectiveSampleRate).isZero
    }

    @Test
    fun `when sample rate is above 100 it is normalized to 100`(
        @FloatForgery(min = 100.01f) fakeSampleRate: Float
    ) {
        // Given
        whenever(mockSampleRateProvider.invoke()) doReturn fakeSampleRate

        // When
        val effectiveSampleRate = testedSampler.getSampleRate()

        // Then
        assertThat(effectiveSampleRate).isEqualTo(100f)
    }

    /**
     * A data class is necessary to wrap the ULong, otherwise the jvm runner
     * converts it to Long at some point.
     */
    data class Fixture(
        val traceId: ULong,
        val samplingRate: Float
    )

    companion object {

        // Those hardcoded values ensures we are consistent with the decisions of our
        // Backend implementation of the knuth sampling method
        @Suppress("unused")
        @JvmStatic
        fun hardcodedFixtures(): Stream<Arguments> {
            return listOf(
                Arguments.of(Fixture(4815162342u, 55.9f), false),
                Arguments.of(Fixture(4815162342u, 56.0f), true),
                Arguments.of(Fixture(1415926535897932384u, 90.5f), false),
                Arguments.of(Fixture(1415926535897932384u, 90.6f), true),
                Arguments.of(Fixture(718281828459045235u, 7.4f), false),
                Arguments.of(Fixture(718281828459045235u, 7.5f), true),
                Arguments.of(Fixture(41421356237309504u, 32.1f), false),
                Arguments.of(Fixture(41421356237309504u, 32.2f), true),
                Arguments.of(Fixture(6180339887498948482u, 68.2f), false),
                Arguments.of(Fixture(6180339887498948482u, 68.3f), true)
            ).stream()
        }
    }
}
