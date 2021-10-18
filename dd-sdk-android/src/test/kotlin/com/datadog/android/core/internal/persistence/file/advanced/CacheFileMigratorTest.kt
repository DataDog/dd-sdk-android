/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.advanced

import com.datadog.android.core.internal.persistence.file.FileHandler
import com.datadog.android.core.internal.persistence.file.FileOrchestrator
import com.datadog.android.log.Logger
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.File
import java.util.concurrent.ExecutorService
import org.assertj.core.api.Assertions.assertThat
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
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class CacheFileMigratorTest {

    lateinit var testedMigrator: DataMigrator<Boolean>

    @Mock
    lateinit var mockPreviousOrchestrator: FileOrchestrator

    @Mock
    lateinit var mockNewOrchestrator: FileOrchestrator

    @Mock
    lateinit var mockFileHandler: FileHandler

    @Mock
    lateinit var mockExecutorService: ExecutorService

    @Mock
    lateinit var mockLogHander: LogHandler

    @BeforeEach
    fun `set up`() {
        testedMigrator = CacheFileMigrator(
            mockFileHandler,
            mockExecutorService,
            Logger(mockLogHander)
        )
    }

    @Test
    fun `𝕄 move and wipe files dir 𝕎 migrateData() {any to true}`(
        @BoolForgery previousState: Boolean,
        @BoolForgery previousStateIsNull: Boolean,
        @Forgery previousDir: File,
        @Forgery newDir: File
    ) {
        // Given
        whenever(mockPreviousOrchestrator.getRootDir()) doReturn previousDir
        whenever(mockNewOrchestrator.getRootDir()) doReturn newDir

        // When
        testedMigrator.migrateData(
            if (previousStateIsNull) null else previousState,
            mockPreviousOrchestrator,
            true,
            mockNewOrchestrator
        )

        // Then
        argumentCaptor<Runnable> {
            verify(mockExecutorService, times(2)).submit(capture())

            assertThat(firstValue).isInstanceOf(MoveDataMigrationOperation::class.java)
            val moveOperation = firstValue as MoveDataMigrationOperation
            assertThat(moveOperation.fromDir).isSameAs(previousDir)
            assertThat(moveOperation.toDir).isSameAs(newDir)
            assertThat(moveOperation.fileHandler).isSameAs(mockFileHandler)
            assertThat(moveOperation.internalLogger.handler).isSameAs(mockLogHander)

            assertThat(secondValue).isInstanceOf(WipeDataMigrationOperation::class.java)
            val wipeOperation = secondValue as WipeDataMigrationOperation
            assertThat(wipeOperation.targetDir).isSameAs(previousDir)
            assertThat(wipeOperation.fileHandler).isSameAs(mockFileHandler)
            assertThat(wipeOperation.internalLogger.handler).isSameAs(mockLogHander)
        }
    }

    @Test
    fun `𝕄 move and wipe files dir 𝕎 migrateData() {any to false}`(
        @BoolForgery previousState: Boolean,
        @BoolForgery previousStateIsNull: Boolean,
        @Forgery previousDir: File,
        @Forgery newDir: File
    ) {
        // Given
        whenever(mockPreviousOrchestrator.getRootDir()) doReturn previousDir
        whenever(mockNewOrchestrator.getRootDir()) doReturn newDir

        // When
        testedMigrator.migrateData(
            if (previousStateIsNull) null else previousState,
            mockPreviousOrchestrator,
            false,
            mockNewOrchestrator
        )

        // Then
        verifyZeroInteractions(mockExecutorService)
    }
}
