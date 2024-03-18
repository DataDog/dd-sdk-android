/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.trace.core

import com.datadog.tools.unit.getFieldValue
import com.datadog.trace.api.Config
import com.datadog.trace.api.DDSpanId
import com.datadog.trace.api.DDTags
import com.datadog.trace.api.DDTraceId
import com.datadog.trace.api.TracePropagationStyle
import com.datadog.trace.api.gateway.RequestContextSlot
import com.datadog.trace.api.naming.SpanNaming
import com.datadog.trace.api.sampling.PrioritySampling
import com.datadog.trace.bootstrap.instrumentation.api.AgentTracer
import com.datadog.trace.bootstrap.instrumentation.api.AgentTracer.NoopPathwayContext
import com.datadog.trace.bootstrap.instrumentation.api.TagContext
import com.datadog.trace.common.writer.ListWriter
import com.datadog.trace.core.propagation.ExtractedContext
import com.datadog.trace.core.propagation.PropagationTags
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.concurrent.TimeUnit
import java.util.stream.Stream

@Timeout(5)
internal class CoreSpanBuilderTest : DDCoreSpecification() {

    lateinit var writer: ListWriter
    lateinit var tracer: CoreTracer

    @BeforeEach
    override fun setup() {
        writer = ListWriter()
        tracer = tracerBuilder().writer(writer).build()
    }

    @AfterEach
    override fun cleanup() {
        tracer.close()
    }

    @Test
    fun `build simple span`() {
        val span = tracer.buildSpan(instrumentationName, "op name").withServiceName("foo").start()
        assertThat(span.operationName).isEqualTo("op name")
    }

    @Test
    fun `build complex span`() {
        val expectedName = "fakeName"
        val tags = mapOf(
            "1" to true,
            "2" to "fakeString",
            "3" to 42.0
        )

        var builder = tracer
            .buildSpan(instrumentationName, expectedName)
            .withServiceName("foo")
        tags.forEach {
            builder = builder.withTag(it.key, it.value)
        }

        var span = builder.start()

        assertThat(span.operationName).isEqualTo(expectedName)
        assertThat(span.tags).containsAllEntriesOf(tags)

        span = tracer.buildSpan(instrumentationName, expectedName).withServiceName("foo").start()

        assertThat(span.tags).isEqualTo(
            mapOf(
                DDTags.THREAD_NAME to Thread.currentThread().name,
                DDTags.THREAD_ID to Thread.currentThread().id,
                DDTags.RUNTIME_ID_TAG to Config.get().runtimeId,
                DDTags.LANGUAGE_TAG_KEY to DDTags.LANGUAGE_TAG_VALUE,
                DDTags.PID_TAG to Config.get().processId,
                DDTags.SCHEMA_VERSION_TAG_KEY to SpanNaming.instance().version(),
                DDTags.PROFILING_ENABLED to if (Config.get().isProfilingEnabled()) 1 else 0
            )
        )

        // with all custom fields provided
        val expectedResource = "fakeResource"
        val expectedService = "fakeService"
        val expectedType = "fakeType"

        span = tracer
            .buildSpan(instrumentationName, expectedName)
            .withServiceName("foo")
            .withResourceName(expectedResource)
            .withServiceName(expectedService)
            .withErrorFlag()
            .withSpanType(expectedType)
            .start()

        val context = span.context() as DDSpanContext

        assertThat(context.resourceName).isEqualTo(expectedResource)
        assertThat(context.errorFlag).isTrue
        assertThat(context.serviceName).isEqualTo(expectedService)
        assertThat(context.spanType).isEqualTo(expectedType)

        assertThat(context.getTag(DDTags.THREAD_NAME)).isEqualTo(Thread.currentThread().name)
        assertThat(context.getTag(DDTags.THREAD_ID)).isEqualTo(Thread.currentThread().id)
    }

