/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.internal.telemetry

import com.datadog.android.internal.telemetry.InternalTelemetryEvent
import com.datadog.android.internal.utils.loggableStackTrace
import com.datadog.tools.unit.forge.aThrowable
import fr.xgouchet.elmyr.Forge
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
internal class InternalTelemetryErrorEventTest {

    @Test
    fun `M resolve the given stacktrace W resolveStacktrace { stacktrace explicitly provided }`(forge: Forge) {
        // Given
        val expectedStackTrace = forge.aString()
        val errorEvent = InternalTelemetryEvent.Log.Error(
            message = forge.aString(),
            additionalProperties = forge.aMap { aString() to aString() },
            error = null,
            stacktrace = expectedStackTrace,
            kind = forge.aNullable { aString() }
        )

        // When
        val resolvedStackTrace = errorEvent.resolveStacktrace()
        assertThat(resolvedStackTrace).isEqualTo(expectedStackTrace)
    }

    @Test
    fun `M resolve the given stacktrace W resolveStacktrace { stacktrace and throwable explicitly provided }`(
        forge: Forge
    ) {
        // Given
        val expectedStackTrace = forge.aString()
        val errorEvent = InternalTelemetryEvent.Log.Error(
            message = forge.aString(),
            additionalProperties = forge.aMap { aString() to aString() },
            error = forge.aThrowable(),
            stacktrace = expectedStackTrace,
            kind = forge.aNullable { aString() }
        )

        // When
        val resolvedStackTrace = errorEvent.resolveStacktrace()
        assertThat(resolvedStackTrace).isEqualTo(expectedStackTrace)
    }

    @Test
    fun `M resolve throwable stacktrace W resolveStacktrace { only throwable explicitly provided }`(
        forge: Forge
    ) {
        // Given
        val fakeThrowable = forge.aThrowable()
        val expectedStackTrace = fakeThrowable.loggableStackTrace()
        val errorEvent = InternalTelemetryEvent.Log.Error(
            message = forge.aString(),
            additionalProperties = forge.aMap { aString() to aString() },
            error = fakeThrowable,
            stacktrace = null,
            kind = forge.aNullable { aString() }
        )

        // When
        val resolvedStackTrace = errorEvent.resolveStacktrace()
        assertThat(resolvedStackTrace).isEqualTo(expectedStackTrace)
    }

    @Test
    fun `M resolve null W resolveStacktrace { stacktrace nor throwable provided }`(
        forge: Forge
    ) {
        // Given
        val errorEvent = InternalTelemetryEvent.Log.Error(
            message = forge.aString(),
            additionalProperties = forge.aMap { aString() to aString() },
            error = null,
            stacktrace = null,
            kind = forge.aNullable { aString() }
        )

        // When
        val resolvedStackTrace = errorEvent.resolveStacktrace()
        assertThat(resolvedStackTrace).isNull()
    }

    @Test
    fun `M resolve the given kind W resolveKind { kind explicitly provided }`(
        forge: Forge
    ) {
        // Given
        val expectedKind = forge.aString()
        val errorEvent = InternalTelemetryEvent.Log.Error(
            message = forge.aString(),
            additionalProperties = forge.aMap { aString() to aString() },
            error = null,
            stacktrace = forge.aNullable { aString() },
            kind = expectedKind
        )

        // When
        val resolvedKind = errorEvent.resolveKind()
        assertThat(resolvedKind).isEqualTo(expectedKind)
    }

    @Test
    fun `M resolve the given kind W resolveKind { kind and throwable explicitly provided }`(
        forge: Forge
    ) {
        // Given
        val expectedKind = forge.aString()
        val errorEvent = InternalTelemetryEvent.Log.Error(
            message = forge.aString(),
            additionalProperties = forge.aMap { aString() to aString() },
            error = forge.aThrowable(),
            stacktrace = forge.aNullable { aString() },
            kind = expectedKind
        )

        // When
        val resolvedKind = errorEvent.resolveKind()
        assertThat(resolvedKind).isEqualTo(expectedKind)
    }

    @Test
    fun `M resolve throwable kind W resolveKind { only throwable explicitly provided }`(
        forge: Forge
    ) {
        // Given
        val fakeThrowable = forge.aThrowable()
        val expectedKind = fakeThrowable.javaClass.canonicalName
        val errorEvent = InternalTelemetryEvent.Log.Error(
            message = forge.aString(),
            additionalProperties = forge.aMap { aString() to aString() },
            error = fakeThrowable,
            stacktrace = forge.aNullable { aString() },
            kind = null
        )

        // When
        val resolvedKind = errorEvent.resolveKind()
        assertThat(resolvedKind).isEqualTo(expectedKind)
    }

    @Test
    fun `M resolve throwable kind W resolveKind { only throwable explicitly provided, anonymous class }`(
        forge: Forge
    ) {
        // Given
        val fakeThrowable = object : Throwable() {}
        val expectedKind = fakeThrowable.javaClass.simpleName
        val errorEvent = InternalTelemetryEvent.Log.Error(
            message = forge.aString(),
            additionalProperties = forge.aMap { aString() to aString() },
            error = fakeThrowable,
            stacktrace = forge.aNullable { aString() },
            kind = null
        )

        // When
        val resolvedKind = errorEvent.resolveKind()
        assertThat(resolvedKind).isEqualTo(expectedKind)
    }

    @Test
    fun `M resolve null W resolveKind { kind nor throwable provided }`(
        forge: Forge
    ) {
        // Given
        val errorEvent = InternalTelemetryEvent.Log.Error(
            message = forge.aString(),
            additionalProperties = forge.aMap { aString() to aString() },
            error = null,
            stacktrace = forge.aNullable { aString() },
            kind = null
        )

        // When
        val resolvedKind = errorEvent.resolveKind()
        assertThat(resolvedKind).isNull()
    }
}
