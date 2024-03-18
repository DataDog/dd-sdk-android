/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.trace.core

import com.datadog.tools.unit.getFieldValue
import com.datadog.trace.api.DDSpanId
import com.datadog.trace.api.DDTags
import com.datadog.trace.api.DDTraceId
import com.datadog.trace.api.TracePropagationStyle
import com.datadog.trace.api.gateway.RequestContextSlot
import com.datadog.trace.api.sampling.PrioritySampling
import com.datadog.trace.api.sampling.SamplingMechanism
import com.datadog.trace.bootstrap.instrumentation.api.AgentSpan
import com.datadog.trace.bootstrap.instrumentation.api.AgentTracer.NoopPathwayContext
import com.datadog.trace.bootstrap.instrumentation.api.ErrorPriorities
import com.datadog.trace.bootstrap.instrumentation.api.TagContext
import com.datadog.trace.bootstrap.instrumentation.api.UTF8BytesString
import com.datadog.trace.common.sampling.RateByServiceTraceSampler
import com.datadog.trace.common.writer.ListWriter
import com.datadog.trace.core.propagation.ExtractedContext
import com.datadog.trace.core.propagation.PropagationTags
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.stream.Stream

internal class DDSpanTest : DDCoreSpecification() {
    lateinit var writer: ListWriter
    lateinit var sampler: RateByServiceTraceSampler
    lateinit var tracer: CoreTracer

    @BeforeEach
    override fun setup() {
        super.setup()
        writer = ListWriter()
        sampler = RateByServiceTraceSampler()
        tracer = tracerBuilder().writer(writer).sampler(sampler).build()
    }

    @AfterEach
    override fun cleanup() {
        super.cleanup()
        tracer.close()
    }

    @Suppress("DEPRECATION")
    @Test
    fun `getters and setters`() {
        val span = tracer.buildSpan(instrumentationName, "fakeOperation")
            .withServiceName("fakeService")
            .withResourceName("fakeResource")
            .withSpanType("fakeType")
            .start()

        span.setServiceName("service")
        assertThat(span.serviceName).isEqualTo("service")

        span.setOperationName("operation")
        assertThat(span.operationName).isEqualTo("operation")

        span.setResourceName("resource")
        assertThat(span.resourceName).isEqualTo("resource")

        span.setSpanType("type")
        assertThat(span.spanType).isEqualTo("type")

        span.setSamplingPriority(PrioritySampling.UNSET.toInt())
        assertNull(span.samplingPriority)

        span.setSamplingPriority(PrioritySampling.SAMPLER_KEEP.toInt())
        assertThat(span.samplingPriority).isEqualTo(PrioritySampling.SAMPLER_KEEP.toInt())

        (span.context() as DDSpanContext).lockSamplingPriority()
        span.setSamplingPriority(PrioritySampling.USER_KEEP.toInt())
        assertThat(span.samplingPriority).isEqualTo(PrioritySampling.SAMPLER_KEEP.toInt())
    }

    @Test
    fun `resource name equals operation name if null`() {
        val opName = "operationName"
        var span = tracer.buildSpan(instrumentationName, opName).start()

        assertThat(span.resourceName).isEqualTo(opName)
        assertThat(span.serviceName).isNotEmpty()

        val resourceName = "fake"
        val serviceName = "myService"
        span = tracer.buildSpan(instrumentationName, opName)
            .withResourceName(resourceName)
            .withServiceName(serviceName)
            .start()

        assertThat(span.resourceName).isEqualTo(resourceName)
        assertThat(span.serviceName).isEqualTo(serviceName)
    }

