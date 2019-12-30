package com.datadog.android.log.internal.file

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

    lateinit var underTest: LogFileDataMigrator

    @TempDir
    lateinit var rootDir: File

    lateinit var v0DataFolder: File

    @BeforeEach
    fun `set up`() {
        underTest =
            LogFileDataMigrator(rootDir)
        v0DataFolder = File(rootDir, LogFileStrategy.DATA_FOLDER_ROOT)
        v0DataFolder.mkdirs()
    }

    @Test
    fun `will migrate all the data up to latest version`() {
        underTest.migrateData()

        assertThat(v0DataFolder).doesNotExist()
    }

    @Test
    fun `will migrate data even if the old data directory does not exist`() {
        v0DataFolder.deleteRecursively()

        underTest.migrateData()

        assertThat(v0DataFolder).doesNotExist()
    }

    @AfterEach
    fun `tear down`() {
        rootDir.deleteRecursively()
    }
}
