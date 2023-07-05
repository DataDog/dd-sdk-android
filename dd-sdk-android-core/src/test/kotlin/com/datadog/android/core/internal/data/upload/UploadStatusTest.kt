/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.upload

import com.datadog.android.api.InternalLogger
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class UploadStatusTest {

    lateinit var fakeContext: String

    @Mock
    lateinit var mockLogger: InternalLogger

    @IntForgery(min = 0)
    var fakeByteSize: Int = 0

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeContext = forge.anAlphabeticalString()
    }

    @Test
    fun `𝕄 log SUCCESS only to USER 𝕎 logStatus()`() {
        // When
        UploadStatus.SUCCESS.logStatus(
            fakeContext,
            fakeByteSize,
            mockLogger
        )

        // Then
        mockLogger.verifyLog(
            InternalLogger.Level.INFO,
            InternalLogger.Target.USER,
            "Batch [$fakeByteSize bytes] ($fakeContext) sent successfully."
        )
        verifyNoMoreInteractions(mockLogger)
    }

    @Test
    fun `𝕄 log NETWORK_ERROR only to USER 𝕎 logStatus()`() {
        // When
        UploadStatus.NETWORK_ERROR.logStatus(
            fakeContext,
            fakeByteSize,
            mockLogger
        )

        // Then
        mockLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            "Batch [$fakeByteSize bytes] ($fakeContext) failed " +
                "because of a network error; we will retry later."
        )
        verifyNoMoreInteractions(mockLogger)
    }

    @Test
    fun `𝕄 log INVALID_TOKEN_ERROR only to USER 𝕎 logStatus()`() {
        // When
        UploadStatus.INVALID_TOKEN_ERROR.logStatus(
            fakeContext,
            fakeByteSize,
            mockLogger
        )

        // Then
        mockLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            "Batch [$fakeByteSize bytes] ($fakeContext) failed " +
                "because your token is invalid; the batch was dropped. " +
                "Make sure that the provided token still " +
                "exists and you're targeting the relevant Datadog site."
        )
        verifyNoMoreInteractions(mockLogger)
    }

    @Test
    fun `𝕄 log HTTP_REDIRECTION only to USER 𝕎 logStatus()`() {
        // When
        UploadStatus.HTTP_REDIRECTION.logStatus(
            fakeContext,
            fakeByteSize,
            mockLogger
        )

        // Then
        mockLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            "Batch [$fakeByteSize bytes] ($fakeContext) failed " +
                "because of a network redirection; the batch was dropped."
        )
        verifyNoMoreInteractions(mockLogger)
    }

    @Test
    fun `𝕄 log HTTP_CLIENT_ERROR to USER and TELEMETRY 𝕎 logStatus()`() {
        // When
        UploadStatus.HTTP_CLIENT_ERROR.logStatus(
            fakeContext,
            fakeByteSize,
            mockLogger
        )

        // Then
        mockLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            "Batch [$fakeByteSize bytes] ($fakeContext) failed " +
                "because of a processing error or invalid data; " +
                "the batch was dropped."
        )
        verifyNoMoreInteractions(mockLogger)
    }

    @Test
    fun `𝕄 log HTTP_CLIENT_ERROR_RATE_LIMITING to USER and TELEMETRY 𝕎 logStatus()`() {
        // When
        UploadStatus.HTTP_CLIENT_RATE_LIMITING.logStatus(
            fakeContext,
            fakeByteSize,
            mockLogger
        )

        // Then
        mockLogger.verifyLog(
            InternalLogger.Level.WARN,
            listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            "Batch [$fakeByteSize bytes] ($fakeContext) not uploaded due to rate limitation; " +
                "we will retry later."
        )
    }

    @Test
    fun `𝕄 log HTTP_SERVER_ERROR only to USER 𝕎 logStatus()`() {
        // When
        UploadStatus.HTTP_SERVER_ERROR.logStatus(
            fakeContext,
            fakeByteSize,
            mockLogger
        )

        // Then
        mockLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            "Batch [$fakeByteSize bytes] ($fakeContext) failed " +
                "because of a server processing error; we will retry later."
        )
        verifyNoMoreInteractions(mockLogger)
    }

    @Test
    fun `𝕄 log UNKNOWN_ERROR only to USER 𝕎 logStatus()`() {
        // When
        UploadStatus.UNKNOWN_ERROR.logStatus(
            fakeContext,
            fakeByteSize,
            mockLogger
        )

        // Then
        mockLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            "Batch [$fakeByteSize bytes] ($fakeContext) failed " +
                "because of an unknown error; the batch was dropped."
        )
    }

    @Test
    fun `𝕄 log INVALID_REQUEST_ERROR only to USER 𝕎 logStatus()`() {
        // When
        UploadStatus.REQUEST_CREATION_ERROR.logStatus(
            fakeContext,
            fakeByteSize,
            mockLogger
        )

        // Then
        mockLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            "Batch [$fakeByteSize bytes] ($fakeContext) failed " +
                "because of an error when creating the request;" +
                " the batch was dropped."
        )
    }
}