    @Test
    fun `setting name should remove`() {
        val nameValues = listOf(
            Pair("null.tag", null),
            Pair("empty.tag", "")
        )

        for ((name, value) in nameValues) {
            val span = tracer.buildSpan(instrumentationName, "op name")
                .withTag(name, "tag value")
                .withTag(name, value)
                .start()

            assertThat(span.tags[name]).isNull()

            span.setTag(name, "a tag")

            assertThat(span.tags[name]).isEqualTo("a tag")

            span.setTag(name, value)

            assertThat(span.tags[name]).isNull()
        }
    }

    @Test
    fun `should build span timestamp in nano`() {
        // time in micro
        val expectedTimestamp = 487517802L * 1000 * 1000L
        val expectedName = "fakeName"

        var span = tracer
            .buildSpan(instrumentationName, expectedName)
            .withServiceName("foo")
            .withStartTimestamp(expectedTimestamp)
            .start()

        // get return nano time
        assertThat(span.startTime).isEqualTo(expectedTimestamp * 1000L)

        // auto-timestamp in nanoseconds
        val start = System.currentTimeMillis()
        span = tracer.buildSpan(instrumentationName, expectedName).withServiceName("foo").start()
        val stop = System.currentTimeMillis()

        // Give a range of +/- 5 millis
        assertThat(span.startTime).isGreaterThanOrEqualTo(TimeUnit.MILLISECONDS.toNanos(start - 1))
        assertThat(span.startTime).isLessThanOrEqualTo(TimeUnit.MILLISECONDS.toNanos(stop + 1))
    }

    @Test
    fun `should link to parent span`() {
        val spanId = 1L
        val traceId = DDTraceId.ONE
        val expectedParentId = spanId

        val mockedContext = mock<DDSpanContext> {
            on { getTraceId() } doReturn traceId
            on { getSpanId() } doReturn spanId
            on { serviceName } doReturn "foo"
            on { baggageItems } doReturn emptyMap()
            on { trace } doReturn tracer.pendingTraceFactory().create(DDTraceId.ONE)
            on { pathwayContext } doReturn NoopPathwayContext.INSTANCE
        }

        val expectedName = "fakeName"

        val span = tracer
            .buildSpan(instrumentationName, expectedName)
            .withServiceName("foo")
            .asChildOf(mockedContext)
            .start()

        val actualContext = span.context() as DDSpanContext

        assertThat(actualContext.parentId).isEqualTo(expectedParentId)
        assertThat(actualContext.traceId).isEqualTo(traceId)
    }

    @ParameterizedTest
    @CsvSource(
        "false, service, false",
        "true, service, true",
        "false, another service, true",
        "true, another service, true"
    )
    fun `should link to parent span implicitly`(noopParent: Boolean, serviceName: String, expectTopLevel: Boolean) {
        val parent = tracer.activateSpan(
            if (noopParent) {
            AgentTracer.NoopAgentSpan.INSTANCE
            } else {
            tracer.buildSpan(instrumentationName, "parent")
                .withServiceName("service").start()
            }
        )

        val expectedParentId = if (noopParent) DDSpanId.ZERO else parent.span().context().spanId

        val expectedName = "fakeName"

        val span = tracer
            .buildSpan(instrumentationName, expectedName)
            .withServiceName(serviceName)
            .start() as DDSpan

        val actualContext = span.context()

        assertThat(actualContext.parentId).isEqualTo(expectedParentId)
        assertThat(span.isTopLevel).isEqualTo(expectTopLevel)

        parent.close()
    }

