/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.android.core.internal.net

import android.util.Log
import com.datadog.android.Datadog
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.annotations.SystemOutStream
import com.datadog.tools.unit.extensions.SystemStreamExtension
import com.datadog.tools.unit.lastLine
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.ByteArrayOutputStream
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
    ExtendWith(ForgeExtension::class),
    ExtendWith(SystemStreamExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class UploadStatusTest {

    lateinit var fakeContext: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeContext = forge.anAlphabeticalString()
        Datadog.setVerbosity(Log.VERBOSE)
    }

    @Test
    fun `logStatus SUCCESS`(
        @SystemOutStream outputStream: ByteArrayOutputStream
    ) {
        UploadStatus.SUCCESS.logStatus(fakeContext)

        assertThat(outputStream.lastLine())
            .isEqualTo("V/Datadog: Batch sent successfully ($fakeContext).")
    }

    @Test
    fun `logStatus NETWORK_ERROR`(
        @SystemOutStream outputStream: ByteArrayOutputStream
    ) {
        UploadStatus.NETWORK_ERROR.logStatus(fakeContext)

        assertThat(outputStream.lastLine())
            .isEqualTo(
                "E/Datadog: Unable to send batch ($fakeContext) because of a network error; " +
                    "we will retry later."
            )
    }

    @Test
    fun `logStatus INVALID_TOKEN_ERROR`(
        @SystemOutStream outputStream: ByteArrayOutputStream
    ) {
        UploadStatus.INVALID_TOKEN_ERROR.logStatus(fakeContext)

        assertThat(outputStream.lastLine())
            .isEqualTo(
                "E/Datadog: Unable to send batch ($fakeContext) because your token is invalid. " +
                    "Make sure that the provided token still exists."
            )
    }

    @Test
    fun `logStatus HTTP_REDIRECTION`(
        @SystemOutStream outputStream: ByteArrayOutputStream
    ) {
        UploadStatus.HTTP_REDIRECTION.logStatus(fakeContext)

        assertThat(outputStream.lastLine())
            .isEqualTo(
                "W/Datadog: Unable to send batch ($fakeContext) because of a network error; " +
                    "we will retry later."
            )
    }

    @Test
    fun `logStatus HTTP_CLIENT_ERROR`(
        @SystemOutStream outputStream: ByteArrayOutputStream
    ) {
        UploadStatus.HTTP_CLIENT_ERROR.logStatus(fakeContext)

        assertThat(outputStream.lastLine())
            .isEqualTo(
                "E/Datadog: Unable to send batch ($fakeContext) because of a processing error " +
                    "(possibly because of invalid data); the batch was dropped."
            )
    }

    @Test
    fun `logStatus HTTP_SERVER_ERROR`(
        @SystemOutStream outputStream: ByteArrayOutputStream
    ) {
        UploadStatus.HTTP_SERVER_ERROR.logStatus(fakeContext)

        assertThat(outputStream.lastLine())
            .isEqualTo(
                "E/Datadog: Unable to send batch ($fakeContext) because of a " +
                    "server processing error; we will retry later."
            )
    }

    @Test
    fun `logStatus UNKNOWN_ERROR`(
        @SystemOutStream outputStream: ByteArrayOutputStream
    ) {
        UploadStatus.UNKNOWN_ERROR.logStatus(fakeContext)

        assertThat(outputStream.lastLine())
            .isEqualTo(
                "E/Datadog: Unable to send batch ($fakeContext) because of an unknown error; " +
                    "we will retry later."
            )
    }
}
