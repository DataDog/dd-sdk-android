/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.trace.core

import com.datadog.android.internal.utils.safeGetThreadId
import com.datadog.trace.api.DDTags
import com.datadog.trace.api.DDTraceId
import com.datadog.trace.api.TracePropagationStyle
import com.datadog.trace.api.sampling.PrioritySampling
import com.datadog.trace.api.sampling.SamplingMechanism
import com.datadog.trace.bootstrap.instrumentation.api.AgentSpan
import com.datadog.trace.common.writer.ListWriter
import com.datadog.trace.core.propagation.ExtractedContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

internal class DDSpanContextTest : DDCoreSpecification() {

    lateinit var writer: ListWriter
    lateinit var tracer: CoreTracer

    @BeforeEach
    override fun setup() {
        super.setup()
        writer = ListWriter()
        tracer = tracerBuilder()
            .writer(writer)
            .build()
    }

    @AfterEach
    override fun cleanup() {
        super.cleanup()
        tracer.close()
    }

    @ParameterizedTest
    @MethodSource("provideTagsForTestNullValues")
    fun `null values for tags delete existing tags`(name: String, tags: Map<String, Any>) {
        // Given
        val span = tracer.buildSpan(instrumentationName, "fakeOperation")
            .withServiceName("fakeService")
            .withResourceName("fakeResource")
            .withSpanType("fakeType")
            .start()
        val context = span.context() as DDSpanContext

        // When
        context.setTag("some.tag", "asdf")
        context.setTag(name, null)
        span.finish()
        // This is assuming you have a writer.waitForTraces() function
        writer.waitForTraces(1)

        // Then
        assertThat(context.tags).containsAllEntriesOf(tags)
        assertThat(context.serviceName).isEqualTo("fakeService")
        assertThat(context.resourceName).isEqualTo("fakeResource")
        assertThat(context.spanType).isEqualTo("fakeType")
    }

    @ParameterizedTest
    @MethodSource("provideSpecialTagsForTest")
    fun `special tags set certain values`(name: String, value: String, method: String) {
        // Given
        val span = tracer.buildSpan(instrumentationName, "fakeOperation")
            .withServiceName("fakeService")
            .withResourceName("fakeResource")
            .withSpanType("fakeType")
            .start()
        val context = span.context() as DDSpanContext

        // When
        context.setTag(name, value)
        span.finish()
        writer.waitForTraces(1)

        // Then
        val thread = Thread.currentThread()
        val expectedTags = mapOf(DDTags.THREAD_NAME to thread.name, DDTags.THREAD_ID to thread.safeGetThreadId())
        assertThat(context.tags).containsAllEntriesOf(expectedTags)
        assertThat(
            context::class.java.getMethod(method)
                .invoke(context)
        )
            .isEqualTo(value)
    }

    @ParameterizedTest
    @MethodSource("provideTagsForTest")
    fun `tags can be added to the context`(name: String, value: Any) {
        // setup
        val span = tracer.buildSpan(instrumentationName, "fakeOperation")
            .withServiceName("fakeService")
            .withResourceName("fakeResource")
            .withSpanType("fakeType")
            .start()
        val context = span.context() as DDSpanContext

        // when
        context.setTag(name, value)
        span.finish()
        writer.waitForTraces(1)
        val thread = Thread.currentThread()

        // then
        val expectedTags = mapOf(
            name to value,
            DDTags.THREAD_NAME to thread.name,
            DDTags.THREAD_ID to thread.safeGetThreadId()
        )
        assertThat(context.tags).containsAllEntriesOf(expectedTags)
    }

