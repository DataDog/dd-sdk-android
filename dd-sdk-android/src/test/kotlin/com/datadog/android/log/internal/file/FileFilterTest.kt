/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal.file

import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.File
import java.io.FileFilter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.io.TempDir
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings()
internal class FileFilterTest {

    @TempDir
    lateinit var tempDir: File

    lateinit var testedFilter: FileFilter

    @BeforeEach
    fun `set up`() {
        testedFilter = com.datadog.android.core.internal.data.file.FileFilter()
    }

    @Test
    fun `does not accept null files`() {
        val file: File? = null

        val accepted = testedFilter.accept(file)

        assertThat(accepted)
            .isFalse()
    }

    @Test
    fun `does not accept directory`(forge: Forge) {
        val fileName = forge.aNumericalString()
        val dir = File(tempDir, fileName)
        dir.mkdirs()

        val accepted = testedFilter.accept(dir)

        assertThat(accepted)
            .isFalse()
    }

    @Test
    fun `does not accept file with at least one invalid char`(forge: Forge) {
        val fileName = forge.aNumericalString() +
            forge.anAlphabeticalChar() +
            forge.aNumericalString()
        val file = File(tempDir, fileName)
        file.writeText(forge.anAlphabeticalString())

        val accepted = testedFilter.accept(file)

        assertThat(accepted)
            .isFalse()
    }

    @Test
    fun `accepts a file with digit only name`(forge: Forge) {
        val fileName = forge.aNumericalString()
        val file = File(tempDir, fileName)
        file.writeText(forge.anAlphabeticalString())

        val accepted = testedFilter.accept(file)

        assertThat(accepted)
            .isTrue()
    }
}
