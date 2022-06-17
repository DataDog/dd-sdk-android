/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file

import com.datadog.android.log.Logger
import com.datadog.android.security.Encryption
import com.datadog.android.utils.config.LoggerTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.nhaarman.mockitokotlin2.mock
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
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@ForgeConfiguration(Configurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class FileReaderWriterTest {

    @Test
    fun `𝕄 create FileReaderWriter 𝕎 create() { without encryption }`() {
        // When
        val readerWriter = FileReaderWriter.create(Logger(logger.mockSdkLogHandler), null)
        // Then
        assertThat(readerWriter)
            .isInstanceOf(PlainFileReaderWriter::class.java)
    }

    @Test
    fun `𝕄 create FileReaderWriter 𝕎 create() { with encryption }`() {
        // When
        val mockEncryption = mock<Encryption>()
        val readerWriter = FileReaderWriter.create(
            Logger(logger.mockSdkLogHandler),
            mockEncryption
        )

        // Then
        assertThat(readerWriter)
            .isInstanceOf(EncryptedFileReaderWriter::class.java)

        (readerWriter as EncryptedFileReaderWriter).let {
            assertThat(it.delegate).isInstanceOf(PlainFileReaderWriter::class.java)
            assertThat(it.encryption).isEqualTo(mockEncryption)
        }
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
