/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.openfeature.internal

import com.datadog.android.flags.EvaluationContextCallback
import com.datadog.android.flags.FlagsClient
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.tools.unit.forge.BaseConfigurator
import dev.openfeature.kotlin.sdk.exceptions.OpenFeatureError
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@ForgeConfiguration(BaseConfigurator::class)
internal class FlagsClientExtTest {

    @Mock
    lateinit var mockFlagsClient: FlagsClient

    @Test
    fun `M complete successfully W setEvaluationContextSuspend() {callback onSuccess}`(
        @StringForgery targetingKey: String
    ) = runTest {
        // Given
        val context = EvaluationContext(targetingKey = targetingKey)
        val contextCaptor = argumentCaptor<EvaluationContext>()

        doAnswer { invocation ->
            val callback = invocation.getArgument<EvaluationContextCallback>(1)
            callback.onSuccess()
            Unit
        }.whenever(mockFlagsClient).setEvaluationContext(contextCaptor.capture(), any())

        // When
        mockFlagsClient.setEvaluationContextSuspend(context)

        // Then - verify the correct context was passed
        verify(mockFlagsClient).setEvaluationContext(contextCaptor.capture(), any())
        assertThat(contextCaptor.firstValue).isEqualTo(context)
        assertThat(contextCaptor.firstValue.targetingKey).isEqualTo(targetingKey)
    }

    @Test
    fun `M throw OpenFeatureError GeneralError W setEvaluationContextSuspend() {callback onFailure with message}`(
        @StringForgery targetingKey: String,
        @StringForgery errorMessage: String
    ) {
        // Given
        val context = EvaluationContext(targetingKey = targetingKey)
        val contextCaptor = argumentCaptor<EvaluationContext>()
        val error = RuntimeException(errorMessage)

        doAnswer { invocation ->
            val callback = invocation.getArgument<EvaluationContextCallback>(1)
            callback.onFailure(error)
            Unit
        }.whenever(mockFlagsClient).setEvaluationContext(contextCaptor.capture(), any())

        // When/Then
        assertThatThrownBy {
            runBlocking {
                mockFlagsClient.setEvaluationContextSuspend(context)
            }
        }
            .isInstanceOf(OpenFeatureError.GeneralError::class.java)
            .hasMessage(errorMessage)

        // Verify the correct context was passed
        assertThat(contextCaptor.firstValue).isEqualTo(context)
    }

    @Test
    fun `M throw OpenFeatureError GeneralError W setEvaluationContextSuspend() {callback onFailure without message}`(
        @StringForgery targetingKey: String
    ) {
        // Given
        val context = EvaluationContext(targetingKey = targetingKey)
        val contextCaptor = argumentCaptor<EvaluationContext>()
        val error = RuntimeException(null as String?)

        doAnswer { invocation ->
            val callback = invocation.getArgument<EvaluationContextCallback>(1)
            callback.onFailure(error)
            Unit
        }.whenever(mockFlagsClient).setEvaluationContext(contextCaptor.capture(), any())

        // When/Then
        assertThatThrownBy {
            runBlocking {
                mockFlagsClient.setEvaluationContextSuspend(context)
            }
        }
            .isInstanceOf(OpenFeatureError.GeneralError::class.java)
            .hasMessage("Unknown error: RuntimeException")

        // Verify the correct context was passed
        assertThat(contextCaptor.firstValue).isEqualTo(context)
    }

    @Test
    fun `M throw OpenFeatureError GeneralError with empty message W setEvaluationContextSuspend()`(
        @StringForgery targetingKey: String
    ) {
        // Given
        val context = EvaluationContext(targetingKey = targetingKey)
        val contextCaptor = argumentCaptor<EvaluationContext>()
        val error = RuntimeException(null as String?)

        doAnswer { invocation ->
            val callback = invocation.getArgument<EvaluationContextCallback>(1)
            callback.onFailure(error)
            Unit
        }.whenever(mockFlagsClient).setEvaluationContext(contextCaptor.capture(), any())

        // When/Then
        assertThatThrownBy {
            runBlocking {
                mockFlagsClient.setEvaluationContextSuspend(context)
            }
        }
            .isInstanceOf(OpenFeatureError.GeneralError::class.java)
            .hasMessage("Unknown error: RuntimeException")

        // Verify the correct context was passed
        assertThat(contextCaptor.firstValue).isEqualTo(context)
    }

    @Test
    fun `M throw OpenFeatureError GeneralError W setEvaluationContextSuspend() {custom exception}`(
        @StringForgery targetingKey: String,
        @StringForgery errorMessage: String
    ) {
        // Given
        val context = EvaluationContext(targetingKey = targetingKey)
        val contextCaptor = argumentCaptor<EvaluationContext>()
        val error = IllegalStateException(errorMessage)

        doAnswer { invocation ->
            val callback = invocation.getArgument<EvaluationContextCallback>(1)
            callback.onFailure(error)
            Unit
        }.whenever(mockFlagsClient).setEvaluationContext(contextCaptor.capture(), any())

        // When/Then
        assertThatThrownBy {
            runBlocking {
                mockFlagsClient.setEvaluationContextSuspend(context)
            }
        }
            .isInstanceOf(OpenFeatureError.GeneralError::class.java)
            .hasMessage(errorMessage)

        // Verify the correct context was passed
        assertThat(contextCaptor.firstValue).isEqualTo(context)
    }

    @Test
    fun `M pass correct context W setEvaluationContextSuspend() {with attributes}`(
        @StringForgery targetingKey: String,
        @StringForgery key1: String,
        @StringForgery value1: String,
        @StringForgery key2: String,
        @StringForgery value2: String
    ) = runTest {
        // Given
        val attributes = mapOf(
            key1 to value1,
            key2 to value2
        )
        val context = EvaluationContext(
            targetingKey = targetingKey,
            attributes = attributes
        )
        val contextCaptor = argumentCaptor<EvaluationContext>()

        doAnswer { invocation ->
            val callback = invocation.getArgument<EvaluationContextCallback>(1)
            callback.onSuccess()
            Unit
        }.whenever(mockFlagsClient).setEvaluationContext(contextCaptor.capture(), any())

        // When
        mockFlagsClient.setEvaluationContextSuspend(context)

        // Then - verify the correct context and attributes were passed
        verify(mockFlagsClient).setEvaluationContext(contextCaptor.capture(), any())
        assertThat(contextCaptor.firstValue).isEqualTo(context)
        assertThat(contextCaptor.firstValue.targetingKey).isEqualTo(targetingKey)
        assertThat(contextCaptor.firstValue.attributes).isEqualTo(attributes)
    }
}
