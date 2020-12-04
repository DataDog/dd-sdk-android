/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.net

import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.HttpUrl
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
internal class FirstPartyHostDetectorTest {

    lateinit var testedDetector: FirstPartyHostDetector

    @StringForgery(regex = HOST_REGEX)
    lateinit var fakeHosts: List<String>

    @BeforeEach
    fun `set up`() {
        testedDetector = FirstPartyHostDetector(fakeHosts)
    }

    @Test
    fun `𝕄 return false 𝕎 isFirstParty(HttpUrl) {unknown host}`(
        @StringForgery(regex = "http(s?)") scheme: String,
        @StringForgery(regex = "(/[a-zA-Z0-9_~\\.-]{1,9}){1,4}") path: String,
        forge: Forge
    ) {
        // Given
        var host = forge.aStringMatching(HOST_REGEX)
        while (host in fakeHosts) {
            host = forge.aStringMatching(HOST_REGEX)
        }
        val url = HttpUrl.get("$scheme://$host$path")

        // When
        val result = testedDetector.isFirstPartyUrl(url)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `𝕄 return true 𝕎 isFirstParty(HttpUrl) {exact first party host}`(
        @StringForgery(regex = "http(s?)") scheme: String,
        @StringForgery(regex = "(/[a-zA-Z0-9_~\\.-]{1,9}){1,4}") path: String,
        forge: Forge
    ) {
        // Given
        val host = forge.anElementFrom(fakeHosts)
        val url = HttpUrl.get("$scheme://$host$path")

        // When
        val result = testedDetector.isFirstPartyUrl(url)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `𝕄 return true 𝕎 isFirstParty(HttpUrl) {known hosts list was updated}`(
        @StringForgery(regex = "http(s?)") scheme: String,
        @StringForgery(regex = "(/[a-zA-Z0-9_~\\.-]{1,9}){1,4}") path: String,
        forge: Forge
    ) {
        // Given
        val fakeNewAllowedHosts = forge.aList { forge.aStringMatching(HOST_REGEX) }
        testedDetector.addKnownHosts(fakeNewAllowedHosts)
        val host = forge.anElementFrom(fakeNewAllowedHosts)
        val url = HttpUrl.get("$scheme://$host$path")

        // When
        val result = testedDetector.isFirstPartyUrl(url)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `𝕄 return true 𝕎 isFirstParty(HttpUrl) {valid host subdomain}`(
        @StringForgery(regex = "http(s?)") scheme: String,
        @StringForgery(regex = "[a-zA-Z0-9_~-]{1,9}") subdomain: String,
        @StringForgery(regex = "(/[a-zA-Z0-9_~\\.-]{1,9}){1,4}") path: String,
        forge: Forge
    ) {
        // Given
        val host = forge.anElementFrom(fakeHosts)
        val url = HttpUrl.get("$scheme://$subdomain.$host$path")

        // When
        val result = testedDetector.isFirstPartyUrl(url)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `𝕄 return false 𝕎 isFirstParty(HttpUrl) {unknown host postfixed with valid host}`(
        @StringForgery(regex = "http(s?)") scheme: String,
        @StringForgery(regex = "[a-zA-Z0-9_~-]{1,9}") prefix: String,
        @StringForgery(regex = "(/[a-zA-Z0-9_~\\.-]{1,9}){1,4}") path: String,
        forge: Forge
    ) {
        // Given
        val host = forge.anElementFrom(fakeHosts)
        val url = HttpUrl.get("$scheme://$prefix$host$path")

        // When
        val result = testedDetector.isFirstPartyUrl(url)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `𝕄 return false 𝕎 isFirstParty(String) {unknown host}`(
        @StringForgery(regex = "http(s?)") scheme: String,
        @StringForgery(regex = "(/[a-zA-Z0-9_~\\.-]{1,9}){1,4}") path: String,
        forge: Forge
    ) {
        // Given
        var host = forge.aStringMatching(HOST_REGEX)
        while (host in fakeHosts) {
            host = forge.aStringMatching(HOST_REGEX)
        }
        val url = "$scheme://$host$path"

        // When
        val result = testedDetector.isFirstPartyUrl(url)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `𝕄 return true 𝕎 isFirstParty(String) {exact first party host}`(
        @StringForgery(regex = "http(s?)") scheme: String,
        @StringForgery(regex = "(/[a-zA-Z0-9_~\\.-]{1,9}){1,4}") path: String,
        forge: Forge
    ) {
        // Given
        val host = forge.anElementFrom(fakeHosts)
        val url = "$scheme://$host$path"

        // When
        val result = testedDetector.isFirstPartyUrl(url)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `𝕄 return true 𝕎 isFirstParty(String) {valid host subdomain}`(
        @StringForgery(regex = "http(s?)") scheme: String,
        @StringForgery(regex = "[a-zA-Z0-9_~-]{1,9}") subdomain: String,
        @StringForgery(regex = "(/[a-zA-Z0-9_~\\.-]{1,9}){1,4}") path: String,
        forge: Forge
    ) {
        // Given
        val host = forge.anElementFrom(fakeHosts)
        val url = "$scheme://$subdomain.$host$path"

        // When
        val result = testedDetector.isFirstPartyUrl(url)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `𝕄 return true 𝕎 isFirstParty(String) {known hosts list was updated}`(
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
    fun `𝕄 return false 𝕎 isFirstParty(String) {unknown host postfixed with valid host}`(
        @StringForgery(regex = "http(s?)") scheme: String,
        @StringForgery(regex = "[a-zA-Z0-9_~-]{1,9}") prefix: String,
        @StringForgery(regex = "(/[a-zA-Z0-9_~\\.-]{1,9}){1,4}") path: String,
        forge: Forge
    ) {
        // Given
        val host = forge.anElementFrom(fakeHosts)
        val url = "$scheme://$prefix$host$path"

        // When
        val result = testedDetector.isFirstPartyUrl(url)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `𝕄 return false 𝕎 isFirstParty(String) {invalid url}`(
        @StringForgery notAUrl: String,
        forge: Forge
    ) {
        // When
        val result = testedDetector.isFirstPartyUrl(notAUrl)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `𝕄 return true 𝕎 isEmpty() {empty host list}`() {
        // Given
        val detector = FirstPartyHostDetector(emptyList())

        // When
        val result = detector.isEmpty()

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `𝕄 return false 𝕎 isEmpty() {non empty host list}`() {
        // When
        val result = testedDetector.isEmpty()

        // Then
        assertThat(result).isFalse()
    }

    companion object {
        private const val HOST_REGEX = "([a-z][a-z0-9_~-]{3,9}\\.){1,4}[a-z][a-z0-9]{2,3}"
    }
}
