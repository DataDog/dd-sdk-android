/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.opentracing.propagation

import com.datadog.android.trace.internal.domain.event.BigIntegerUtils
import com.datadog.android.utils.forge.Configurator
import com.datadog.opentracing.DDSpanContext
import com.datadog.tools.unit.setFieldValue
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.math.BigInteger

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(value = Configurator::class)
internal class DatadogHttpCodecTest {

    @Forgery
    lateinit var fakeDDSpanContext: DDSpanContext

    private lateinit var testedInjector: DatadogHttpCodec.Injector

    private lateinit var testedExtractor: DatadogHttpCodec.Extractor

    @Mock
    lateinit var mockBigIntegerUtils: BigIntegerUtils

    @StringForgery(regex = "[0-9]+")
    lateinit var fakeLeastSignificant64BitsTraceId: String

    @StringForgery(regex = "[0-9a-f]{16}")
    lateinit var fakeMostSignificant64BitsTraceId: String

    private lateinit var fakeTaggedHeaders: Map<String, String>

    private lateinit var fakeIdAsHexString: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeIdAsHexString = fakeDDSpanContext.traceId.toString(16)
        fakeTaggedHeaders = forge.aMap(size = forge.anInt(min = 1, max = 10)) {
            anAlphabeticalString() to anAlphabeticalString()
        }
        testedInjector = DatadogHttpCodec.Injector(mockBigIntegerUtils)
        whenever(mockBigIntegerUtils.leastSignificant64BitsAsDecimal(fakeDDSpanContext.traceId))
            .thenReturn(fakeLeastSignificant64BitsTraceId)
        whenever(mockBigIntegerUtils.mostSignificant64BitsAsHex(fakeDDSpanContext.traceId))
            .thenReturn(fakeMostSignificant64BitsTraceId)
        testedExtractor = DatadogHttpCodec.Extractor(fakeTaggedHeaders)
    }

    // region Injector

    @Test
    fun `M inject the required headers W inject`() {
        // Given
        val headers = mutableMapOf<String, String>()

        // When
        testedInjector.inject(fakeDDSpanContext) { key, value ->
            headers[key] = value
        }

        // Then
        assertThat(headers[DatadogHttpCodec.ORIGIN_KEY]).isEqualTo(fakeDDSpanContext.origin)
        assertThat(
            headers[DatadogHttpCodec.LEAST_SIGNIFICANT_TRACE_ID_KEY]
        ).isEqualTo(fakeLeastSignificant64BitsTraceId)
        assertThat(headers[DatadogHttpCodec.DATADOG_TAGS_KEY]).isEqualTo(expectedInjectedTags())
        assertThat(headers[DatadogHttpCodec.SAMPLING_PRIORITY_KEY]).isEqualTo("1")
        assertThat(headers[DatadogHttpCodec.SPAN_ID_KEY])
            .isEqualTo(fakeDDSpanContext.spanId.toString())
        fakeDDSpanContext.baggageItems.forEach { (key, value) ->
            assertThat(headers[DatadogHttpCodec.OT_BAGGAGE_PREFIX + key]).isEqualTo(HttpCodec.encode(value))
        }
    }

    // endregion

    @ParameterizedTest
    @MethodSource("spanContextValues")
    fun `M extract the required headers W extract`(spanContext: DDSpanContext, forge: Forge) {
        // Given
        val headers = resolveExtractedHeadersFromSpanContext(spanContext, forge)

        // When
        val extractedSpanContext = testedExtractor.extract {
            headers.iterator()
        } as? ExtractedContext

        // Then
        checkNotNull(extractedSpanContext)
        assertThat(extractedSpanContext.origin).isEqualTo(spanContext.origin)
        assertThat(extractedSpanContext.traceId).isEqualTo(spanContext.traceId)
        assertThat(extractedSpanContext.spanId).isEqualTo(spanContext.spanId)
        assertThat(extractedSpanContext.samplingPriority).isEqualTo(spanContext.samplingPriority)
        assertThat(extractedSpanContext.baggageItems())
            .containsExactlyInAnyOrder(*spanContext.baggageItems.entries.toTypedArray())
    }

    @Test
    fun `M extract the TagContext W extract { traceId 0 }`(forge: Forge) {
        // Given
        fakeDDSpanContext.setFieldValue("traceId", BigInteger.ZERO)
        val headers = resolveExtractedHeadersFromSpanContext(fakeDDSpanContext, forge)

        // When
        val extractedSpanContext = testedExtractor.extract {
            headers.iterator()
        } as? TagContext

        // Then
        checkNotNull(extractedSpanContext)
        assertThat(extractedSpanContext.origin).isEqualTo(fakeDDSpanContext.origin)
    }

    @Test
    fun `M extract the TagContext W extract { traceId is missing }`(forge: Forge) {
        // Given
        val headers = resolveExtractedHeadersFromSpanContext(fakeDDSpanContext, forge).apply {
            remove(DatadogHttpCodec.LEAST_SIGNIFICANT_TRACE_ID_KEY)
        }

        // When
        val extractedSpanContext = testedExtractor.extract {
            headers.iterator()
        } as? TagContext

        // Then
        checkNotNull(extractedSpanContext)
        assertThat(extractedSpanContext.origin).isEqualTo(fakeDDSpanContext.origin)
    }

    @Test
    fun `M return null W extract { most significant trace id tag is broken }`(forge: Forge) {
        // Given
        val headers = resolveExtractedHeadersFromSpanContext(fakeDDSpanContext, forge).apply {
            remove(DatadogHttpCodec.DATADOG_TAGS_KEY)
            set(DatadogHttpCodec.DATADOG_TAGS_KEY, DatadogHttpCodec.MOST_SIGNIFICANT_TRACE_ID_KEY + "=broken")
        }

        // When
        val extractedSpanContext = testedExtractor.extract {
            headers.iterator()
        }

        // Then
        assertThat(extractedSpanContext).isNull()
    }

    @Test
    fun `M return TagContext W extract { most significant trace id value is missing }`(forge: Forge) {
        // Given
        val headers = resolveExtractedHeadersFromSpanContext(fakeDDSpanContext, forge).apply {
            remove(DatadogHttpCodec.DATADOG_TAGS_KEY)
            set(DatadogHttpCodec.DATADOG_TAGS_KEY, DatadogHttpCodec.MOST_SIGNIFICANT_TRACE_ID_KEY)
        }

        // When
        val extractedSpanContext = testedExtractor.extract {
            headers.iterator()
        } as? TagContext

        // Then
        checkNotNull(extractedSpanContext)
        assertThat(extractedSpanContext.origin).isEqualTo(fakeDDSpanContext.origin)
    }

    @Test
    fun `M return null W extract { origin is null traceId is 0 }`(forge: Forge) {
        // Given
        fakeDDSpanContext.setFieldValue("traceId", BigInteger.ZERO)
        val headers = resolveExtractedHeadersFromSpanContext(fakeDDSpanContext, forge).apply {
            remove(DatadogHttpCodec.ORIGIN_KEY)
        }

        // When
        val extractedSpanContext = testedExtractor.extract {
            headers.iterator()
        }

        // Then
        assertThat(extractedSpanContext).isNull()
    }

    @Test
    fun `M return null W extract { out of bounds least significant trace id }`(forge: Forge) {
        // Given
        val outOfBoundsSize = forge.anInt(min = 17, max = 32)
        val fakeOutOfBoundsLeastSignificantTraceId =
            BigInteger(forge.aStringMatching("[0-9][a-f]{$outOfBoundsSize}"), 16).toString()
        val headers = resolveExtractedHeadersFromSpanContext(fakeDDSpanContext, forge).apply {
            set(DatadogHttpCodec.LEAST_SIGNIFICANT_TRACE_ID_KEY, fakeOutOfBoundsLeastSignificantTraceId)
        }

        // When
        val extractedSpanContext = testedExtractor.extract {
            headers.iterator()
        }

        // Then
        assertThat(extractedSpanContext).isNull()
    }

    @Test
    fun `M return null W extract { out of bounds most significant trace id }`(forge: Forge) {
        // Given
        val outOfBoundsSize = forge.anInt(min = 17, max = 32)
        val fakeOutOfBoundsMostSignificantTraceId =
            BigInteger(forge.aStringMatching("[0-9][a-f]{$outOfBoundsSize}"), 16).toString(16)
        val traceIdTagsAndNoise = traceIdTagsAndNoise(
            fakeOutOfBoundsMostSignificantTraceId,
            forge
        )
        val headers = resolveExtractedHeadersFromSpanContext(fakeDDSpanContext, forge).apply {
            set(DatadogHttpCodec.DATADOG_TAGS_KEY, traceIdTagsAndNoise)
        }

        // When
        val extractedSpanContext = testedExtractor.extract {
            headers.iterator()
        }

        // Then
        assertThat(extractedSpanContext).isNull()
    }

    // region internal

    private fun resolveExtractedHeadersFromSpanContext(
        spanContext: DDSpanContext,
        forge: Forge
    ): MutableMap<String, String> {
        val headers = mutableMapOf<String, String>()
        headers[DatadogHttpCodec.ORIGIN_KEY] = spanContext.origin
        headers[DatadogHttpCodec.LEAST_SIGNIFICANT_TRACE_ID_KEY] = BigIntegerUtils.leastSignificant64BitsAsDecimal(
            spanContext.traceId
        )
        val traceIdTagsAndNoise = traceIdTagsAndNoise(
            BigIntegerUtils.mostSignificant64BitsAsHex(spanContext.traceId),
            forge
        )
        headers[DatadogHttpCodec.DATADOG_TAGS_KEY] = traceIdTagsAndNoise
        headers[DatadogHttpCodec.SAMPLING_PRIORITY_KEY] = spanContext.samplingPriority.toString()
        headers[DatadogHttpCodec.SPAN_ID_KEY] = spanContext.spanId.toString()
        spanContext.baggageItems.forEach { (key, value) ->
            headers[DatadogHttpCodec.OT_BAGGAGE_PREFIX + key] = HttpCodec.encode(value)
        }
        return headers
    }

    private fun expectedInjectedTags(): String {
        return "_dd.p.tid=$fakeMostSignificant64BitsTraceId"
    }

    private fun traceIdTagsAndNoise(traceId: String, forge: Forge): String {
        val noiseTags = forge.aMap(size = forge.anInt(min = 0, max = 10)) {
            anAlphabeticalString() to anAlphabeticalString()
        }
        return "_dd.p.tid=$traceId" +
            "," + noiseTags.map { "${it.key}=${it.value}" }.joinToString(separator = ",")
    }

    // endregion

    companion object {
        val forge = Forge().apply {
            Configurator().configure(this)
        }

        @JvmStatic
        @MethodSource("spanContextValues")
        fun spanContextValues(): List<DDSpanContext> {
            val max128BitsHex = forge.aStringMatching("[f]{32}")
            val minLeastSignificant128BitsHex = forge.aStringMatching("[0]{31}[1]{1}")
            val minMostSignificant128BitsHex = forge.aStringMatching("[0]{15}[1]{1}[0]{16}")
            return listOf(
                forge.getForgery<DDSpanContext>(),
                forge.getForgery<DDSpanContext>().apply {
                    setFieldValue("traceId", BigInteger(max128BitsHex, 16))
                },
                forge.getForgery<DDSpanContext>().apply {
                    setFieldValue("traceId", BigInteger(minLeastSignificant128BitsHex, 16))
                },
                forge.getForgery<DDSpanContext>().apply {
                    setFieldValue("traceId", BigInteger(minMostSignificant128BitsHex, 16))
                }
            )
        }
    }
}
