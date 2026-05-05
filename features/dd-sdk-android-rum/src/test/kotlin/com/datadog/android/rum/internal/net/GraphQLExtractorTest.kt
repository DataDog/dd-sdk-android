/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.net

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.instrumentation.network.HttpRequestInfo
import com.datadog.android.internal.network.GraphQLHeaders
import com.datadog.android.internal.utils.toBase64
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.internal.net.GraphQLExtractor.Companion.JSON_CONTENT_TYPES
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.android.tests.elmyr.anHttpRequestInfo
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
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
internal class GraphQLExtractorTest {

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    lateinit var testedExtractor: GraphQLExtractor

    @BeforeEach
    fun `set up`() {
        testedExtractor = GraphQLExtractor()
    }

    // region extractGraphQLAttributes

    @Test
    fun `M return all attributes W extractGraphQLAttributes() {all headers present}`(
        forge: Forge,
        @StringForgery fakeGraphQLName: String,
        @StringForgery fakeGraphQLType: String,
        @StringForgery fakeGraphQLVariables: String,
        @StringForgery fakeGraphQLPayload: String
    ) {
        // Given
        val requestInfo = forge.anHttpRequestInfo(
            mapOf(
                GraphQLHeaders.DD_GRAPHQL_NAME_HEADER.headerValue to fakeGraphQLName.toBase64(),
                GraphQLHeaders.DD_GRAPHQL_TYPE_HEADER.headerValue to fakeGraphQLType.toBase64(),
                GraphQLHeaders.DD_GRAPHQL_VARIABLES_HEADER.headerValue to fakeGraphQLVariables.toBase64(),
                GraphQLHeaders.DD_GRAPHQL_PAYLOAD_HEADER.headerValue to fakeGraphQLPayload.toBase64()
            )
        )

        // When
        val result = testedExtractor.extractGraphQLAttributes(requestInfo)

        // Then
        assertThat(result).containsEntry(RumAttributes.GRAPHQL_OPERATION_NAME, fakeGraphQLName)
        assertThat(result).containsEntry(RumAttributes.GRAPHQL_OPERATION_TYPE, fakeGraphQLType)
        assertThat(result).containsEntry(RumAttributes.GRAPHQL_VARIABLES, fakeGraphQLVariables)
        assertThat(result).containsEntry(RumAttributes.GRAPHQL_PAYLOAD, fakeGraphQLPayload)
        assertThat(result).hasSize(4)
    }

    @Test
    fun `M return empty map W extractGraphQLAttributes() {no headers}`(@Forgery fakeRequestInfo: HttpRequestInfo) {
        // When
        val result = testedExtractor.extractGraphQLAttributes(fakeRequestInfo)

        // Then
        assertThat(result).isEmpty()
    }

    @Test
    fun `M return null values W extractGraphQLAttributes() {invalid base64}`(forge: Forge) {
        // Given
        val requestInfo = forge.anHttpRequestInfo(
            mapOf(
                GraphQLHeaders.DD_GRAPHQL_NAME_HEADER.headerValue to "!!!invalid-base64!!!"
            )
        )

        // When
        val result = testedExtractor.extractGraphQLAttributes(requestInfo)

        // Then
        assertThat(result).containsEntry(RumAttributes.GRAPHQL_OPERATION_NAME, null)
        assertThat(result).hasSize(1)
    }

    // endregion

    // region extractGraphQLErrors

    @Test
    fun `M return errors JSON W extractGraphQLErrors() {response with errors}`(forge: Forge) {
        // Given
        val body =
            """{"data":null,"errors":[{"message":"Something went wrong","locations":[{"line":1,"column":1}]}]}"""

        // When
        val result = testedExtractor.extractGraphQLErrors(
            forge.anElementFrom(JSON_CONTENT_TYPES),
            body,
            mockInternalLogger
        )

        // Then
        assertThat(result).isNotNull
        assertThat(result).contains("Something went wrong")
    }

    @Test
    fun `M return null W extractGraphQLErrors() {empty errors array}`(forge: Forge) {
        // Given
        val body = """{"data":{"user":{"name":"John"}},"errors":[]}"""

        // When
        val result = testedExtractor.extractGraphQLErrors(
            forge.anElementFrom(JSON_CONTENT_TYPES),
            body,
            mockInternalLogger
        )

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W extractGraphQLErrors() {no errors key}`(forge: Forge) {
        // Given
        val body = """{"data":{"user":{"name":"John"}}}"""

        // When
        val result = testedExtractor.extractGraphQLErrors(
            forge.anElementFrom(JSON_CONTENT_TYPES),
            body,
            mockInternalLogger
        )

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W extractGraphQLErrors() {non-JSON content type}`(@StringForgery fakeContentType: String) {
        // Given
        val body = """{"errors":[{"message":"err"}]}"""

        // When
        val result = testedExtractor.extractGraphQLErrors(fakeContentType, body, mockInternalLogger)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M normalize extensions code W extractGraphQLErrors() {extensions code without top-level code}`(forge: Forge) {
        // Given
        val body = """{"errors":[{"message":"Unauthorized","extensions":{"code":"UNAUTHENTICATED"}}]}"""

        // When
        val result = testedExtractor.extractGraphQLErrors(
            forge.anElementFrom(JSON_CONTENT_TYPES),
            body,
            mockInternalLogger
        )

        // Then
        assertThat(result).isNotNull
        assertThat(result).contains("\"code\"")
        assertThat(result).contains("UNAUTHENTICATED")
    }

    @Test
    fun `M preserve top-level code W extractGraphQLErrors() {both code and extensions code}`(forge: Forge) {
        // Given
        val body = """{"errors":[{"message":"err","code":"TOP_LEVEL","extensions":{"code":"EXT_CODE"}}]}"""

        // When
        val result = testedExtractor.extractGraphQLErrors(
            forge.anElementFrom(JSON_CONTENT_TYPES),
            body,
            mockInternalLogger
        )

        // Then
        assertThat(result).isNotNull
        assertThat(result).contains("TOP_LEVEL")
    }

    @Test
    fun `M return null W extractGraphQLErrors() {invalid JSON}`(forge: Forge) {
        // Given
        val body = forge.aString()

        // When
        val result = testedExtractor.extractGraphQLErrors(
            forge.anElementFrom(JSON_CONTENT_TYPES),
            body,
            mockInternalLogger
        )

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W extractGraphQLErrors() {null body}`(forge: Forge) {
        // When
        val result = testedExtractor.extractGraphQLErrors(
            forge.anElementFrom(JSON_CONTENT_TYPES),
            null,
            mockInternalLogger
        )

        // Then
        assertThat(result).isNull()
    }

    // endregion
}
