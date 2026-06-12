/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.configuration

import com.datadog.android.api.InternalLogger
import com.datadog.android.utils.config.InternalLoggerTestConfiguration
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.util.Locale

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
internal class HostPatternValidatorTest {

    lateinit var testedValidator: HostPatternValidator

    @StringForgery(type = StringForgeryType.ALPHABETICAL)
    lateinit var fakeFeature: String

    @BeforeEach
    fun `set up`() {
        testedValidator = HostPatternValidator()
    }

    @Test
    fun `M keep valid wildcard patterns W validate()`() {
        // Given
        val patterns = listOf("*.example.com", "preview-*.shopist.io", "example.net")

        // When
        val result = testedValidator.validate(patterns, fakeFeature)

        // Then
        assertThat(result).isEqualTo(patterns)
    }

    @Test
    fun `M keep plain hosts W validate { no wildcard }`(
        @StringForgery(
            regex = "(([a-z0-9]|[a-z0-9][a-z0-9-]*[a-z0-9])\\.)+([a-z]|[a-z][a-z0-9-]*[a-z0-9])"
        ) hosts: List<String>
    ) {
        // When
        val result = testedValidator.validate(hosts, fakeFeature)

        // Then
        assertThat(result).isEqualTo(hosts)
    }

    @Test
    fun `M keep single wildcard W validate { star }`() {
        // When
        val result = testedValidator.validate(listOf("*"), fakeFeature)

        // Then
        assertThat(result).containsExactly("*")
    }

    @Test
    fun `M keep empty string W validate { blank }`() {
        // When
        val result = testedValidator.validate(listOf(""), fakeFeature)

        // Then
        assertThat(result).containsExactly("")
    }

    @Test
    fun `M lowercase patterns W validate { uppercase }`() {
        // When
        val result = testedValidator.validate(
            listOf("*.SHOPIST.IO", "Preview-*.Example.COM"),
            fakeFeature
        )

        // Then
        assertThat(result).containsExactly("*.shopist.io", "preview-*.example.com")
    }

    @Test
    fun `M drop and warn W validate { more than one wildcard }`() {
        // Given
        val pattern = "*.foo.*.bar"

        // When
        val result = testedValidator.validate(listOf(pattern), fakeFeature)

        // Then
        assertThat(result).isEmpty()
        val expectedMessage = HostPatternValidator.ERROR_MULTIPLE_WILDCARDS.format(
            Locale.US,
            pattern,
            fakeFeature
        )
        argumentCaptor<() -> String> {
            verify(logger.mockInternalLogger).log(
                eq(InternalLogger.Level.WARN),
                eq(InternalLogger.Target.USER),
                capture(),
                isNull(),
                eq(false),
                eq(null)
            )
            assertThat(firstValue()).isEqualTo(expectedMessage)
        }
    }

    @Test
    fun `M drop and warn W validate { invalid characters }`() {
        // Given
        val patterns = listOf("foo'bar.com", "back\\slash.com", "https://foo.com")

        // When
        val result = testedValidator.validate(patterns, fakeFeature)

        // Then
        assertThat(result).isEmpty()
        val expectedMessages = patterns.map {
            HostPatternValidator.ERROR_INVALID_CHARACTERS.format(Locale.US, it, fakeFeature)
        }
        argumentCaptor<() -> String> {
            verify(logger.mockInternalLogger, times(patterns.size)).log(
                eq(InternalLogger.Level.WARN),
                eq(InternalLogger.Target.USER),
                capture(),
                isNull(),
                eq(false),
                eq(null)
            )
            assertThat(allValues.map { it() })
                .containsExactlyInAnyOrderElementsOf(expectedMessages)
        }
    }

    @Test
    fun `M warn with original pattern W validate { uppercase invalid characters }`() {
        // Given
        val pattern = "FOO'BAR.com"

        // When
        testedValidator.validate(listOf(pattern), fakeFeature)

        // Then
        val expectedMessage = HostPatternValidator.ERROR_INVALID_CHARACTERS.format(
            Locale.US,
            pattern,
            fakeFeature
        )
        argumentCaptor<() -> String> {
            verify(logger.mockInternalLogger).log(
                eq(InternalLogger.Level.WARN),
                eq(InternalLogger.Target.USER),
                capture(),
                isNull(),
                eq(false),
                eq(null)
            )
            assertThat(firstValue()).isEqualTo(expectedMessage)
        }
    }

    @Test
    fun `M keep valid and drop invalid W validate { mixed }`() {
        // Given
        val validWildcard = "*.shopist.io"
        val plainHost = "example.com"
        val multiWildcard = "*.foo.*.bar"
        val invalidChars = "foo'bar.com"
        val patterns = listOf(validWildcard, multiWildcard, plainHost, invalidChars)

        // When
        val result = testedValidator.validate(patterns, fakeFeature)

        // Then
        assertThat(result).containsExactly(validWildcard, plainHost)
    }

    companion object {
        val logger = InternalLoggerTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(logger)
        }
    }
}
