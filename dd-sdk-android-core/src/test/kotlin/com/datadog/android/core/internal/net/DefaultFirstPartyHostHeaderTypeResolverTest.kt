/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.net

import com.datadog.android.trace.TracingHeaderType
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
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
internal class DefaultFirstPartyHostHeaderTypeResolverTest {

    lateinit var testedDetector: DefaultFirstPartyHostHeaderTypeResolver

    lateinit var fakeHosts: Map<String, Set<TracingHeaderType>>

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeHosts = forge.aMap {
            forge.aStringMatching(HOST_REGEX) to
                forge.aList { aValueFrom(TracingHeaderType::class.java) }.toSet()
        }
        testedDetector = DefaultFirstPartyHostHeaderTypeResolver(fakeHosts)
    }

    @Test
    fun `M return false W isFirstParty(HttpUrl) {unknown host}`(
        @StringForgery(regex = "http(s?)") scheme: String,
        @StringForgery(regex = "(/[a-zA-Z0-9_~\\.-]{1,9}){1,4}") path: String,
        forge: Forge
    ) {
        // Given
        var host = forge.aStringMatching(HOST_REGEX)
        while (host in fakeHosts) {
            host = forge.aStringMatching(HOST_REGEX)
        }
        val url = "$scheme://$host$path".toHttpUrl()

        // When
        val result = testedDetector.isFirstPartyUrl(url)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M return true W isFirstParty(HttpUrl) {exact first party host}`(
        @StringForgery(regex = "http(s?)") scheme: String,
        @StringForgery(regex = "(/[a-zA-Z0-9_~\\.-]{1,9}){1,4}") path: String,
        forge: Forge
    ) {
        // Given
        val host = forge.anElementFrom(fakeHosts.keys)
        val url = "$scheme://$host$path".toHttpUrl()

        // When
        val result = testedDetector.isFirstPartyUrl(url)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `M return true W isFirstParty(HttpUrl) {known hosts list was updated}`(
        @StringForgery(regex = "http(s?)") scheme: String,
        @StringForgery(regex = "(/[a-zA-Z0-9_~\\.-]{1,9}){1,4}") path: String,
        forge: Forge
    ) {
        // Given
        val fakeNewAllowedHosts = forge.aList { forge.aStringMatching(HOST_REGEX) }
        testedDetector.addKnownHosts(fakeNewAllowedHosts)
        val host = forge.anElementFrom(fakeNewAllowedHosts)
        val url = "$scheme://$host$path".toHttpUrl()

        // When
        val result = testedDetector.isFirstPartyUrl(url)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `M return true W isFirstParty(HttpUrl) {valid host subdomain}`(
        @StringForgery(regex = "http(s?)") scheme: String,
        @StringForgery(regex = "[a-zA-Z0-9_~-]{1,9}") subdomain: String,
        @StringForgery(regex = "(/[a-zA-Z0-9_~\\.-]{1,9}){1,4}") path: String,
        forge: Forge
    ) {
        // Given
        val host = forge.anElementFrom(fakeHosts.keys)
        val url = "$scheme://$subdomain.$host$path".toHttpUrl()

        // When
        val result = testedDetector.isFirstPartyUrl(url)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `M return false W isFirstParty(HttpUrl) {unknown host postfixed with valid host}`(
        @StringForgery(regex = "http(s?)") scheme: String,
        @StringForgery(regex = "[a-zA-Z0-9_~-]{1,9}") prefix: String,
        @StringForgery(regex = "(/[a-zA-Z0-9_~\\.-]{1,9}){1,4}") path: String,
        forge: Forge
    ) {
        // Given
        val host = forge.anElementFrom(fakeHosts.keys)
        val url = "$scheme://$prefix$host$path".toHttpUrl()

        // When
        val result = testedDetector.isFirstPartyUrl(url)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M return false W isFirstParty(String) {unknown host}`(
        @StringForgery(regex = "http(s?)") scheme: String,
        @StringForgery(regex = "(/[a-zA-Z0-9_~\\.-]{1,9}){1,4}") path: String,
        forge: Forge
    ) {
        // Given
        var host = forge.aStringMatching(HOST_REGEX)
        while (host in fakeHosts.keys) {
            host = forge.aStringMatching(HOST_REGEX)
        }
        val url = "$scheme://$host$path"

        // When
        val result = testedDetector.isFirstPartyUrl(url)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M return true W isFirstParty(String) {exact first party host}`(
        @StringForgery(regex = "http(s?)") scheme: String,
        @StringForgery(regex = "(/[a-zA-Z0-9_~\\.-]{1,9}){1,4}") path: String,
        forge: Forge
    ) {
        // Given
        val host = forge.anElementFrom(fakeHosts.keys)
        val url = "$scheme://$host$path"

        // When
        val result = testedDetector.isFirstPartyUrl(url)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `M return true W isFirstParty(String) {valid host subdomain}`(
        @StringForgery(regex = "http(s?)") scheme: String,
        @StringForgery(regex = "[a-zA-Z0-9_~-]{1,9}") subdomain: String,
        @StringForgery(regex = "(/[a-zA-Z0-9_~\\.-]{1,9}){1,4}") path: String,
        forge: Forge
    ) {
        // Given
        val host = forge.anElementFrom(fakeHosts.keys)
        val url = "$scheme://$subdomain.$host$path"

        // When
        val result = testedDetector.isFirstPartyUrl(url)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `M return true W isFirstParty(String) {known hosts list was updated}`(
        @StringForgery(regex = "http(s?)") scheme: String,
        @StringForgery(regex = "(/[a-zA-Z0-9_~\\.-]{1,9}){1,4}") path: String,
        forge: Forge
    ) {
        // Given
        val fakeNewAllowedHosts = forge.aList { forge.aStringMatching(HOST_REGEX) }
        testedDetector.addKnownHosts(fakeNewAllowedHosts)
        val host = forge.anElementFrom(fakeNewAllowedHosts)
        val url = "$scheme://$host$path"

        // When
        val result = testedDetector.isFirstPartyUrl(url)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `M return true isFirstParty(String) { wild card used for known hosts }`(
        @StringForgery(regex = "http(s?)") scheme: String,
        @StringForgery(regex = "(/[a-zA-Z0-9_~\\.-]{1,9}){1,4}") path: String,
        forge: Forge
    ) {
        // GIVEN
        val fakeNewAllowedHosts = forge.aList { forge.aStringMatching(HOST_REGEX) }
        val host = forge.anElementFrom(fakeNewAllowedHosts)
        val url = "$scheme://$host$path"
        testedDetector = DefaultFirstPartyHostHeaderTypeResolver(mapOf("*" to emptySet()))

        // WHEN
        val result = testedDetector.isFirstPartyUrl(url)

        // THEN
        assertThat(result).isTrue()
    }

    @Test
    fun `M return true isFirstParty(HttpUrl) { wild card used for known hosts }`(
        @StringForgery(regex = "http(s?)") scheme: String,
        @StringForgery(regex = "(/[a-zA-Z0-9_~\\.-]{1,9}){1,4}") path: String,
        forge: Forge
    ) {
        // GIVEN
        val fakeNewAllowedHosts = forge.aList { forge.aStringMatching(HOST_REGEX) }
        val host = forge.anElementFrom(fakeNewAllowedHosts)
        val url = "$scheme://$host$path".toHttpUrl()
        testedDetector = DefaultFirstPartyHostHeaderTypeResolver(mapOf("*" to emptySet()))

        // WHEN
        val result = testedDetector.isFirstPartyUrl(url)

        // THEN
        assertThat(result).isTrue()
    }

    @Test
    fun `M return false W isFirstParty(String) {unknown host postfixed with valid host}`(
        @StringForgery(regex = "http(s?)") scheme: String,
        @StringForgery(regex = "[a-zA-Z0-9_~-]{1,9}") prefix: String,
        @StringForgery(regex = "(/[a-zA-Z0-9_~\\.-]{1,9}){1,4}") path: String,
        forge: Forge
    ) {
        // Given
        val host = forge.anElementFrom(fakeHosts.keys)
        val url = "$scheme://$prefix$host$path"

        // When
        val result = testedDetector.isFirstPartyUrl(url)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M return false W isFirstParty(String) {invalid url}`(
        @StringForgery notAUrl: String
    ) {
        // When
        val result = testedDetector.isFirstPartyUrl(notAUrl)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M return true W isEmpty() {empty host list}`() {
        // Given
        val resolver = DefaultFirstPartyHostHeaderTypeResolver(emptyMap())

        // When
        val result = resolver.isEmpty()

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `M return false W isEmpty() {non empty host list}`() {
        // When
        val result = testedDetector.isEmpty()

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M return header types W headerTypesForUrl(String) {first party hosts}`(
        @StringForgery(regex = "http(s?)") scheme: String,
        @StringForgery(regex = "(/[a-zA-Z0-9_~\\.-]{1,9}){1,4}") path: String
    ) {
        for ((host, tracingHeaders) in fakeHosts) {
            // Given
            val url = "$scheme://$host$path"

            // When
            val detectedHeader = testedDetector.headerTypesForUrl(url)

            // Then
            assertThat(detectedHeader).isEqualTo(tracingHeaders)
        }
    }

    @Test
    fun `M return all header types W getAllHeaderTypes() {first party hosts}`() {
        // Given
        var allUsedHeaderTraces = mutableSetOf<TracingHeaderType>()

        // When
        for ((_, tracingHeaders) in fakeHosts) {
            allUsedHeaderTraces =
                allUsedHeaderTraces.plus(tracingHeaders) as MutableSet<TracingHeaderType>
        }

        // Then
        assertThat(testedDetector.getAllHeaderTypes()).isEqualTo(allUsedHeaderTraces)
    }

    @Test
    fun `M use datadog and tracecontext header types W addKnownHosts(String)`(
        @StringForgery(regex = "http(s?)") scheme: String,
        forge: Forge
    ) {
        // Given
        val fakeHosts = forge.aList { forge.aStringMatching(HOST_REGEX) }
        val fakeUrls = fakeHosts.map { "$scheme://$it" }
        testedDetector.addKnownHosts(fakeHosts)

        // When + Then
        val expectedHeaderTypes = setOf(
            TracingHeaderType.DATADOG,
            TracingHeaderType.TRACECONTEXT
        )
        fakeUrls.forEach {
            val headerTypes = testedDetector.headerTypesForUrl(it)
            assertThat(headerTypes).isEqualTo(expectedHeaderTypes)
        }
    }

    companion object {
        private const val HOST_REGEX = "([a-z][a-z0-9_~-]{3,9}\\.){1,4}[a-z][a-z0-9]{2,3}"
    }
}