    @ParameterizedTest
    @MethodSource("provideMetricsForTest")
    fun `metrics use the expected types`(type: Class<*>, value: Number) {
        // Given
        val span = tracer.buildSpan(instrumentationName, "fakeOperation")
            .withServiceName("fakeService")
            .withResourceName("fakeResource")
            .start()
        val context = span.context() as DDSpanContext

        // When
        context.setMetric("test", value)

        // Then
        val tag = context.getTag("test")
        assertThat(tag).isInstanceOf(type)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `force keep really keeps the trace`() {
        // Given
        val span = tracer.buildSpan(instrumentationName, "fakeOperation")
            .withServiceName("fakeService")
            .withResourceName("fakeResource")
            .start()
        val context = span.context() as DDSpanContext

        // When
        context.setSamplingPriority(PrioritySampling.SAMPLER_DROP.toInt(), SamplingMechanism.DEFAULT.toInt())

        // Then: "priority should be set"
        assertThat(context.samplingPriority).isEqualTo(PrioritySampling.SAMPLER_DROP.toInt())

        // When: "sampling priority locked"
        context.lockSamplingPriority()

        // Then: "override ignored"
        val samplingPriority =
            context.setSamplingPriority(PrioritySampling.USER_DROP.toInt(), SamplingMechanism.MANUAL.toInt())
        assertThat(samplingPriority)
            .isFalse()
        assertThat(context.samplingPriority).isEqualTo(PrioritySampling.SAMPLER_DROP.toInt())

        // When
        context.forceKeep()

        // Then: "lock is bypassed and priority set to USER_KEEP"
        assertThat(context.samplingPriority).isEqualTo(PrioritySampling.USER_KEEP.toInt())

        // Tear down
        span.finish()
    }

    @ParameterizedTest
    @MethodSource("provideSamplingRatesForTest")
    fun `set single span sampling tags`(rate: Double, limit: Int) {
        // Given
        val span = tracer.buildSpan(instrumentationName, "fakeOperation")
            .withServiceName("fakeService")
            .withResourceName("fakeResource")
            .start()
        val context = span.context() as DDSpanContext

        // Then
        assertThat(context.samplingPriority).isEqualTo(PrioritySampling.UNSET.toInt())

        // Given
        context.setSpanSamplingPriority(rate, limit)

        // Then
        assertThat(context.getTag(DDSpanContext.SPAN_SAMPLING_MECHANISM_TAG))
            .isEqualTo(SamplingMechanism.SPAN_SAMPLING_RATE)
        assertThat(context.getTag(DDSpanContext.SPAN_SAMPLING_RULE_RATE_TAG))
            .isEqualTo(rate)
        assertThat(context.getTag(DDSpanContext.SPAN_SAMPLING_MAX_PER_SECOND_TAG))
            .isEqualTo(if (limit == Int.MAX_VALUE) null else limit)
        // single span sampling should not change the trace sampling priority
        assertThat(context.samplingPriority).isEqualTo(PrioritySampling.UNSET.toInt())
        // make sure the `_dd.p.dm` tag has not been set by single span sampling
        assertThat(context.propagationTags.createTagMap().containsKey("_dd.p.dm")).isFalse()
    }

    @Test
    fun `set TraceSegment tags and data on correct span`() {
        // Given
        val extracted = ExtractedContext(
            DDTraceId.from(123),
            456,
            PrioritySampling.SAMPLER_KEEP.toInt(),
            "789",
            tracer.propagationTagsFactory.empty(),
            TracePropagationStyle.DATADOG
        ).withRequestContextDataAppSec("palceholder")

        val top = tracer
            .buildSpan(instrumentationName, "top")
            .asChildOf(extracted as AgentSpan.Context)
            .start()
        val topC = top.context() as DDSpanContext
        val topTS = top.requestContext.traceSegment
        val current = tracer
            .buildSpan(instrumentationName, "current")
            .asChildOf(top.context())
            .start()
        val currentTS = current.requestContext.traceSegment
        val currentC = current.context() as DDSpanContext

        // When
        currentTS.setDataTop("ctd", "[1]")
        currentTS.setTagTop("ctt", "t1")
        currentTS.setDataCurrent("ccd", "[2]")
        currentTS.setTagCurrent("cct", "t2")
        topTS.setDataTop("ttd", "[3]")
        topTS.setTagTop("ttt", "t3")
        topTS.setDataCurrent("tcd", "[4]")
        topTS.setTagCurrent("tct", "t4")

        // Then
        val expectedTopTags = mapOf(
            dataTagFormat("ctd") to "[1]",
            "ctt" to "t1",
            dataTagFormat("ttd") to "[3]",
            "ttt" to "t3",
            dataTagFormat("tcd") to "[4]",
            "tct" to "t4"
        )
        assertThat(topC.tags).containsAllEntriesOf(expectedTopTags)

        val expectedCurrentTags = mapOf(
            dataTagFormat("ccd") to "[2]",
            "cct" to "t2"
        )
        assertThat(currentC.tags).containsAllEntriesOf(expectedCurrentTags)

        // Tear down
        current.finish()
        top.finish()
    }

    @Test
    fun `setting resource name to null is ignored`() {
        // Given
        val span = tracer.buildSpan(instrumentationName, "fakeOperation")
            .withServiceName("fakeService")
            .withResourceName("fakeResource")
            .start()

        // When
        span.setResourceName(null)

        // Then
        assertThat(span.resourceName).isEqualTo("fakeResource")
    }

    private fun dataTagFormat(name: String): String {
        return "_dd.$name.json"
    }

    companion object {
        @JvmStatic
        fun provideTagsForTestNullValues(): Stream<Arguments> = Stream.of(
            Arguments.of(
                DDTags.SERVICE_NAME,
                mapOf(
                    "some.tag" to "asdf",
                    DDTags.THREAD_NAME to Thread.currentThread().name,
                    DDTags.THREAD_ID to Thread.currentThread().safeGetThreadId()
                )
            ),
            Arguments.of(
                DDTags.RESOURCE_NAME,
                mapOf(
                    "some.tag" to "asdf",
                    DDTags.THREAD_NAME to Thread.currentThread().name,
                    DDTags.THREAD_ID to Thread.currentThread().safeGetThreadId()
                )
            ),
            Arguments.of(
                DDTags.SPAN_TYPE,
                mapOf(
                    "some.tag" to "asdf",
                    DDTags.THREAD_NAME to Thread.currentThread().name,
                    DDTags.THREAD_ID to Thread.currentThread().safeGetThreadId()
                )
            ),
            Arguments.of(
                "some.tag",
                mapOf(
                    DDTags.THREAD_NAME to Thread.currentThread().name,
                    DDTags.THREAD_ID to Thread.currentThread().safeGetThreadId()
                )
            )
        )

        @JvmStatic
        fun provideSpecialTagsForTest(): Stream<Arguments> = Stream.of(
            Arguments.of(DDTags.SERVICE_NAME, "different service", "getServiceName"),
            Arguments.of(DDTags.RESOURCE_NAME, "different resource", "getResourceName"),
            Arguments.of(DDTags.SPAN_TYPE, "different type", "getSpanType")
        )

        @JvmStatic
        fun provideTagsForTest(): Stream<Arguments> = Stream.of(
            Arguments.of("tag.name", "some value"),
            Arguments.of("tag with int", 1234),
            Arguments.of("tag-with-bool", false),
            Arguments.of("tag_with_float", 0.321)
        )

        @JvmStatic
        fun provideMetricsForTest(): Stream<Arguments> = Stream.of(
            Arguments.of(java.lang.Integer::class.java, 0),
            Arguments.of(java.lang.Integer::class.java, Int.MAX_VALUE),
            Arguments.of(java.lang.Integer::class.java, Int.MIN_VALUE),
            Arguments.of(java.lang.Short::class.java, Short.MAX_VALUE),
            Arguments.of(java.lang.Short::class.java, Short.MIN_VALUE),
            Arguments.of(java.lang.Float::class.java, Float.MAX_VALUE),
            Arguments.of(java.lang.Float::class.java, Float.MIN_VALUE),
            Arguments.of(java.lang.Double::class.java, Double.MAX_VALUE),
            Arguments.of(java.lang.Double::class.java, Double.MIN_VALUE),
            Arguments.of(java.lang.Float::class.java, 1f),
            Arguments.of(java.lang.Double::class.java, 1.0),
            Arguments.of(java.lang.Float::class.java, 0.5f),
            Arguments.of(java.lang.Double::class.java, 0.5),
            Arguments.of(java.lang.Integer::class.java, 0x55)
        )

        @JvmStatic
        fun provideSamplingRatesForTest(): Stream<Arguments> = Stream.of(
            Arguments.of(1.0, 10),
            Arguments.of(0.5, 100),
            Arguments.of(0.25, Int.MAX_VALUE)
        )
    }
}
