/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.domain.batching.migrators

import com.datadog.android.core.internal.data.file.FileHandler
import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.File
import java.util.concurrent.ExecutorService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
internal class WipeDataMigratorTest {

    @Mock
    lateinit var mockedExecutorService: ExecutorService

    @Mock
    lateinit var mockedFileHandler: FileHandler

    @StringForgery(regex = "([a-zA-z]+)/([a-zA-z]+)")
    lateinit var fakeFolderPath: String
    lateinit var testedMigrator: WipeDataMigrator

    @BeforeEach
    fun `set up`() {
        testedMigrator = WipeDataMigrator(fakeFolderPath, mockedExecutorService, mockedFileHandler)
    }

    @Test
    fun `M delegate to fileHandler W migrateData`() {
        // GIVEN
        whenever(mockedFileHandler.deleteFileOrDirectory(any())).thenReturn(true)
        val argumentCaptor = argumentCaptor<Runnable>()

        // WHEN
        testedMigrator.migrateData()

        // THEN
        verify(mockedExecutorService).submit(argumentCaptor.capture())
        argumentCaptor.firstValue.run()
        verify(mockedFileHandler).deleteFileOrDirectory(
            argThat {
                this.absolutePath == File(fakeFolderPath).absolutePath
            }
        )
    }

    @Test
    fun `M retry maximum 3 times W migrateData fails`() {
        // GIVEN
        val argumentCaptor = argumentCaptor<Runnable>()
        whenever(mockedFileHandler.deleteFileOrDirectory(any())).thenReturn(false)

        // WHEN
        testedMigrator.migrateData()

        // THEN
        verify(mockedExecutorService).submit(argumentCaptor.capture())
        argumentCaptor.firstValue.run()
        verify(mockedFileHandler, times(3)).deleteFileOrDirectory(
            argThat {
                this.absolutePath == File(fakeFolderPath).absolutePath
            }
        )
    }

    @Test
    fun `M retry W migrateData fails`() {
        // GIVEN
        val argumentCaptor = argumentCaptor<Runnable>()
        whenever(mockedFileHandler.deleteFileOrDirectory(any()))
            .thenReturn(false)
            .thenReturn(true)

        // WHEN
        testedMigrator.migrateData()

        // THEN
        verify(mockedExecutorService).submit(argumentCaptor.capture())
        argumentCaptor.firstValue.run()
        verify(mockedFileHandler, times(2)).deleteFileOrDirectory(
            argThat {
                this.absolutePath == File(fakeFolderPath).absolutePath
            }
        )
    }
}
