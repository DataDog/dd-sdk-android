/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.evaluation

import com.datadog.android.api.InternalLogger
import com.datadog.android.flags.model.ErrorCode
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.utils.verifyLog
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verifyNoInteractions

@ExtendWith(MockitoExtension::class)
internal class AssignmentAuthorizationTest {

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    private lateinit var testedAuthorization: AssignmentAuthorization

    @BeforeEach
    fun `set up`() {
        testedAuthorization = AssignmentAuthorization(mockInternalLogger, Any())
    }

    @Test
    fun `M extract credential and sanitize context W prepare() {valid token}`() {
        // Given
        val token = "header.payload.signature"
        val context = EvaluationContext(
            "subject",
            mapOf("plan" to "premium", AssignmentAuthorization.ATTRIBUTE to token)
        )

        // When
        val request = testedAuthorization.prepare(context)

        // Then
        assertThat(request.token).isEqualTo(token)
        assertThat(request.context.attributes).isEqualTo(mapOf("plan" to "premium"))
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M omit credential without boundary W prepare() {empty token}`() {
        // When
        val request = testedAuthorization.prepare(
            EvaluationContext("subject", mapOf(AssignmentAuthorization.ATTRIBUTE to ""))
        )

        // Then
        assertThat(request.token).isNull()
        assertThat(request.context.attributes).isEmpty()
        assertThat(testedAuthorization.resolutionContext(EvaluationContext("other")).errorCode).isNull()
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M omit credential and log fixed warning W prepare() {invalid token}`() {
        // Given
        val token = "secret token"

        // When
        val request = testedAuthorization.prepare(
            EvaluationContext("subject", mapOf(AssignmentAuthorization.ATTRIBUTE to token))
        )

        // Then
        assertThat(request.token).isNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            AssignmentAuthorization.INVALID_TOKEN_MESSAGE
        )
        assertThat(AssignmentAuthorization.INVALID_TOKEN_MESSAGE).doesNotContain(token)
    }

    @Test
    fun `M withhold another subject immediately W prepare() {valid token}`() {
        // When
        testedAuthorization.prepare(
            EvaluationContext("requested", mapOf(AssignmentAuthorization.ATTRIBUTE to "token"))
        )

        // Then
        val resolution = testedAuthorization.resolutionContext(EvaluationContext("installed"))
        assertThat(resolution.errorCode).isEqualTo(ErrorCode.GENERAL)
        assertThat(resolution.context?.targetingKey).isEqualTo("requested")
    }

    @Test
    fun `M allow same subject while protected W resolutionContext()`() {
        // When
        testedAuthorization.prepare(
            EvaluationContext("subject", mapOf(AssignmentAuthorization.ATTRIBUTE to "token"))
        )

        // Then
        val installed = EvaluationContext("subject", mapOf("version" to "old"))
        val resolution = testedAuthorization.resolutionContext(installed)
        assertThat(resolution.errorCode).isNull()
        assertThat(resolution.context).isEqualTo(installed)
    }

    @Test
    fun `M establish boundary W onAuthorizationRejected() {no token}`() {
        // Given
        val request = testedAuthorization.prepare(EvaluationContext("requested"))

        // When
        testedAuthorization.onAuthorizationRejected(request)

        // Then
        assertThat(testedAuthorization.resolutionContext(EvaluationContext("installed")).errorCode)
            .isEqualTo(ErrorCode.GENERAL)
    }

    @Test
    fun `M recover boundary W onFallbackAccepted() {same subject}`() {
        // Given
        val request = testedAuthorization.prepare(
            EvaluationContext("subject", mapOf(AssignmentAuthorization.ATTRIBUTE to "token", "version" to "new"))
        )
        val installed = EvaluationContext("subject", mapOf("version" to "old"))
        val fallback = testedAuthorization.fallback(request, installed, requireExactContext = false)

        // When
        testedAuthorization.onFallbackAccepted(request)

        // Then
        assertThat(fallback.usable).isTrue()
        assertThat(fallback.authorizationProtected).isTrue()
        assertThat(testedAuthorization.resolutionContext(EvaluationContext("other")).errorCode).isNull()
    }

    @Test
    fun `M retain established boundary W prepare() {token removed}`() {
        // Given
        testedAuthorization.prepare(
            EvaluationContext("first", mapOf(AssignmentAuthorization.ATTRIBUTE to "token"))
        )

        // When
        testedAuthorization.prepare(EvaluationContext("second"))

        // Then
        assertThat(testedAuthorization.resolutionContext(EvaluationContext("first")).errorCode)
            .isEqualTo(ErrorCode.GENERAL)
    }

    @Test
    fun `M recover boundary W onAssignmentsAccepted()`() {
        // Given
        val request = testedAuthorization.prepare(
            EvaluationContext("requested", mapOf(AssignmentAuthorization.ATTRIBUTE to "token"))
        )

        // When
        testedAuthorization.onAssignmentsAccepted(request)

        // Then
        assertThat(testedAuthorization.resolutionContext(EvaluationContext("other")).errorCode).isNull()
    }

    @Test
    fun `M restore established boundary for later request W prepare() {accepted token then token removed}`() {
        // Given
        val accepted = testedAuthorization.prepare(
            EvaluationContext("first", mapOf(AssignmentAuthorization.ATTRIBUTE to "token"))
        )
        testedAuthorization.onAssignmentsAccepted(accepted)

        // When
        testedAuthorization.prepare(EvaluationContext("second"))

        // Then
        assertThat(testedAuthorization.resolutionContext(EvaluationContext("first")).errorCode)
            .isEqualTo(ErrorCode.GENERAL)
    }
}
