/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.utils

import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.trace.AndroidTracer
import com.datadog.android.utils.forge.Configurator
import com.datadog.opentracing.DDSpan
import com.datadog.opentracing.scopemanager.ScopeTestHelper
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
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
internal class TracerExtensionsTest {

    lateinit var tracer: AndroidTracer

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @BeforeEach
    fun `set up`(forge: Forge) {
        whenever(mockSdkCore.internalLogger) doReturn mock()

        tracer = AndroidTracer.Builder(mockSdkCore)
            .setService(forge.anAlphaNumericalString())
            .build()
    }

    @AfterEach
    fun `tear down`() {
        val activeSpan = tracer.activeSpan()

        @Suppress("DEPRECATION")
        val activeScope = tracer.scopeManager().active()
        activeSpan?.finish()
        activeScope?.close()

        ScopeTestHelper.removeThreadLocalScope()
    }

    @Test
    fun `it will return the trace id and span id if there is an active span`(forge: Forge) {
        // When
        val span = tracer.buildSpan(forge.anAlphabeticalString()).start() as DDSpan
        tracer.activateSpan(span)

        // Then
        assertThat(tracer.traceId()).isEqualTo(span.traceId.toString())
        assertThat(tracer.spanId()).isEqualTo(span.spanId.toString())
    }

    @Test
    fun `it will return null for trace and span id if there is no active span`() {
        // Then
        assertThat(tracer.traceId()).isNull()
        assertThat(tracer.spanId()).isNull()
    }
}
