/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.utils

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DdTagsUtilsTest {

    @Test
    fun `M build ddtags string W toDdTagsString() { basic context }`(
        @Forgery fakeContext: DatadogContext
    ) {
        // Given - context with empty variant
        val context = fakeContext.copy(variant = "")

        // When
        val result = DdTagsUtils.toDdTagsString(context)

        // Then
        assertThat(result).isEqualTo(
            "${DdTagsUtils.TAG_SERVICE}:${context.service}," +
                "${DdTagsUtils.TAG_VERSION}:${context.version}," +
                "${DdTagsUtils.TAG_SDK_VERSION}:${context.sdkVersion}," +
                "${DdTagsUtils.TAG_ENV}:${context.env}"
        )
    }

    @Test
    fun `M build ddtags string with variant W toDdTagsString() { non-empty variant }`(
        @Forgery fakeContext: DatadogContext,
        @StringForgery(regex = "[a-z]+") fakeVariant: String
    ) {
        // Given - context with non-empty variant
        val context = fakeContext.copy(variant = fakeVariant)
        val expected = "${DdTagsUtils.TAG_SERVICE}:${context.service}," +
            "${DdTagsUtils.TAG_VERSION}:${context.version}," +
            "${DdTagsUtils.TAG_SDK_VERSION}:${context.sdkVersion}," +
            "${DdTagsUtils.TAG_ENV}:${context.env}," +
            "${DdTagsUtils.TAG_VARIANT}:${context.variant}"

        // When
        val result = DdTagsUtils.toDdTagsString(context)

        // Then
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `M build ddtags string without variant W toDdTagsString() { empty variant }`(
        @Forgery fakeContext: DatadogContext
    ) {
        // Given - context with empty variant
        val context = fakeContext.copy(variant = "")

        // When
        val result = DdTagsUtils.toDdTagsString(context)

        // Then
        assertThat(result).doesNotContain(DdTagsUtils.TAG_VARIANT + ":")
    }

    @Test
    fun `M parse ddtags string W toDdTagsMap() { basic string }`(
        @StringForgery fakeService: String,
        @StringForgery fakeVersion: String,
        @StringForgery fakeSdkVersion: String,
        @StringForgery fakeEnv: String
    ) {
        // Given
        val tagsString = "${DdTagsUtils.TAG_SERVICE}:$fakeService," +
            "${DdTagsUtils.TAG_VERSION}:$fakeVersion," +
            "${DdTagsUtils.TAG_SDK_VERSION}:$fakeSdkVersion," +
            "${DdTagsUtils.TAG_ENV}:$fakeEnv"

        // When
        val result = DdTagsUtils.toDdTagsMap(tagsString)

        // Then
        assertThat(result)
            .containsEntry(DdTagsUtils.TAG_SERVICE, fakeService)
            .containsEntry(DdTagsUtils.TAG_VERSION, fakeVersion)
            .containsEntry(DdTagsUtils.TAG_SDK_VERSION, fakeSdkVersion)
            .containsEntry(DdTagsUtils.TAG_ENV, fakeEnv)
            .hasSize(4)
    }

    @Test
    fun `M parse ddtags string with variant W toDdTagsMap() { full string }`(
        @StringForgery fakeService: String,
        @StringForgery fakeVersion: String,
        @StringForgery fakeSdkVersion: String,
        @StringForgery fakeEnv: String,
        @StringForgery fakeVariant: String
    ) {
        // Given
        val tagsString = "${DdTagsUtils.TAG_SERVICE}:$fakeService," +
            "${DdTagsUtils.TAG_VERSION}:$fakeVersion," +
            "${DdTagsUtils.TAG_SDK_VERSION}:$fakeSdkVersion," +
            "${DdTagsUtils.TAG_ENV}:$fakeEnv," +
            "${DdTagsUtils.TAG_VARIANT}:$fakeVariant"

        // When
        val result = DdTagsUtils.toDdTagsMap(tagsString)

        // Then
        assertThat(result)
            .containsEntry(DdTagsUtils.TAG_SERVICE, fakeService)
            .containsEntry(DdTagsUtils.TAG_VERSION, fakeVersion)
            .containsEntry(DdTagsUtils.TAG_SDK_VERSION, fakeSdkVersion)
            .containsEntry(DdTagsUtils.TAG_ENV, fakeEnv)
            .containsEntry(DdTagsUtils.TAG_VARIANT, fakeVariant)
            .hasSize(5)
    }

    @Test
    fun `M preserve colons in value W toDdTagsMap() { value contains separator }`() {
        // Given
        val key = "key"
        val value = "value:with:colons"
        val tagsString = "$key:$value"

        // When
        val result = DdTagsUtils.toDdTagsMap(tagsString)

        // Then
        assertThat(result).isEqualTo(mapOf(key to value))
    }

    @Test
    fun `M preserve url value W toDdTagsMap() { value contains http url }`() {
        // Given
        val key = "url"
        val value = "https://example.com/path?a=b"
        val tagsString = "$key:$value"

        // When
        val result = DdTagsUtils.toDdTagsMap(tagsString)

        // Then
        assertThat(result).isEqualTo(mapOf(key to value))
    }

    @Test
    fun `M return null W toDdTagsMap() { null input }`() {
        // When
        val result = DdTagsUtils.toDdTagsMap(null)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W toDdTagsMap() { empty string }`() {
        // When
        val result = DdTagsUtils.toDdTagsMap("")

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W toDdTagsMap() { key without value }`() {
        // When
        val result = DdTagsUtils.toDdTagsMap("key:")

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return null W toDdTagsMap() { value without key }`() {
        // When
        val result = DdTagsUtils.toDdTagsMap(":value")

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return map W toDdTagsMap() { correct key-value }`() {
        // When
        val result = DdTagsUtils.toDdTagsMap("key:value")

        // Then
        assertThat(result).isEqualTo(mapOf("key" to "value"))
    }

    @Test
    fun `M preserve leading colon in value W toDdTagsMap() { multiple colons in value }`() {
        // When
        val result = DdTagsUtils.toDdTagsMap("key::value")

        // Then
        assertThat(result).isEqualTo(mapOf("key" to ":value"))
    }

    @Test
    fun `M round trip W toDdTagsString and toDdTagsMap() { context with variant }`(
        @Forgery fakeContext: DatadogContext
    ) {
        // When
        val tagsString = DdTagsUtils.toDdTagsString(fakeContext)
        val parsed = DdTagsUtils.toDdTagsMap(tagsString)

        // Then
        assertThat(parsed)
            .containsEntry(DdTagsUtils.TAG_SERVICE, fakeContext.service)
            .containsEntry(DdTagsUtils.TAG_VERSION, fakeContext.version)
            .containsEntry(DdTagsUtils.TAG_SDK_VERSION, fakeContext.sdkVersion)
            .containsEntry(DdTagsUtils.TAG_ENV, fakeContext.env)
            .containsEntry(DdTagsUtils.TAG_VARIANT, fakeContext.variant)
    }

    @Test
    fun `M serialize map W toDdTagsString() { simple map }`(
        @StringForgery fakeService: String,
        @StringForgery fakeVersion: String
    ) {
        // Given
        val map = mapOf(
            DdTagsUtils.TAG_SERVICE to fakeService,
            DdTagsUtils.TAG_VERSION to fakeVersion
        )

        // When
        val result = DdTagsUtils.toDdTagsString(map)

        // Then
        assertThat(result).isEqualTo("${DdTagsUtils.TAG_SERVICE}:$fakeService,${DdTagsUtils.TAG_VERSION}:$fakeVersion")
    }

    @Test
    fun `M serialize empty map W toDdTagsString() { empty map }`() {
        // When
        val result = DdTagsUtils.toDdTagsString(emptyMap())

        // Then
        assertThat(result).isEmpty()
    }

    @Test
    fun `M produce same string W toDdTagsMap from context and toDdTagsString from context { basic }`(
        @Forgery fakeContext: DatadogContext
    ) {
        // Given - context with empty variant
        val context = fakeContext.copy(variant = "")

        // When
        val fromMap = DdTagsUtils.toDdTagsString(DdTagsUtils.toDdTagsMap(context))
        val fromString = DdTagsUtils.toDdTagsString(context)

        // Then
        assertThat(fromMap).isEqualTo(fromString)
    }

    @Test
    fun `M produce same string W toDdTagsMap from context and toDdTagsString from context { with variant }`(
        @Forgery fakeContext: DatadogContext,
        @StringForgery(regex = "[a-z]+") fakeVariant: String
    ) {
        // Given - context with non-empty variant
        val context = fakeContext.copy(variant = fakeVariant)

        // When
        val fromMap = DdTagsUtils.toDdTagsString(DdTagsUtils.toDdTagsMap(context))
        val fromString = DdTagsUtils.toDdTagsString(context)

        // Then
        assertThat(fromMap).isEqualTo(fromString)
    }
}