    @Test
    fun `should inherit the DD parent attributes`() {
        val expectedName = "fakeName"
        val expectedParentServiceName = "fakeServiceName"
        val expectedParentResourceName = "fakeResourceName"
        val expectedParentType = "fakeType"
        val expectedChildServiceName = "fakeServiceName-child"
        val expectedChildResourceName = "fakeResourceName-child"
        val expectedChildType = "fakeType-child"
        val expectedBaggageItemKey = "fakeKey"
        val expectedBaggageItemValue = "fakeValue"

        val parent = tracer
            .buildSpan(instrumentationName, expectedName)
            .withServiceName("foo")
            .withResourceName(expectedParentResourceName)
            .withSpanType(expectedParentType)
            .start()

        parent.setBaggageItem(expectedBaggageItemKey, expectedBaggageItemValue)

        // ServiceName and SpanType are always set by the parent if they are not present in the child
        var span = tracer
            .buildSpan(instrumentationName, expectedName)
            .withServiceName(expectedParentServiceName)
            .asChildOf(parent.context())
            .start() as DDSpan

        assertThat(span.operationName).isEqualTo(expectedName)
        assertThat(span.getBaggageItem(expectedBaggageItemKey)).isEqualTo(expectedBaggageItemValue)
        assertThat(span.context().serviceName).isEqualTo(expectedParentServiceName)
        assertThat(span.context().resourceName).isEqualTo(expectedName)
        assertThat(span.context().spanType).isNull()
        assertThat(span.isTopLevel).isTrue() // service names differ between parent and child

        // ServiceName and SpanType are always overwritten by the child if they are present
        span = tracer
            .buildSpan(instrumentationName, expectedName)
            .withServiceName(expectedChildServiceName)
            .withResourceName(expectedChildResourceName)
            .withSpanType(expectedChildType)
            .asChildOf(parent.context())
            .start() as DDSpan

        assertThat(span.operationName).isEqualTo(expectedName)
        assertThat(span.getBaggageItem(expectedBaggageItemKey)).isEqualTo(expectedBaggageItemValue)
        assertThat(span.context().serviceName).isEqualTo(expectedChildServiceName)
        assertThat(span.context().resourceName).isEqualTo(expectedChildResourceName)
        assertThat(span.context().spanType).isEqualTo(expectedChildType)
    }

    @Test
    fun `should track all spans in trace`() {
        val spans = mutableListOf<DDSpan>()
        val nbSamples = 10

        // root (aka spans[0]) is the parent
        // others are just for fun

        val root = tracer.buildSpan(instrumentationName, "fake_O").withServiceName("foo").start()

        var lastSpan = root

        for (i in 1..nbSamples) {
            lastSpan = tracer
                .buildSpan(instrumentationName, "fake_$i")
                .withServiceName("foo")
                .asChildOf(lastSpan.context())
                .start() as DDSpan
            spans.add(lastSpan)
            lastSpan.finish()
        }

        val trace = root.context().trace as PendingTrace
        assertThat(trace.rootSpan).isEqualTo(root)
        assertThat(trace.size()).isEqualTo(nbSamples)
        assertThat(trace.spans).containsAll(spans)
        assertThat(spans[(Math.random() * nbSamples).toInt()].context().trace.spans).containsAll(spans)
    }

    @ParameterizedTest
    @MethodSource("extractedContextProvider")
    fun `ExtractedContext should populate new span details`(extractedContext: ExtractedContext) {
        // setup:
        val thread = Thread.currentThread()
        val span = tracer.buildSpan(instrumentationName, "op name")
            .asChildOf(extractedContext).start() as DDSpan

        // expect:
        assertThat(span.traceId).isEqualTo(extractedContext.traceId)
        assertThat(span.parentId).isEqualTo(extractedContext.spanId)
        assertThat(span.samplingPriority).isEqualTo(extractedContext.samplingPriority)
        assertThat(span.context().origin).isEqualTo(extractedContext.origin)
        assertThat(span.context().baggageItems).isEqualTo(extractedContext.baggage)
        // check the extracted context has been copied into the span tags. Intercepted tags will be skipped from
        // propagation.
        extractedContext.tags.filterKeys { it != DDTags.ORIGIN_KEY }.forEach { (key, value) ->
            assertThat(span.context().tags[key]).isEqualTo(value)
        }
        assertThat(span.getTag(DDTags.THREAD_ID)).isEqualTo(thread.id)
        assertThat(span.getTag(DDTags.THREAD_NAME)).isEqualTo(thread.name)
        assertThat(span.context().propagationTags.headerValue(PropagationTags.HeaderType.DATADOG)).isEqualTo(
            extractedContext.propagationTags.headerValue(PropagationTags.HeaderType.DATADOG)
        )
    }

