/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.trace.core

import com.datadog.tools.unit.callStaticMethod
import com.datadog.tools.unit.createInstance
import com.datadog.trace.api.Config
import com.datadog.trace.api.config.GeneralConfig
import com.datadog.trace.api.config.TracerConfig
import com.datadog.trace.api.sampling.PrioritySampling
import com.datadog.trace.bootstrap.config.provider.ConfigProvider
import com.datadog.trace.common.sampling.AllSampler
import com.datadog.trace.common.sampling.RateByServiceTraceSampler
import com.datadog.trace.common.writer.ListWriter
import com.datadog.trace.common.writer.NoOpWriter
import com.datadog.trace.common.writer.Writer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.mock
import java.util.Properties
import java.util.stream.Stream

internal class CoreTracerTest : DDCoreSpecification() {
    @Test
    fun `verify defaults on tracer`() {
        // When
        val tracer = CoreTracer.builder().build()

        // Then
        assertThat(tracer.serviceName).isNotEmpty
        assertThat(tracer.initialSampler).isInstanceOf(RateByServiceTraceSampler::class.java)
        assertThat(tracer.writer).isInstanceOf(NoOpWriter::class.java)

        // Tear down
        tracer.close()
    }

    @ParameterizedTest
    @MethodSource("serviceEnvVersionTags")
    fun `verify service, env, and version are added as stats tags`(service: String?, env: String?, version: String?) {
        // Given
        var expectedSize = 6
        val properties = Properties()
        service?.let { properties.setProperty(GeneralConfig.SERVICE_NAME, it) }
        env?.let {
            properties.setProperty(GeneralConfig.ENV, it)
            expectedSize += 1
        }
        version?.let {
            properties.setProperty(GeneralConfig.VERSION, it)
            expectedSize += 1
        }
        val config = createInstance(
            Config::class.java,
            ConfigProvider.withPropertiesOverride(properties)
        )

        // When
        val constantTags: Array<String> =
            CoreTracer::class.java.callStaticMethod("generateConstantTags", config)

        // Then
        assertThat(constantTags.size).isEqualTo(expectedSize)
        when (service) {
            null -> assertThat(constantTags).anyMatch { it.startsWith("service:") }
            else -> assertThat(constantTags).contains("service:$service")
        }
        env?.let { assertThat(constantTags).contains("env:$env") }
        version?.let { assertThat(constantTags).contains("version:$version") }
    }

    @Test
    fun `verify overriding sampler`() {
        // Given
        val properties = Properties().apply { setProperty(TracerConfig.PRIORITY_SAMPLING, "false") }

        // When
        val tracer = tracerBuilder().withProperties(properties).build()

        // Then
        assertThat(tracer.initialSampler).isInstanceOf(AllSampler::class.java)

        // Tear down
        tracer.close()
    }

    @Test
    fun `verify overriding writer`() {
        // Given
        val mockWriter: Writer = mock()

        // When
        val tracer = tracerBuilder().writer(mockWriter).build()

        // Then
        assertThat(tracer.writer).isSameAs(mockWriter)

        // Tear down
        tracer.close()
    }

    @ParameterizedTest
    @MethodSource("baggageMapping")
    fun `verify baggage mapping configs on tracer`(baggageItem: String, expectedMapping: Map<String, String>) {
        // Given
        val properties = Properties().apply {
            setProperty(TracerConfig.SERVICE_MAPPING, baggageItem)
            setProperty(TracerConfig.SPAN_TAGS, baggageItem)
            setProperty(TracerConfig.HEADER_TAGS, baggageItem)
        }

        // When
        val config = createInstance(
            Config::class.java,
            ConfigProvider.withPropertiesOverride(properties)
        )
        val tracer = tracerBuilder().config(config).build()

        // Then
        assertThat(expectedMapping).isEqualTo(tracer.captureTraceConfig().serviceMapping)
    }

    @ParameterizedTest
    @MethodSource("baggageMapping")
    fun `verify mapping configs on tracer`(baggageItem: String, expectedMapping: Map<String, String>) {
        // Given
        val properties = Properties().apply {
            setProperty(TracerConfig.BAGGAGE_MAPPING, baggageItem)
        }
        val config = createInstance(
            Config::class.java,
            ConfigProvider.withPropertiesOverride(properties)
        )

        // When
        val tracer = tracerBuilder().config(config).build()

        // Then
        assertThat(expectedMapping).isEqualTo(tracer.captureTraceConfig().baggageMapping)
    }

    @Test
    fun `root tags are applied only to root spans`() {
        // Given
        val tracer = tracerBuilder().localRootSpanTags(mapOf("only_root" to "value")).build()
        val root = tracer.buildSpan(instrumentationName, "my_root").start()

        // When
        val child = tracer.buildSpan(instrumentationName, "my_child").asChildOf(root.context()).start()

        // Then
        assertThat((root.context() as DDSpanContext).tags).containsKey("only_root")
        assertThat((child.context() as DDSpanContext).tags).doesNotContainKey("only_root")

        // Tear down
        child.finish()
        root.finish()
        tracer.close()
    }

    @Test
    fun `priority sampling when span finishes`() {
        // Given
        val writer = ListWriter()
        val tracer = tracerBuilder().writer(writer).build()

        // When
        val span = tracer.buildSpan(instrumentationName, "operation").start()
        span.finish()
        writer.waitForTraces(1)

        // Then
        assertThat(span.samplingPriority).isEqualTo(PrioritySampling.SAMPLER_KEEP.toInt())

        // Tear down
        tracer.close()
    }

    @Test
    fun `priority sampling set when child span complete`() {
        // Given
        val writer = ListWriter()
        val tracer = tracerBuilder().writer(writer).build()

        // When
        val root = tracer.buildSpan(instrumentationName, "operation").start()
        val child = tracer.buildSpan(instrumentationName, "my_child").asChildOf(root.context()).start()
        root.finish()

        // Then
        assertThat(child.samplingPriority).isNull()

        // Tear down
        child.finish()
        writer.waitForTraces(1)

        // Then
        assertThat(root.samplingPriority).isEqualTo(PrioritySampling.SAMPLER_KEEP.toInt())
        assertThat(root.samplingPriority).isEqualTo(child.samplingPriority)

        // Tear down
        tracer.close()
    }

    companion object {
        @JvmStatic
        fun serviceEnvVersionTags(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(null, null, null),
                Arguments.of("testService", null, null),
                Arguments.of("testService", "staging", null),
                Arguments.of("testService", null, "1"),
                Arguments.of("testService", "staging", "1"),
                Arguments.of(null, "staging", null),
                Arguments.of(null, "staging", "1"),
                Arguments.of(null, null, "1")
            )
        }

        @JvmStatic
        fun baggageMapping(): Stream<Arguments> {
            return Stream.of(
                Arguments.of("a:one, a:two, a:three", mapOf("a" to "three")),
                Arguments.of("a:b,c:d,e:", mapOf("a" to "b", "c" to "d"))
            )
        }
    }
}
