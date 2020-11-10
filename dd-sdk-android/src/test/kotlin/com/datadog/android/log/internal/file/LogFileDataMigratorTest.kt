/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.file

import com.datadog.android.log.internal.domain.LogFileDataMigrator
import com.datadog.android.log.internal.domain.LogFileStrategy
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.io.TempDir
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings

@Extensions(
    ExtendWith(MockitoExtension::class)
)
@MockitoSettings()
internal class LogFileDataMigratorTest {

    lateinit var testedMigrator: LogFileDataMigrator

    @TempDir
    lateinit var tempRootDir: File

    lateinit var tempOldDir: File

    @BeforeEach
    fun `set up`() {
        testedMigrator =
            LogFileDataMigrator(tempRootDir)
        tempOldDir = File(tempRootDir, LogFileStrategy.INTERMEDIATE_DATA_FOLDER)
        tempOldDir.mkdirs()
    }

    @Test
    fun `will migrate all the data up to latest version`() {
        testedMigrator.migrateData()

        assertThat(tempOldDir).doesNotExist()
    }

    @Test
    fun `will migrate data even if the old data directory does not exist`() {
        tempOldDir.deleteRecursively()

        testedMigrator.migrateData()

        assertThat(tempOldDir).doesNotExist()
    }

    @AfterEach
    fun `tear down`() {
        tempRootDir.deleteRecursively()
    }
}