    @ParameterizedTest
    @MethodSource("tagContextProvider")
    fun `TagContext should populate default span details`(tagContext: TagContext) {
        // setup:
        val thread = Thread.currentThread()
        val span = tracer.buildSpan(instrumentationName, "op name").asChildOf(tagContext).start() as DDSpan

        // expect:
        assertThat(span.traceId).isNotEqualTo(DDTraceId.ZERO)
        assertThat(span.parentId).isEqualTo(DDSpanId.ZERO)
        val samplingPriority = span.samplingPriority
        assert(samplingPriority == null)
        assertThat(span.context().origin).isEqualTo(tagContext.origin)
        assertThat(span.context().baggageItems).isEqualTo(emptyMap<String, String>())
        assertThat(span.context().tags).containsExactlyInAnyOrderEntriesOf(
            tagContext.tags +
                    mapOf(
                        DDTags.RUNTIME_ID_TAG to Config.get().getRuntimeId(),
                        DDTags.LANGUAGE_TAG_KEY to DDTags.LANGUAGE_TAG_VALUE,
                        DDTags.THREAD_NAME to thread.name,
                        DDTags.THREAD_ID to thread.id,
                        DDTags.PID_TAG to Config.get().processId,
                        DDTags.SCHEMA_VERSION_TAG_KEY to SpanNaming.instance().version(),
                        DDTags.PROFILING_ENABLED to if (Config.get().isProfilingEnabled()) 1 else 0
                    )
        )
    }

    @ParameterizedTest
    @MethodSource("tagStringProvider")
    fun `default span tags populated on each span`(tags: Map<String, String>) {
        // setup:
        val customTracer = tracerBuilder().writer(writer).config(Config.get()).defaultSpanTags(tags).build()
        val span = customTracer.buildSpan(instrumentationName, "op name").withServiceName("foo").start()

        // expect:
        assertThat(span.tags).containsExactlyInAnyOrderEntriesOf(
            tags +
                    mapOf(
                        DDTags.THREAD_NAME to Thread.currentThread().name,
                        DDTags.THREAD_ID to Thread.currentThread().id,
                        DDTags.RUNTIME_ID_TAG to Config.get().getRuntimeId(),
                        DDTags.LANGUAGE_TAG_KEY to DDTags.LANGUAGE_TAG_VALUE,
                        DDTags.PID_TAG to Config.get().getProcessId(),
                        DDTags.SCHEMA_VERSION_TAG_KEY to SpanNaming.instance().version(),
                        DDTags.PROFILING_ENABLED to if (Config.get().isProfilingEnabled()) 1 else 0
                    )
        )

        // cleanup:
        customTracer.close()
    }

    @Test
    fun `can overwrite RequestContext data with builder from empty`() {
        // when:
        val span1 = tracer.startSpan("test", "span1")

        // then:
        val appSecContext1 = span1.getRequestContext().getData<Any>(RequestContextSlot.APPSEC)
        assertThat(appSecContext1).isNull()
        val ciVisibilityContext1 = span1.getRequestContext().getData<Any>(RequestContextSlot.CI_VISIBILITY)
        assertThat(ciVisibilityContext1).isNull()
        val iastContext1 = span1.getRequestContext().getData<Any>(RequestContextSlot.IAST)
        assertThat(iastContext1).isNull()

        // when:
        val span2 = tracer.buildSpan(instrumentationName, "span2")
            .asChildOf(span1.context())
            .withRequestContextData(RequestContextSlot.APPSEC, "override")
            .withRequestContextData(RequestContextSlot.CI_VISIBILITY, "override")
            .withRequestContextData(RequestContextSlot.IAST, "override")
            .start()

        // then:
        val appSecContext2 = span2.getRequestContext().getData<Any>(RequestContextSlot.APPSEC)
        assertThat(appSecContext2).isEqualTo("override")
        val ciVisibilityContext2 = span2.getRequestContext().getData<Any>(RequestContextSlot.CI_VISIBILITY)
        assertThat(ciVisibilityContext2).isEqualTo("override")
        val iastContext2 = span2.getRequestContext().getData<Any>(RequestContextSlot.IAST)
        assertThat(iastContext2).isEqualTo("override")

        // cleanup:
        span2.finish()
        span1.finish()
    }

