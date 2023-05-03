/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.time

import com.datadog.android.v2.api.InternalLogger
import com.datadog.tools.unit.forge.aThrowable
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@Extensions(
    ExtendWith(ForgeExtension::class)
)
internal class LoggingSyncListenerTest {

    @Test
    fun `𝕄 log error 𝕎 onError()`(
        @StringForgery(regex = "https://[a-z]+\\.com") fakeHost: String,
        forge: Forge
    ) {
        // Given
        val mockInternalLogger = mock<InternalLogger>()
        val testableListener = LoggingSyncListener(internalLogger = mockInternalLogger)
        val throwable = forge.aThrowable()

        // When
        testableListener.onError(fakeHost, throwable)

        // Then
        verify(mockInternalLogger).log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            "Kronos onError @host:$fakeHost",
            throwable
        )
    }
}
