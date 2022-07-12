/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.time

import android.util.Log
import com.datadog.android.utils.config.LoggerTestConfiguration
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.datadog.tools.unit.forge.aThrowable
import com.nhaarman.mockitokotlin2.verify
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions

@Extensions(
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
internal class LoggingSyncListenerTest {

    private val testableListener = LoggingSyncListener()

    @Test
    fun `𝕄 log error 𝕎 onError()`(
        @StringForgery(regex = "https://[a-z]+\\.com") fakeHost: String,
        forge: Forge
    ) {
        // Given
        val throwable = forge.aThrowable()

        // When
        testableListener.onError(fakeHost, throwable)

        // Then
        verify(logger.mockSdkLogHandler)
            .handleLog(
                Log.ERROR,
                "Kronos onError @host:$fakeHost",
                throwable,
                mapOf("kronos.sync.host" to fakeHost)
            )
    }

    companion object {
        val logger = LoggerTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(logger)
        }
    }
}