    @Test
    fun `can overwrite RequestContext data with builder`() {
        // setup:
        val context = TagContext()
            .withCiVisibilityContextData("value")
            .withRequestContextDataIast("value")
            .withRequestContextDataAppSec("value")
        val span1 = tracer.buildSpan(instrumentationName, "span1").asChildOf(context).start()

        // when:
        val span2 = tracer.buildSpan(instrumentationName, "span2").asChildOf(span1.context()).start()

        // then:
        val appSecContext1 = span2.getRequestContext().getData<Any>(RequestContextSlot.APPSEC)
        assertThat(appSecContext1).isEqualTo("value")
        val ciVisibilityContext1 = span2.getRequestContext().getData<Any>(RequestContextSlot.CI_VISIBILITY)
        assertThat(ciVisibilityContext1).isEqualTo("value")
        val iastContext1 = span2.getRequestContext().getData<Any>(RequestContextSlot.IAST)
        assertThat(iastContext1).isEqualTo("value")

        // when:
        val span3 = tracer.buildSpan(instrumentationName, "span3")
            .asChildOf(span2.context())
            .withRequestContextData(RequestContextSlot.APPSEC, "override")
            .withRequestContextData(RequestContextSlot.CI_VISIBILITY, "override")
            .withRequestContextData(RequestContextSlot.IAST, "override")
            .start()

        // then:
        val appSecContext2 = span3.getRequestContext().getData<Any>(RequestContextSlot.APPSEC)
        assertThat(appSecContext2).isEqualTo("override")
        val ciVisibilityContext2 = span3.getRequestContext().getData<Any>(RequestContextSlot.CI_VISIBILITY)
        assertThat(ciVisibilityContext2).isEqualTo("override")
        val iastContext2 = span3.getRequestContext().getData<Any>(RequestContextSlot.IAST)
        assertThat(iastContext2).isEqualTo("override")

        // cleanup:
        span3.finish()
        span2.finish()
        span1.finish()
    }

    private fun CoreTracer.pendingTraceFactory(): PendingTrace.Factory {
        return this.getFieldValue("pendingTraceFactory")
    }

    companion object {
        @JvmStatic
        fun extractedContextProvider(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(
                    ExtractedContext(
                        DDTraceId.ONE,
                        2,
                        PrioritySampling.SAMPLER_DROP.toInt(),
                        null,
                        0,
                        emptyMap(),
                        emptyMap(),
                        null,
                        PropagationTags.factory().fromHeaderValue(
                            PropagationTags.HeaderType.DATADOG,
                            "_dd.p.dm=934086a686-4,_dd.p.anytag=value"
                        ),
                        null,
                        TracePropagationStyle.DATADOG
                    )
                ),
                Arguments.of(
                    ExtractedContext(
                        DDTraceId.from(3),
                        4,
                        PrioritySampling.SAMPLER_KEEP.toInt(),
                        "some-origin",
                        0,
                        mapOf("asdf" to "qwer"),
                        mapOf(DDTags.ORIGIN_KEY to "some-origin", "zxcv" to "1234"),
                        null,
                        PropagationTags.factory().empty(),
                        null,
                        TracePropagationStyle.DATADOG
                    )
                )
            )
        }

        @JvmStatic
        fun tagContextProvider(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(TagContext(null, emptyMap())),
                Arguments.of(TagContext("some-origin", mapOf("asdf" to "qwer")))
            )
        }

        @JvmStatic
        fun tagStringProvider(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(emptyMap<String, String>()),
                Arguments.of(mapOf("is" to "val:id")),
                Arguments.of(mapOf("a" to "x")),
                Arguments.of(mapOf("a" to "c")),
                Arguments.of(mapOf("a" to "1", "b-c" to "d"))
            )
        }
    }
}
