/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package datadog.trace.util

import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeConfigurator
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.io.TempDir
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.io.File
import java.io.IOException

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(value = ForgeConfigurator.NoOp::class)
internal class FileUtilsTest {

    @TempDir
    lateinit var tempDir: File

    @StringForgery
    lateinit var fakeFileName: String

    lateinit var fakeFile: File

    @StringForgery
    lateinit var fakeFileContent: String

    @BeforeEach
    fun `set up`() {
        fakeFile = File(tempDir, fakeFileName)
        fakeFile.writeText(fakeFileContent)
    }

    @Test
    fun `M read the file as bytes W readAllBytes`() {
        // When
        val result = FileUtils.readAllBytes(fakeFile.absolutePath)

        // Then
        assertThat(result).isEqualTo(fakeFileContent.toByteArray())
    }

    @Test
    fun `M throw IOException W readAllBytes { file does not exist }`(forge: Forge) {
        // When
        assertThatThrownBy { FileUtils.readAllBytes(forge.anAlphabeticalString()) }
            .isInstanceOf(IOException::class.java)
    }
}
