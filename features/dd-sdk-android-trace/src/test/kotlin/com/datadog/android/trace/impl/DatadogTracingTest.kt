/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.impl

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.trace.InternalCoreWriterProvider
import com.datadog.android.trace.impl.internal.DatadogSpanWriterWrapper
import com.datadog.android.trace.impl.internal.DatadogTracerAdapter
import com.datadog.android.trace.impl.internal.DatadogTracingInternalToolkit.ErrorMessages.DEFAULT_SERVICE_NAME_IS_MISSING_ERROR_MESSAGE
import com.datadog.android.trace.impl.internal.DatadogTracingInternalToolkit.ErrorMessages.WRITER_PROVIDER_INTERFACE_NOT_IMPLEMENTED_ERROR_MESSAGE
import com.datadog.android.trace.utils.verifyLog
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.getFieldValue
import com.datadog.trace.common.writer.NoOpWriter
import com.datadog.trace.common.writer.Writer
import com.datadog.trace.core.CoreTracer
import fr.xgouchet.elmyr.Forge
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
class DatadogTracingTest {

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    interface StubTracingFeature : Feature, InternalCoreWriterProvider

    @Mock
    lateinit var mockTracingFeature: FeatureScope

    @Mock
    lateinit var mockTracingFeatureScope: StubTracingFeature

    lateinit var fakeServiceName: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeServiceName = forge.anAlphabeticalString()
        val writerWrapperMock = mock<DatadogSpanWriterWrapper> {
            on { delegate } doReturn mock()
        }
        whenever(mockSdkCore.service) doReturn fakeServiceName
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockTracingFeature.unwrap<Feature>()) doReturn mockTracingFeatureScope
        whenever(mockSdkCore.getFeature(Feature.TRACING_FEATURE_NAME)) doReturn mockTracingFeature
        whenever(mockTracingFeatureScope.getCoreTracerWriter()) doReturn writerWrapperMock
    }

    @Test
    fun `M use a NoOpCoreTracerWriter W build { TracingFeature not enabled }`() {
        // GIVEN
        whenever(mockSdkCore.getFeature(Feature.TRACING_FEATURE_NAME)) doReturn null

        // WHEN
        val tracer = DatadogTracing.newTracerBuilder(mockSdkCore).build() as DatadogTracerAdapter

        // THEN
        assertThat(tracer).isNotNull
        val coreTracer: CoreTracer = tracer.delegate as CoreTracer
        val writer: Writer = coreTracer.getFieldValue("writer")
        assertThat(writer).isInstanceOf(NoOpWriter::class.java)
    }

    @Test
    fun `M log a maintainer error W build { TracingFeature not implementing InternalCoreTracerWriterProvider }`() {
        // GIVEN

        whenever(mockTracingFeature.unwrap<Feature>()) doReturn mock()

        // WHEN
        val tracer = DatadogTracing.newTracerBuilder(mockSdkCore).build()

        // THEN
        assertThat(tracer).isNotNull
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            WRITER_PROVIDER_INTERFACE_NOT_IMPLEMENTED_ERROR_MESSAGE
        )
    }

    @Test
    fun `M log a user error W build { default service name not available }`() {
        // GIVEN
        whenever(mockSdkCore.service) doReturn ""

        // WHEN
        DatadogTracing.newTracerBuilder(mockSdkCore).build()

        // THEN
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            DEFAULT_SERVICE_NAME_IS_MISSING_ERROR_MESSAGE
        )
    }
}