    @Test
    fun `duration measured in nanoseconds`() {
        val mod = TimeUnit.MILLISECONDS.toNanos(1)
        val builder = tracer.buildSpan(instrumentationName, "test")
        val start = System.nanoTime()
        val span = builder.start()
        val between = System.nanoTime()
        val betweenDur = System.nanoTime() - between
        span.finish()
        val total = System.nanoTime() - start
        val timeDifference = TimeUnit.NANOSECONDS.toSeconds(span.startTime) -
                TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())
        assertThat(timeDifference).isLessThan(5)
        assertThat(span.durationNano).isGreaterThan(betweenDur)
        assertThat(span.durationNano).isLessThan(total)
        assertThat(span.durationNano % mod).isGreaterThan(0)
    }

    @Test
    fun `phasedFinish captures duration but doesn't publish immediately`() {
        val mod = TimeUnit.MILLISECONDS.toNanos(1)
        val builder = tracer.buildSpan(instrumentationName, "test")
        val start = System.nanoTime()
        val span = builder.start()
        val between = System.nanoTime()
        val betweenDur = System.nanoTime() - between

        span.publish()

        assertThat(span.durationNano).isEqualTo(0)
        assertThat(writer.size).isEqualTo(0)

        var finish = span.phasedFinish()
        val total = System.nanoTime() - start

        assertThat(finish).isTrue()
        assertThat(writer).isEmpty()

        val actualDurationNano = span.durationNano and Long.MAX_VALUE
        val timeDifference = TimeUnit.NANOSECONDS.toSeconds(span.startTime) -
                TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())
        assertThat(timeDifference).isLessThan(5)
        assertThat(actualDurationNano).isGreaterThan(betweenDur)
        assertThat(actualDurationNano).isLessThan(total)
        assertThat(actualDurationNano % mod).isGreaterThan(0)

        finish = span.phasedFinish()
        span.finish()
        assertThat(finish).isFalse()
        assertThat(writer).isEmpty()

        span.publish()
        assertThat(span.durationNano).isPositive()
        assertThat(span.durationNano).isEqualTo(actualDurationNano)
        assertThat(writer.size).isEqualTo(1)

        span.publish()
        assertThat(writer.size).isEqualTo(1)
    }

    @Test
    fun `starting with a timestamp disables nanotime`() {
        val mod = TimeUnit.MILLISECONDS.toNanos(1)
        val start = System.currentTimeMillis()
        val builder = tracer.buildSpan(instrumentationName, "test")
            .withStartTimestamp(TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis()))
        val span = builder.start()
        val between = System.currentTimeMillis()
        val betweenDur = System.currentTimeMillis() - between
        span.finish()
        val total = Math.max(1, System.currentTimeMillis() - start)
        val timeDifference = TimeUnit.NANOSECONDS.toSeconds(span.startTime) -
                TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())

        assertThat(timeDifference).isLessThan(5)
        assertThat(span.durationNano).isGreaterThanOrEqualTo(TimeUnit.MILLISECONDS.toNanos(betweenDur))
        assertThat(span.durationNano).isLessThanOrEqualTo(TimeUnit.MILLISECONDS.toNanos(total))
        assertThat(span.durationNano % mod).isIn(0L, 1L)
    }

    @Test
    fun `stopping with a timestamp disables nanotime`() {
        val mod = TimeUnit.MILLISECONDS.toNanos(1)
        val builder = tracer.buildSpan(instrumentationName, "test")
        val start = System.currentTimeMillis()
        val span = builder.start()
        val between = System.currentTimeMillis()
        val betweenDur = System.currentTimeMillis() - between
        span.finish(TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis() + 1))
        val total = System.currentTimeMillis() - start + 1
        val timeDifference = TimeUnit.NANOSECONDS.toSeconds(span.startTime) -
                TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())

        assertThat(timeDifference).isLessThan(5)
        assertThat(span.durationNano).isGreaterThanOrEqualTo(TimeUnit.MILLISECONDS.toNanos(betweenDur))
        assertThat(span.durationNano).isLessThanOrEqualTo(TimeUnit.MILLISECONDS.toNanos(total))
        assertThat(span.durationNano % mod).isIn(0L, 1L)
    }

    @Test
    fun `stopping with a timestamp before start time yields a min duration of 1`() {
        val span = tracer.buildSpan(instrumentationName, "test").start() as DDSpan

        span.finish(TimeUnit.MILLISECONDS.toMicros(TimeUnit.NANOSECONDS.toMillis(span.startTimeNano)) - 10)

        assertThat(span.durationNano).isEqualTo(1)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `priority sampling metric set only on root span`() {
        val parent = tracer.buildSpan(instrumentationName, "testParent").start()
        val child1 = tracer.buildSpan(instrumentationName, "testChild1").asChildOf(parent.context()).start()

        child1.setSamplingPriority(PrioritySampling.SAMPLER_KEEP.toInt())
        (child1.context() as DDSpanContext).lockSamplingPriority()
        parent.setSamplingPriority(PrioritySampling.SAMPLER_DROP.toInt())
        child1.finish()
        val child2 = tracer.buildSpan(instrumentationName, "testChild2").asChildOf(parent.context()).start()
        child2.finish()
        parent.finish()

        assertThat(parent.context().samplingPriority).isEqualTo(PrioritySampling.SAMPLER_KEEP.toInt())
        assertThat(parent.samplingPriority).isEqualTo(PrioritySampling.SAMPLER_KEEP.toInt())
        assertThat(child1.samplingPriority).isEqualTo(parent.samplingPriority)
        assertThat(child2.samplingPriority).isEqualTo(parent.samplingPriority)
    }

    @ParameterizedTest
    @MethodSource("originExtractionTests")
    fun `origin set only on root span`(extractedContext: AgentSpan.Context) {
        val parent = tracer
            .buildSpan(instrumentationName, "testParent")
            .asChildOf(extractedContext)
            .start()
            .context() as DDSpanContext
        val child = tracer
            .buildSpan(instrumentationName, "testChild1")
            .asChildOf(parent)
            .start()
            .context() as DDSpanContext

        assertThat(child.origin).isEqualTo("some-origin")
        // Access field directly instead of getter not directly possible in Kotlin
        assertThat(child.origin).isEqualTo("some-origin")
        // Access field directly instead of getter not directly possible in Kotlin, Nullable origin would be null at runtime
    }

    @ParameterizedTest
    @MethodSource("isRootSpanTests")
    fun `isRootSpan() in and not in the context of distributed tracing`(
        context: AgentSpan.Context?,
        isTraceRootSpan: Boolean
    ) {
        val root = tracer.buildSpan(instrumentationName, "root").asChildOf(context).start() as DDSpan
        val child = tracer.buildSpan(instrumentationName, "child").asChildOf(root.context()).start() as DDSpan

        assertThat(child.isRootSpan).isFalse
        assertThat(root.isRootSpan).isEqualTo(isTraceRootSpan)
    }

    @Suppress("DEPRECATION")
    @ParameterizedTest
    @MethodSource("getApplicationRootSpanTests")
    fun `getApplicationRootSpan() in and not in the context of distributed tracing`(
        extractedContext: AgentSpan.Context?
    ) {
        val root = tracer.buildSpan(instrumentationName, "root").asChildOf(extractedContext).start()
        val child = tracer.buildSpan(instrumentationName, "child").asChildOf(root.context()).start()

        assertThat(child.localRootSpan).isEqualTo(root)
        assertThat(child.localRootSpan).isEqualTo(root)
        assertThat(root.rootSpan).isEqualTo(root)
        assertThat(child.rootSpan).isEqualTo(root)
    }

    @Test
    fun `publishing of root span closes the request context data`() {
        val reqContextData: Closeable = mock()
        val context = TagContext().withRequestContextDataAppSec(reqContextData)
        val root = tracer.buildSpan(instrumentationName, "root").asChildOf(context).start()
        val child = tracer.buildSpan(instrumentationName, "child").asChildOf(root.context()).start()

        val rootContextData = root.requestContext.getData<Any>(RequestContextSlot.APPSEC)
        assertThat(rootContextData).isEqualTo(reqContextData)
        val childContextData = child.requestContext.getData<Any>(RequestContextSlot.APPSEC)
        assertThat(childContextData).isEqualTo(reqContextData)

        child.finish()

        verify(reqContextData, times(0)).close()

        root.finish()

        verify(reqContextData, times(1)).close()
    }

    @Test
    fun `infer top level from parent service name`() {
        val propagationTagsFactory = tracer.propagationTagsFactory
        val dataSet = listOf(
            Pair("foo", true),
            Pair(UTF8BytesString.create("foo"), true),
            Pair("fakeService", false),
            Pair(UTF8BytesString.create("fakeService"), false),
            Pair("", true),
            Pair(null, true)
        )
        val pendingTracerFactory: PendingTrace.Factory = tracer.getFieldValue("pendingTraceFactory")
        dataSet.forEach { (parentServiceName, expectedTopLevel) ->
            val context = DDSpanContext(
                DDTraceId.ONE,
                1,
                DDSpanId.ZERO,
                parentServiceName,
                "fakeService",
                "fakeOperation",
                "fakeResource",
                PrioritySampling.UNSET.toInt(),
                null,
                emptyMap(),
                false,
                "fakeType",
                0,
                pendingTracerFactory.create(DDTraceId.ONE),
                null,
                null,
                NoopPathwayContext.INSTANCE,
                false,
                propagationTagsFactory.empty()
            )
            assertThat(context.isTopLevel).isEqualTo(expectedTopLevel)
        }
    }

    @Test
    fun `broken pipe exception does not create error span`() {
        val span = tracer.buildSpan(instrumentationName, "root").start()
        span.addThrowable(IOException("Broken pipe"))

        assertThat(span.isError()).isFalse
        assertThat(span.getTag(DDTags.ERROR_STACK)).isNull()
        assertThat(span.getTag(DDTags.ERROR_MSG)).isEqualTo("Broken pipe")
    }

    @Test
    fun `wrapped broken pipe exception does not create error span`() {
        val span = tracer.buildSpan(instrumentationName, "root").start()
        span.addThrowable(RuntimeException(IOException("Broken pipe")))

        assertThat(span.isError()).isFalse
        assertThat(span.getTag(DDTags.ERROR_STACK)).isNull()
        assertThat(span.getTag(DDTags.ERROR_MSG)).isEqualTo("java.io.IOException: Broken pipe")
    }

    @Test
    fun `null exception safe to add`() {
        val span = tracer.buildSpan(instrumentationName, "root").start()
        span.addThrowable(null)

        assertThat(span.isError()).isFalse
        assertThat(span.getTag(DDTags.ERROR_STACK)).isNull()
    }

    @ParameterizedTest
    @MethodSource("spanSamplingAttributes")
    fun `set single span sampling tags`(rate: Double, limit: Int) {
        val span = tracer.buildSpan(instrumentationName, "testSpan").start() as DDSpan
        val expectedLimit = if (limit == Int.MAX_VALUE) null else limit

        assertThat(span.samplingPriority()).isEqualTo(PrioritySampling.UNSET.toInt())

        span.setSpanSamplingPriority(rate, limit)

        assertThat(span.getTag(DDSpanContext.SPAN_SAMPLING_MECHANISM_TAG))
            .isEqualTo(SamplingMechanism.SPAN_SAMPLING_RATE)
        assertThat(span.getTag(DDSpanContext.SPAN_SAMPLING_RULE_RATE_TAG)).isEqualTo(rate)
        assertThat(span.getTag(DDSpanContext.SPAN_SAMPLING_MAX_PER_SECOND_TAG)).isEqualTo(expectedLimit)
        assertThat(span.samplingPriority()).isEqualTo(PrioritySampling.UNSET.toInt())
    }

    @Test
    fun `error priorities should be respected`() {
        val span = tracer.buildSpan(instrumentationName, "testSpan").start()
        assertThat(span.isError()).isFalse

        span.setError(true)
        assertThat(span.isError()).isTrue

        span.setError(false)
        assertThat(span.isError()).isFalse

        span.setError(true, ErrorPriorities.HTTP_SERVER_DECORATOR)
        assertThat(span.isError()).isFalse

        span.setError(true, Byte.MAX_VALUE)
        assertThat(span.isError()).isTrue
    }

    companion object {
        @JvmStatic
        fun originExtractionTests() = arrayOf(
            TagContext("some-origin", mapOf<String, String>()),
            ExtractedContext(
                DDTraceId.ONE,
                2,
                PrioritySampling.SAMPLER_DROP.toInt(),
                "some-origin",
                PropagationTags.factory().empty(),
                TracePropagationStyle.DATADOG
            )
        )

        @JvmStatic
        fun getApplicationRootSpanTests() = arrayOf(
            null,
            ExtractedContext(
                DDTraceId.from(123L),
                456L,
                PrioritySampling.SAMPLER_KEEP.toInt(),
                "789",
                PropagationTags.factory().empty(),
                TracePropagationStyle.DATADOG
            )
        )

        @JvmStatic
        fun isRootSpanTests(): Stream<Arguments> = listOf(
            Arguments.of(null, true),
            Arguments.of(
                ExtractedContext(
                    DDTraceId.from(123L),
                    456L,
                    PrioritySampling.SAMPLER_KEEP.toInt(),
                    "789",
                    PropagationTags.factory().empty(),
                    TracePropagationStyle.DATADOG
                ),
                false
            )
        ).stream()

        @JvmStatic
        fun spanSamplingAttributes(): Stream<Arguments> = listOf(
            Arguments.of(1.0, 10),
            Arguments.of(0.5, 100),
            Arguments.of(0.25, Int.MAX_VALUE)
        ).stream()
    }
}
