/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal

import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class FeatureSdkCoreExtTest {

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @StringForgery
    lateinit var fakeTraceId: String

    @StringForgery
    lateinit var fakeSpanId: String

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `M add the active trace to trace context W addActiveTraceToContext() `() {
        // Given
        val expectedThreadName = Thread.currentThread().name
        val activeTraceContextName = "context@$expectedThreadName"

        // When
        mockSdkCore.addActiveTraceToContext(fakeTraceId, fakeSpanId)

        // Then
        val traceContext: MutableMap<String, Any?> = mutableMapOf()
        argumentCaptor<(MutableMap<String, Any?>) -> Unit> {
            verify(mockSdkCore).updateFeatureContext(eq(Feature.TRACING_FEATURE_NAME), eq(true), capture())
            firstValue.invoke(traceContext)
            val activeTraceContext = traceContext[activeTraceContextName] as Map<String, Any>
            assertThat(activeTraceContext).containsEntry("trace_id", fakeTraceId)
            assertThat(activeTraceContext).containsEntry("span_id", fakeSpanId)
        }
    }

    @Test
    fun `M remove the active trace to trace context W removeActiveTraceFromContext() `() {
        // Given
        val expectedThreadName = Thread.currentThread().name
        val activeTraceContextName = "context@$expectedThreadName"
        val fakeTraceContext: MutableMap<String, Any?> = mutableMapOf(
            activeTraceContextName to mapOf(
                "trace_id" to fakeTraceId,
                "span_id" to fakeSpanId
            )
        )

        // When
        mockSdkCore.removeActiveTraceFromContext()

        // Then
        argumentCaptor<(MutableMap<String, Any?>) -> Unit> {
            verify(mockSdkCore).updateFeatureContext(eq(Feature.TRACING_FEATURE_NAME), eq(true), capture())
            firstValue.invoke(fakeTraceContext)
            assertThat(fakeTraceContext).doesNotContainKey(activeTraceContextName)
        }
    }
}
