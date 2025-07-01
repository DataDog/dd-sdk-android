/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.opentelemetry.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.trace.opentelemetry.utils.verifyLog
import com.datadog.android.trace.opentelemetry.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
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
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(ApiLevelExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class MiscUtilsTest {

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Test
    fun `M return defaultValue and log exception W executeIfJavaFunctionPackageExists{action throws, below 24}`(
        @StringForgery defaultValue: String
    ) {
        // Given
        val throwble = Throwable()
        val action: () -> String = { throw throwble }

        // When
        val result = executeIfJavaFunctionPackageExists(mockInternalLogger, defaultValue, action)

        // Then
        assertThat(result).isEqualTo(defaultValue)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            NEEDS_DESUGARING_ERROR_MESSAGE,
            throwble
        )
    }

    @Test
    fun `M return action result W executeIfJavaFunctionPackageExists{action does not throw, below 24}`(
        @StringForgery actionResult: String,
        @StringForgery defaultValue: String
    ) {
        // Given
        val action: () -> String = { actionResult }

        // When
        val result = executeIfJavaFunctionPackageExists(mockInternalLogger, defaultValue, action)

        // Then
        assertThat(result).isEqualTo(actionResult)
    }

    @TestTargetApi(24)
    @Test
    fun `M return action result W executeIfJavaFunctionPackageExists{action does not throw, above 23}`(
        @StringForgery actionResult: String,
        @StringForgery defaultValue: String
    ) {
        // Given
        val action: () -> String = { actionResult }

        // When
        val result = executeIfJavaFunctionPackageExists(mockInternalLogger, defaultValue, action)

        // Then
        assertThat(result).isEqualTo(actionResult)
    }
}
