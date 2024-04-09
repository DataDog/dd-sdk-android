/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.advanced

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.persistence.file.FileOrchestrator
import com.datadog.android.core.internal.privacy.ConsentProvider
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class ConsentAwareFileOrchestratorTest {

    lateinit var testedOrchestrator: ConsentAwareFileOrchestrator

    @Mock
    lateinit var mockConsentProvider: ConsentProvider

    @Mock
    lateinit var mockPendingOrchestrator: FileOrchestrator

    @Mock
    lateinit var mockGrantedOrchestrator: FileOrchestrator

    @Mock
    lateinit var mockExecutorService: ExecutorService

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockDataMigrator: DataMigrator<TrackingConsent>

    @BeforeEach
    fun `set up`() {
        instantiateTestedOrchestrator(TrackingConsent.PENDING)
        runPendingRunnable()
        reset(mockDataMigrator, mockConsentProvider, mockExecutorService)
    }

    // region init

    @Test
    fun `M registers as listener W init()`(
        @Forgery consent: TrackingConsent
    ) {
        // When
        instantiateTestedOrchestrator(consent)

        // Then
        verify(mockConsentProvider).registerCallback(testedOrchestrator)
    }

    @Test
    fun `M migrate data W init() {GRANTED}`() {
        // When
        instantiateTestedOrchestrator(TrackingConsent.GRANTED)

        // Then
        argumentCaptor<Runnable> {
            verify(mockExecutorService).submit(capture())
            firstValue.run()
            verify(mockDataMigrator).migrateData(
                null,
                mockPendingOrchestrator,
                TrackingConsent.GRANTED,
                mockGrantedOrchestrator
            )
        }
    }

    @Test
    fun `M migrate data W init() {PENDING}`() {
        // When
        instantiateTestedOrchestrator(TrackingConsent.PENDING)

        // Then
        argumentCaptor<Runnable> {
            verify(mockExecutorService).submit(capture())
            firstValue.run()
            verify(mockDataMigrator).migrateData(
                null,
                mockPendingOrchestrator,
                TrackingConsent.PENDING,
                mockPendingOrchestrator
            )
        }
    }

    @Test
    fun `M migrate data W init() {NOT_GRANTED}`() {
        // When
        instantiateTestedOrchestrator(TrackingConsent.NOT_GRANTED)

        // Then
        argumentCaptor<Runnable> {
            verify(mockExecutorService).submit(capture())
            firstValue.run()
            verify(mockDataMigrator).migrateData(
                null,
                mockPendingOrchestrator,
                TrackingConsent.NOT_GRANTED,
                ConsentAwareFileOrchestrator.NO_OP_ORCHESTRATOR
            )
        }
    }

    // endregion

    // region getWritableFile

    @Test
    fun `M return pending writable file W getWritableFile() {consent=PENDING}`(
        @BoolForgery forceNewFile: Boolean,
        @Forgery file: File
    ) {
        // Given
        whenever(mockPendingOrchestrator.getWritableFile(forceNewFile)) doReturn file

        // When
        val result = testedOrchestrator.getWritableFile(forceNewFile)

        // Then
        assertThat(result).isSameAs(file)
        verifyNoInteractions(mockGrantedOrchestrator)
    }

    @Test
    fun `M return pending writable file W getWritableFile() {consent=GRANTED then PENDING}`(
        @BoolForgery forceNewFile: Boolean,
        @Forgery file: File
    ) {
        // Given
        instantiateTestedOrchestrator(TrackingConsent.GRANTED)
        runPendingRunnable()
        whenever(mockPendingOrchestrator.getWritableFile(forceNewFile)) doReturn file

        // When
        testedOrchestrator.onConsentUpdated(TrackingConsent.GRANTED, TrackingConsent.PENDING)
        runPendingRunnable()
        val result = testedOrchestrator.getWritableFile(forceNewFile)

        // Then
        assertThat(result).isSameAs(file)
        verifyNoInteractions(mockGrantedOrchestrator)
    }

    @Test
    fun `M return pending writable file W getWritableFile() {consent=NOT_GRANTED then PENDING}`(
        @BoolForgery forceNewFile: Boolean,
        @Forgery file: File
    ) {
        // Given
        instantiateTestedOrchestrator(TrackingConsent.NOT_GRANTED)
        runPendingRunnable()
        whenever(mockPendingOrchestrator.getWritableFile(forceNewFile)) doReturn file

        // When
        testedOrchestrator.onConsentUpdated(TrackingConsent.NOT_GRANTED, TrackingConsent.PENDING)
        runPendingRunnable()
        val result = testedOrchestrator.getWritableFile(forceNewFile)

        // Then
        assertThat(result).isSameAs(file)
        verifyNoInteractions(mockGrantedOrchestrator)
    }

    @Test
    fun `M return granted writable file W getWritableFile() {consent=GRANTED}`(
        @BoolForgery forceNewFile: Boolean,
        @Forgery file: File
    ) {
        // Given
        instantiateTestedOrchestrator(TrackingConsent.GRANTED)
        runPendingRunnable()
        whenever(mockGrantedOrchestrator.getWritableFile(forceNewFile)) doReturn file

        // When
        val result = testedOrchestrator.getWritableFile(forceNewFile)

        // Then
        assertThat(result).isSameAs(file)
        verifyNoInteractions(mockPendingOrchestrator)
    }

    @Test
    fun `M return granted writable file W getWritableFile() {consent=NOT_GRANTED then GRANTED}`(
        @BoolForgery forceNewFile: Boolean,
        @Forgery file: File
    ) {
        // Given
        instantiateTestedOrchestrator(TrackingConsent.NOT_GRANTED)
        runPendingRunnable()
        whenever(mockGrantedOrchestrator.getWritableFile(forceNewFile)) doReturn file

        // When
        testedOrchestrator.onConsentUpdated(TrackingConsent.NOT_GRANTED, TrackingConsent.GRANTED)
        runPendingRunnable()
        val result = testedOrchestrator.getWritableFile(forceNewFile)

        // Then
        assertThat(result).isSameAs(file)
        verifyNoInteractions(mockPendingOrchestrator)
    }

    @Test
    fun `M return granted writable file W getWritableFile() {consent=PENDING then GRANTED}`(
        @BoolForgery forceNewFile: Boolean,
        @Forgery file: File
    ) {
        // Given
        instantiateTestedOrchestrator(TrackingConsent.PENDING)
        runPendingRunnable()
        whenever(mockGrantedOrchestrator.getWritableFile(forceNewFile)) doReturn file

        // When
        testedOrchestrator.onConsentUpdated(TrackingConsent.PENDING, TrackingConsent.GRANTED)
        runPendingRunnable()
        val result = testedOrchestrator.getWritableFile(forceNewFile)

        // Then
        assertThat(result).isSameAs(file)
        verifyNoInteractions(mockPendingOrchestrator)
    }

    @Test
    fun `M return null file W getWritableFile() {consent=NOT_GRANTED}`(
        @BoolForgery forceNewFile: Boolean
    ) {
        // Given
        instantiateTestedOrchestrator(TrackingConsent.NOT_GRANTED)
        runPendingRunnable()

        // When
        val result = testedOrchestrator.getWritableFile(forceNewFile)

        // Then
        assertThat(result).isNull()
        verifyNoInteractions(mockPendingOrchestrator, mockGrantedOrchestrator)
    }

    @Test
    fun `M return null file W getWritableFile() {consent=GRANTED then NOT_GRANTED}`(
        @BoolForgery forceNewFile: Boolean
    ) {
        // Given
        instantiateTestedOrchestrator(TrackingConsent.GRANTED)
        runPendingRunnable()

        // When
        testedOrchestrator.onConsentUpdated(TrackingConsent.GRANTED, TrackingConsent.NOT_GRANTED)
        runPendingRunnable()
        val result = testedOrchestrator.getWritableFile(forceNewFile)

        // Then
        assertThat(result).isNull()
        verifyNoInteractions(mockPendingOrchestrator, mockGrantedOrchestrator)
    }

    @Test
    fun `M return null file W getWritableFile() {consent=PENDING then NOT_GRANTED}`(
        @BoolForgery forceNewFile: Boolean
    ) {
        // Given
        instantiateTestedOrchestrator(TrackingConsent.PENDING)
        runPendingRunnable()

        // When
        testedOrchestrator.onConsentUpdated(TrackingConsent.PENDING, TrackingConsent.NOT_GRANTED)
        runPendingRunnable()
        val result = testedOrchestrator.getWritableFile(forceNewFile)

        // Then
        assertThat(result).isNull()
        verifyNoInteractions(mockPendingOrchestrator, mockGrantedOrchestrator)
    }

    // endregion

    // region getReadableFile

    @Test
    fun `M return granted file W getReadableFile() {initial consent}`(
        @Forgery consent: TrackingConsent,
        @Forgery file: File
    ) {
        // Given
        instantiateTestedOrchestrator(consent)
        whenever(mockGrantedOrchestrator.getReadableFile(any())) doReturn file

        // When
        val result = testedOrchestrator.getReadableFile(emptySet())

        // Then
        assertThat(result).isSameAs(file)
        verifyNoInteractions(mockPendingOrchestrator)
    }

    @Test
    fun `M return granted file W getReadableFile() {updated consent}`(
        @Forgery initialConsent: TrackingConsent,
        @Forgery updatedConsent: TrackingConsent,
        @Forgery file: File
    ) {
        // Given
        instantiateTestedOrchestrator(initialConsent)
        whenever(mockGrantedOrchestrator.getReadableFile(any())) doReturn file

        // When
        testedOrchestrator.onConsentUpdated(initialConsent, updatedConsent)
        val result = testedOrchestrator.getReadableFile(emptySet())

        // Then
        assertThat(result).isSameAs(file)
        verifyNoInteractions(mockPendingOrchestrator)
    }

    // endregion

    // region getAllFiles

    @Test
    fun `M return all files W getAllFiles() {initial consent}`(
        @Forgery consent: TrackingConsent,
        forge: Forge
    ) {
        // Given
        instantiateTestedOrchestrator(consent)
        val pendingFiles = forge.aList { getForgery<File>() }
        val grantedFiles = forge.aList { getForgery<File>() }
        whenever(mockPendingOrchestrator.getAllFiles()) doReturn pendingFiles
        whenever(mockGrantedOrchestrator.getAllFiles()) doReturn grantedFiles

        // When
        val result = testedOrchestrator.getAllFiles()

        // Then
        assertThat(result)
            .containsAll(pendingFiles)
            .containsAll(grantedFiles)
    }

    @Test
    fun `M return all files W getAllFiles() {updated consent}`(
        @Forgery initialConsent: TrackingConsent,
        @Forgery updatedConsent: TrackingConsent,
        forge: Forge
    ) {
        // Given
        instantiateTestedOrchestrator(initialConsent)
        val pendingFiles = forge.aList { getForgery<File>() }
        val grantedFiles = forge.aList { getForgery<File>() }
        whenever(mockPendingOrchestrator.getAllFiles()) doReturn pendingFiles
        whenever(mockGrantedOrchestrator.getAllFiles()) doReturn grantedFiles

        // When
        testedOrchestrator.onConsentUpdated(initialConsent, updatedConsent)
        val result = testedOrchestrator.getAllFiles()

        // Then
        assertThat(result)
            .containsAll(pendingFiles)
            .containsAll(grantedFiles)
    }

    // endregion

    // region getGrantedFiles

    @Test
    fun `M return granted files W getFlushableFiles()`(
        @Forgery consent: TrackingConsent,
        forge: Forge
    ) {
        // Given
        instantiateTestedOrchestrator(consent)
        val pendingFiles = forge.aList { getForgery<File>() }
        val grantedFiles = forge.aList { getForgery<File>() }
        whenever(mockPendingOrchestrator.getFlushableFiles()) doReturn pendingFiles
        whenever(mockGrantedOrchestrator.getFlushableFiles()) doReturn grantedFiles

        // When
        val result = testedOrchestrator.getFlushableFiles()

        // Then
        assertThat(result)
            .containsExactlyElementsOf(grantedFiles)
    }

    // endregion

    // region getRootDir

    @RepeatedTest(8)
    fun `M return null W getRootDir() {initial consent}`(
        @Forgery consent: TrackingConsent
    ) {
        // Given
        instantiateTestedOrchestrator(consent)

        // When
        val result = testedOrchestrator.getRootDir()

        // Then
        assertThat(result).isNull()
        verifyNoInteractions(mockPendingOrchestrator, mockGrantedOrchestrator)
    }

    @RepeatedTest(16)
    fun `M return null W getRootDir() {updated consent}`(
        @Forgery initialConsent: TrackingConsent,
        @Forgery updatedConsent: TrackingConsent
    ) {
        // Given
        instantiateTestedOrchestrator(initialConsent)

        // When
        testedOrchestrator.onConsentUpdated(initialConsent, updatedConsent)
        val result = testedOrchestrator.getRootDir()

        // Then
        assertThat(result).isNull()
        verifyNoInteractions(mockPendingOrchestrator, mockGrantedOrchestrator)
    }

    // endregion

    // region getMetadataFile

    @Test
    fun `M return pending meta file W getMetadataFile() {consent=PENDING}`(
        @Forgery fakeFile: File,
        @Forgery metaFile: File
    ) {
        // Given
        whenever(mockPendingOrchestrator.getMetadataFile(fakeFile)) doReturn metaFile

        // When
        val result = testedOrchestrator.getMetadataFile(fakeFile)

        // Then
        assertThat(result).isSameAs(metaFile)
        verifyNoInteractions(mockGrantedOrchestrator)
    }

    @Test
    fun `M return pending meta file W getMetadataFile() {consent=GRANTED then PENDING}`(
        @Forgery fakeFile: File,
        @Forgery metaFile: File
    ) {
        // Given
        instantiateTestedOrchestrator(TrackingConsent.GRANTED)
        runPendingRunnable()
        whenever(mockPendingOrchestrator.getMetadataFile(fakeFile)) doReturn metaFile

        // When
        testedOrchestrator.onConsentUpdated(TrackingConsent.GRANTED, TrackingConsent.PENDING)
        runPendingRunnable()
        val result = testedOrchestrator.getMetadataFile(fakeFile)

        // Then
        assertThat(result).isSameAs(metaFile)
        verifyNoInteractions(mockGrantedOrchestrator)
    }

    @Test
    fun `M return pending meta file W getMetadataFile() {consent=NOT_GRANTED then PENDING}`(
        @Forgery fakeFile: File,
        @Forgery metaFile: File
    ) {
        // Given
        instantiateTestedOrchestrator(TrackingConsent.NOT_GRANTED)
        runPendingRunnable()
        whenever(mockPendingOrchestrator.getMetadataFile(fakeFile)) doReturn metaFile

        // When
        testedOrchestrator.onConsentUpdated(TrackingConsent.NOT_GRANTED, TrackingConsent.PENDING)
        runPendingRunnable()
        val result = testedOrchestrator.getMetadataFile(fakeFile)

        // Then
        assertThat(result).isSameAs(metaFile)
        verifyNoInteractions(mockGrantedOrchestrator)
    }

    @Test
    fun `M return granted meta file W getMetadataFile() {consent=GRANTED}`(
        @Forgery fakeFile: File,
        @Forgery metaFile: File
    ) {
        // Given
        instantiateTestedOrchestrator(TrackingConsent.GRANTED)
        runPendingRunnable()
        whenever(mockGrantedOrchestrator.getMetadataFile(fakeFile)) doReturn metaFile

        // When
        val result = testedOrchestrator.getMetadataFile(fakeFile)

        // Then
        assertThat(result).isSameAs(metaFile)
        verifyNoInteractions(mockPendingOrchestrator)
    }

    @Test
    fun `M return granted meta file W getMetadataFile() {consent=NOT_GRANTED then GRANTED}`(
        @Forgery fakeFile: File,
        @Forgery metaFile: File
    ) {
        // Given
        instantiateTestedOrchestrator(TrackingConsent.NOT_GRANTED)
        runPendingRunnable()
        whenever(mockGrantedOrchestrator.getMetadataFile(fakeFile)) doReturn metaFile

        // When
        testedOrchestrator.onConsentUpdated(TrackingConsent.NOT_GRANTED, TrackingConsent.GRANTED)
        runPendingRunnable()
        val result = testedOrchestrator.getMetadataFile(fakeFile)

        // Then
        assertThat(result).isSameAs(metaFile)
        verifyNoInteractions(mockPendingOrchestrator)
    }

    @Test
    fun `M return granted meta file W getMetadataFile() {consent=PENDING then GRANTED}`(
        @Forgery fakeFile: File,
        @Forgery metaFile: File
    ) {
        // Given
        instantiateTestedOrchestrator(TrackingConsent.PENDING)
        runPendingRunnable()
        whenever(mockGrantedOrchestrator.getMetadataFile(fakeFile)) doReturn metaFile

        // When
        testedOrchestrator.onConsentUpdated(TrackingConsent.PENDING, TrackingConsent.GRANTED)
        runPendingRunnable()
        val result = testedOrchestrator.getMetadataFile(fakeFile)

        // Then
        assertThat(result).isSameAs(metaFile)
        verifyNoInteractions(mockPendingOrchestrator)
    }

    @Test
    fun `M return null file W getMetadataFile() {consent=NOT_GRANTED}`(
        @Forgery fakeFile: File
    ) {
        // Given
        instantiateTestedOrchestrator(TrackingConsent.NOT_GRANTED)
        runPendingRunnable()

        // When
        val result = testedOrchestrator.getMetadataFile(fakeFile)

        // Then
        assertThat(result).isNull()
        verifyNoInteractions(mockPendingOrchestrator, mockGrantedOrchestrator)
    }

    @Test
    fun `M return null file W getMetadataFile() {consent=GRANTED then NOT_GRANTED}`(
        @Forgery fakeFile: File
    ) {
        // Given
        instantiateTestedOrchestrator(TrackingConsent.GRANTED)
        runPendingRunnable()

        // When
        testedOrchestrator.onConsentUpdated(TrackingConsent.GRANTED, TrackingConsent.NOT_GRANTED)
        runPendingRunnable()
        val result = testedOrchestrator.getMetadataFile(fakeFile)

        // Then
        assertThat(result).isNull()
        verifyNoInteractions(mockPendingOrchestrator, mockGrantedOrchestrator)
    }

    @Test
    fun `M return null file W getMetadataFile() {consent=PENDING then NOT_GRANTED}`(
        @Forgery fakeFile: File
    ) {
        // Given
        instantiateTestedOrchestrator(TrackingConsent.PENDING)
        runPendingRunnable()

        // When
        testedOrchestrator.onConsentUpdated(TrackingConsent.PENDING, TrackingConsent.NOT_GRANTED)
        runPendingRunnable()
        val result = testedOrchestrator.getMetadataFile(fakeFile)

        // Then
        assertThat(result).isNull()
        verifyNoInteractions(mockPendingOrchestrator, mockGrantedOrchestrator)
    }

    // endregion

    // region onConsentUpdated

    @Test
    fun `M migrate data W onConsentUpdated() {GRANTED to GRANTED}`() {
        // When
        testedOrchestrator.onConsentUpdated(TrackingConsent.GRANTED, TrackingConsent.GRANTED)

        // Then
        argumentCaptor<Runnable> {
            verify(mockExecutorService).submit(capture())
            firstValue.run()
            verify(mockDataMigrator).migrateData(
                TrackingConsent.GRANTED,
                mockGrantedOrchestrator,
                TrackingConsent.GRANTED,
                mockGrantedOrchestrator
            )
        }
    }

    @Test
    fun `M migrate data W onConsentUpdated() {GRANTED to PENDING}`() {
        // When
        testedOrchestrator.onConsentUpdated(TrackingConsent.GRANTED, TrackingConsent.PENDING)

        // Then
        argumentCaptor<Runnable> {
            verify(mockExecutorService).submit(capture())
            firstValue.run()
            verify(mockDataMigrator).migrateData(
                TrackingConsent.GRANTED,
                mockGrantedOrchestrator,
                TrackingConsent.PENDING,
                mockPendingOrchestrator
            )
        }
    }

    @Test
    fun `M migrate data W onConsentUpdated() {GRANTED to NOT_GRANTED}`() {
        // When
        testedOrchestrator.onConsentUpdated(TrackingConsent.GRANTED, TrackingConsent.NOT_GRANTED)

        // Then
        argumentCaptor<Runnable> {
            verify(mockExecutorService).submit(capture())
            firstValue.run()
            verify(mockDataMigrator).migrateData(
                TrackingConsent.GRANTED,
                mockGrantedOrchestrator,
                TrackingConsent.NOT_GRANTED,
                ConsentAwareFileOrchestrator.NO_OP_ORCHESTRATOR
            )
        }
    }

    @Test
    fun `M migrate data W onConsentUpdated() {PENDING to GRANTED}`() {
        // When
        testedOrchestrator.onConsentUpdated(TrackingConsent.PENDING, TrackingConsent.GRANTED)

        // Then
        argumentCaptor<Runnable> {
            verify(mockExecutorService).submit(capture())
            firstValue.run()
            verify(mockDataMigrator).migrateData(
                TrackingConsent.PENDING,
                mockPendingOrchestrator,
                TrackingConsent.GRANTED,
                mockGrantedOrchestrator
            )
        }
    }

    @Test
    fun `M migrate data W onConsentUpdated() {PENDING to PENDING}`() {
        // When
        testedOrchestrator.onConsentUpdated(TrackingConsent.PENDING, TrackingConsent.PENDING)

        // Then
        argumentCaptor<Runnable> {
            verify(mockExecutorService).submit(capture())
            firstValue.run()
            verify(mockDataMigrator).migrateData(
                TrackingConsent.PENDING,
                mockPendingOrchestrator,
                TrackingConsent.PENDING,
                mockPendingOrchestrator
            )
        }
    }

    @Test
    fun `M migrate data W onConsentUpdated() {PENDING to NOT_GRANTED}`() {
        // When
        testedOrchestrator.onConsentUpdated(TrackingConsent.PENDING, TrackingConsent.NOT_GRANTED)

        // Then
        argumentCaptor<Runnable> {
            verify(mockExecutorService).submit(capture())
            firstValue.run()
            verify(mockDataMigrator).migrateData(
                TrackingConsent.PENDING,
                mockPendingOrchestrator,
                TrackingConsent.NOT_GRANTED,
                ConsentAwareFileOrchestrator.NO_OP_ORCHESTRATOR
            )
        }
    }

    @Test
    fun `M migrate data W onConsentUpdated() {NOT_GRANTED to GRANTED}`() {
        // When
        testedOrchestrator.onConsentUpdated(TrackingConsent.NOT_GRANTED, TrackingConsent.GRANTED)

        // Then
        argumentCaptor<Runnable> {
            verify(mockExecutorService).submit(capture())
            firstValue.run()
            verify(mockDataMigrator).migrateData(
                TrackingConsent.NOT_GRANTED,
                ConsentAwareFileOrchestrator.NO_OP_ORCHESTRATOR,
                TrackingConsent.GRANTED,
                mockGrantedOrchestrator
            )
        }
    }

    @Test
    fun `M migrate data W onConsentUpdated() {NOT_GRANTED to PENDING}`() {
        // When
        testedOrchestrator.onConsentUpdated(TrackingConsent.NOT_GRANTED, TrackingConsent.PENDING)

        // Then
        argumentCaptor<Runnable> {
            verify(mockExecutorService).submit(capture())
            firstValue.run()
            verify(mockDataMigrator).migrateData(
                TrackingConsent.NOT_GRANTED,
                ConsentAwareFileOrchestrator.NO_OP_ORCHESTRATOR,
                TrackingConsent.PENDING,
                mockPendingOrchestrator
            )
        }
    }

    @Test
    fun `M migrate data W onConsentUpdated() {NOT_GRANTED to NOT_GRANTED}`() {
        // When
        testedOrchestrator.onConsentUpdated(
            TrackingConsent.NOT_GRANTED,
            TrackingConsent.NOT_GRANTED
        )

        // Then
        argumentCaptor<Runnable> {
            verify(mockExecutorService).submit(capture())
            firstValue.run()
            verify(mockDataMigrator).migrateData(
                TrackingConsent.NOT_GRANTED,
                ConsentAwareFileOrchestrator.NO_OP_ORCHESTRATOR,
                TrackingConsent.NOT_GRANTED,
                ConsentAwareFileOrchestrator.NO_OP_ORCHESTRATOR
            )
        }
    }

    @Test
    fun `M warn W onConsentUpdated() {submission rejected}`(
        @Forgery previousConsent: TrackingConsent,
        @Forgery newConsent: TrackingConsent,
        @StringForgery errorMessage: String
    ) {
        // Given
        val exception = RejectedExecutionException(errorMessage)
        whenever(mockExecutorService.submit(any())) doThrow exception

        // When
        testedOrchestrator.onConsentUpdated(previousConsent, newConsent)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            "Unable to schedule Data migration task on the executor",
            exception
        )
    }

    // endregion

    private fun instantiateTestedOrchestrator(consent: TrackingConsent) {
        whenever(mockConsentProvider.getConsent()) doReturn consent
        testedOrchestrator = ConsentAwareFileOrchestrator(
            mockConsentProvider,
            mockPendingOrchestrator,
            mockGrantedOrchestrator,
            mockDataMigrator,
            mockExecutorService,
            mockInternalLogger
        )
    }

    private fun runPendingRunnable() {
        argumentCaptor<Runnable> {
            verify(mockExecutorService, atLeast(0)).submit(capture())
            allValues.forEach { it.run() }
        }
    }
}
