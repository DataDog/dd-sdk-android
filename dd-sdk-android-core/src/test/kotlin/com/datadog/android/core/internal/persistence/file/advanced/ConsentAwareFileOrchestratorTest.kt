/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.advanced

import com.datadog.android.core.internal.persistence.file.FileOrchestrator
import com.datadog.android.core.internal.privacy.ConsentProvider
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.InternalLogger
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
    fun `𝕄 registers as listener 𝕎 init()`(
        @Forgery consent: TrackingConsent
    ) {
        // When
        instantiateTestedOrchestrator(consent)

        // Then
        verify(mockConsentProvider).registerCallback(testedOrchestrator)
    }

    @Test
    fun `𝕄 migrate data 𝕎 init() {GRANTED}`() {
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
    fun `𝕄 migrate data 𝕎 init() {PENDING}`() {
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
    fun `𝕄 migrate data 𝕎 init() {NOT_GRANTED}`() {
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
    fun `𝕄 return pending writable file 𝕎 getWritableFile() {consent=PENDING}`(
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
    fun `𝕄 return pending writable file 𝕎 getWritableFile() {consent=GRANTED then PENDING}`(
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
    fun `𝕄 return pending writable file 𝕎 getWritableFile() {consent=NOT_GRANTED then PENDING}`(
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
    fun `𝕄 return granted writable file 𝕎 getWritableFile() {consent=GRANTED}`(
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
    fun `𝕄 return granted writable file 𝕎 getWritableFile() {consent=NOT_GRANTED then GRANTED}`(
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
    fun `𝕄 return granted writable file 𝕎 getWritableFile() {consent=PENDING then GRANTED}`(
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
    fun `𝕄 return null file 𝕎 getWritableFile() {consent=NOT_GRANTED}`(
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
    fun `𝕄 return null file 𝕎 getWritableFile() {consent=GRANTED then NOT_GRANTED}`(
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
    fun `𝕄 return null file 𝕎 getWritableFile() {consent=PENDING then NOT_GRANTED}`(
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
    fun `𝕄 return granted file 𝕎 getReadableFile() {initial consent}`(
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
    fun `𝕄 return granted file 𝕎 getReadableFile() {updated consent}`(
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
    fun `𝕄 return all files 𝕎 getAllFiles() {initial consent}`(
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
    fun `𝕄 return all files 𝕎 getAllFiles() {updated consent}`(
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
    fun `𝕄 return granted files 𝕎 getFlushableFiles()`(
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
    fun `𝕄 return null 𝕎 getRootDir() {initial consent}`(
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
    fun `𝕄 return null 𝕎 getRootDir() {updated consent}`(
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
    fun `𝕄 return pending meta file 𝕎 getMetadataFile() {consent=PENDING}`(
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
    fun `𝕄 return pending meta file 𝕎 getMetadataFile() {consent=GRANTED then PENDING}`(
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
    fun `𝕄 return pending meta file 𝕎 getMetadataFile() {consent=NOT_GRANTED then PENDING}`(
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
    fun `𝕄 return granted meta file 𝕎 getMetadataFile() {consent=GRANTED}`(
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
    fun `𝕄 return granted meta file 𝕎 getMetadataFile() {consent=NOT_GRANTED then GRANTED}`(
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
    fun `𝕄 return granted meta file 𝕎 getMetadataFile() {consent=PENDING then GRANTED}`(
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
    fun `𝕄 return null file 𝕎 getMetadataFile() {consent=NOT_GRANTED}`(
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
    fun `𝕄 return null file 𝕎 getMetadataFile() {consent=GRANTED then NOT_GRANTED}`(
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
    fun `𝕄 return null file 𝕎 getMetadataFile() {consent=PENDING then NOT_GRANTED}`(
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
    fun `𝕄 migrate data 𝕎 onConsentUpdated() {GRANTED to GRANTED}`() {
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
    fun `𝕄 migrate data 𝕎 onConsentUpdated() {GRANTED to PENDING}`() {
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
    fun `𝕄 migrate data 𝕎 onConsentUpdated() {GRANTED to NOT_GRANTED}`() {
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
    fun `𝕄 migrate data 𝕎 onConsentUpdated() {PENDING to GRANTED}`() {
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
    fun `𝕄 migrate data 𝕎 onConsentUpdated() {PENDING to PENDING}`() {
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
    fun `𝕄 migrate data 𝕎 onConsentUpdated() {PENDING to NOT_GRANTED}`() {
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
    fun `𝕄 migrate data 𝕎 onConsentUpdated() {NOT_GRANTED to GRANTED}`() {
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
    fun `𝕄 migrate data 𝕎 onConsentUpdated() {NOT_GRANTED to PENDING}`() {
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
    fun `𝕄 migrate data 𝕎 onConsentUpdated() {NOT_GRANTED to NOT_GRANTED}`() {
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
    fun `𝕄 warn 𝕎 onConsentUpdated() {submission rejected}`(
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
        verify(mockInternalLogger).log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            DataMigrator.ERROR_REJECTED,
            throwable = exception
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
