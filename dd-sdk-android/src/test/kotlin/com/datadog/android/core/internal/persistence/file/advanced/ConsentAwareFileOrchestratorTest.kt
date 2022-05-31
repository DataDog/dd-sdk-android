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
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.reset
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
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
internal class ConsentAwareFileOrchestratorTest {

    lateinit var testedOrchestrator: ConsentAwareFileOrchestrator

    @Mock
    lateinit var mockConsentProvider: ConsentProvider

    @Mock
    lateinit var mockPendingOrchestrator: FileOrchestrator

    @Mock
    lateinit var mockGrantedOrchestrator: FileOrchestrator

    @Mock
    lateinit var mockDataMigrator: DataMigrator<TrackingConsent>

    @BeforeEach
    fun `set up`() {
        instantiateTestedOrchestrator(TrackingConsent.PENDING)
        reset(mockDataMigrator, mockConsentProvider)
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
        verify(mockDataMigrator).migrateData(
            null,
            mockPendingOrchestrator,
            TrackingConsent.GRANTED,
            mockGrantedOrchestrator
        )
    }

    @Test
    fun `𝕄 migrate data 𝕎 init() {PENDING}`() {
        // When
        instantiateTestedOrchestrator(TrackingConsent.PENDING)

        // Then
        verify(mockDataMigrator).migrateData(
            null,
            mockPendingOrchestrator,
            TrackingConsent.PENDING,
            mockPendingOrchestrator
        )
    }

    @Test
    fun `𝕄 migrate data 𝕎 init() {NOT_GRANTED}`() {
        // When
        instantiateTestedOrchestrator(TrackingConsent.NOT_GRANTED)

        // Then
        verify(mockDataMigrator).migrateData(
            null,
            mockPendingOrchestrator,
            TrackingConsent.NOT_GRANTED,
            ConsentAwareFileOrchestrator.NO_OP_ORCHESTRATOR
        )
    }

    // endregion

    // region getWritableFile

    @Test
    fun `𝕄 return pending writable file 𝕎 getWritableFile() {consent=PENDING}`(
        @Forgery file: File
    ) {
        // Given
        whenever(mockPendingOrchestrator.getWritableFile()) doReturn file

        // When
        val result = testedOrchestrator.getWritableFile()

        // Then
        assertThat(result).isSameAs(file)
        verifyZeroInteractions(mockGrantedOrchestrator)
    }

    @Test
    fun `𝕄 return pending writable file 𝕎 getWritableFile() {consent=GRANTED then PENDING}`(
        @Forgery file: File
    ) {
        // Given
        instantiateTestedOrchestrator(TrackingConsent.GRANTED)
        whenever(mockPendingOrchestrator.getWritableFile()) doReturn file

        // When
        testedOrchestrator.onConsentUpdated(TrackingConsent.GRANTED, TrackingConsent.PENDING)
        val result = testedOrchestrator.getWritableFile()

        // Then
        assertThat(result).isSameAs(file)
        verifyZeroInteractions(mockGrantedOrchestrator)
    }

    @Test
    fun `𝕄 return pending writable file 𝕎 getWritableFile() {consent=NOT_GRANTED then PENDING}`(
        @Forgery file: File
    ) {
        // Given
        instantiateTestedOrchestrator(TrackingConsent.NOT_GRANTED)
        whenever(mockPendingOrchestrator.getWritableFile()) doReturn file

        // When
        testedOrchestrator.onConsentUpdated(TrackingConsent.NOT_GRANTED, TrackingConsent.PENDING)
        val result = testedOrchestrator.getWritableFile()

        // Then
        assertThat(result).isSameAs(file)
        verifyZeroInteractions(mockGrantedOrchestrator)
    }

    @Test
    fun `𝕄 return granted writable file 𝕎 getWritableFile() {consent=GRANTED}`(
        @Forgery file: File
    ) {
        // Given
        instantiateTestedOrchestrator(TrackingConsent.GRANTED)
        whenever(mockGrantedOrchestrator.getWritableFile()) doReturn file

        // When
        val result = testedOrchestrator.getWritableFile()

        // Then
        assertThat(result).isSameAs(file)
        verifyZeroInteractions(mockPendingOrchestrator)
    }

    @Test
    fun `𝕄 return granted writable file 𝕎 getWritableFile() {consent=NOT_GRANTED then GRANTED}`(
        @Forgery file: File
    ) {
        // Given
        instantiateTestedOrchestrator(TrackingConsent.NOT_GRANTED)
        whenever(mockGrantedOrchestrator.getWritableFile()) doReturn file

        // When
        testedOrchestrator.onConsentUpdated(TrackingConsent.NOT_GRANTED, TrackingConsent.GRANTED)
        val result = testedOrchestrator.getWritableFile()

        // Then
        assertThat(result).isSameAs(file)
        verifyZeroInteractions(mockPendingOrchestrator)
    }

    @Test
    fun `𝕄 return granted writable file 𝕎 getWritableFile() {consent=PENDING then GRANTED}`(
        @Forgery file: File
    ) {
        // Given
        instantiateTestedOrchestrator(TrackingConsent.PENDING)
        whenever(mockGrantedOrchestrator.getWritableFile()) doReturn file

        // When
        testedOrchestrator.onConsentUpdated(TrackingConsent.PENDING, TrackingConsent.GRANTED)
        val result = testedOrchestrator.getWritableFile()

        // Then
        assertThat(result).isSameAs(file)
        verifyZeroInteractions(mockPendingOrchestrator)
    }

    @Test
    fun `𝕄 return null file 𝕎 getWritableFile() {consent=NOT_GRANTED}`() {
        // Given
        instantiateTestedOrchestrator(TrackingConsent.NOT_GRANTED)

        // When
        val result = testedOrchestrator.getWritableFile()

        // Then
        assertThat(result).isNull()
        verifyZeroInteractions(mockPendingOrchestrator, mockGrantedOrchestrator)
    }

    @Test
    fun `𝕄 return null file 𝕎 getWritableFile() {consent=GRANTED then NOT_GRANTED}`() {
        // Given
        instantiateTestedOrchestrator(TrackingConsent.GRANTED)

        // When
        testedOrchestrator.onConsentUpdated(TrackingConsent.GRANTED, TrackingConsent.NOT_GRANTED)
        val result = testedOrchestrator.getWritableFile()

        // Then
        assertThat(result).isNull()
        verifyZeroInteractions(mockPendingOrchestrator, mockGrantedOrchestrator)
    }

    @Test
    fun `𝕄 return null file 𝕎 getWritableFile() {consent=PENDING then NOT_GRANTED}`() {
        // Given
        instantiateTestedOrchestrator(TrackingConsent.PENDING)

        // When
        testedOrchestrator.onConsentUpdated(TrackingConsent.PENDING, TrackingConsent.NOT_GRANTED)
        val result = testedOrchestrator.getWritableFile()

        // Then
        assertThat(result).isNull()
        verifyZeroInteractions(mockPendingOrchestrator, mockGrantedOrchestrator)
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
        verifyZeroInteractions(mockPendingOrchestrator)
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
        verifyZeroInteractions(mockPendingOrchestrator)
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
        verifyZeroInteractions(mockPendingOrchestrator, mockGrantedOrchestrator)
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
        verifyZeroInteractions(mockPendingOrchestrator, mockGrantedOrchestrator)
    }

    // endregion

    // region onConsentUpdated

    @Test
    fun `𝕄 migrate data 𝕎 onConsentUpdated() {GRANTED to GRANTED}`() {
        // When
        testedOrchestrator.onConsentUpdated(TrackingConsent.GRANTED, TrackingConsent.GRANTED)

        // Then
        verify(mockDataMigrator).migrateData(
            TrackingConsent.GRANTED,
            mockGrantedOrchestrator,
            TrackingConsent.GRANTED,
            mockGrantedOrchestrator
        )
    }

    @Test
    fun `𝕄 migrate data 𝕎 onConsentUpdated() {GRANTED to PENDING}`() {
        // When
        testedOrchestrator.onConsentUpdated(TrackingConsent.GRANTED, TrackingConsent.PENDING)

        // Then
        verify(mockDataMigrator).migrateData(
            TrackingConsent.GRANTED,
            mockGrantedOrchestrator,
            TrackingConsent.PENDING,
            mockPendingOrchestrator
        )
    }

    @Test
    fun `𝕄 migrate data 𝕎 onConsentUpdated() {GRANTED to NOT_GRANTED}`() {
        // When
        testedOrchestrator.onConsentUpdated(TrackingConsent.GRANTED, TrackingConsent.NOT_GRANTED)

        // Then
        verify(mockDataMigrator).migrateData(
            TrackingConsent.GRANTED,
            mockGrantedOrchestrator,
            TrackingConsent.NOT_GRANTED,
            ConsentAwareFileOrchestrator.NO_OP_ORCHESTRATOR
        )
    }

    @Test
    fun `𝕄 migrate data 𝕎 onConsentUpdated() {PENDING to GRANTED}`() {
        // When
        testedOrchestrator.onConsentUpdated(TrackingConsent.PENDING, TrackingConsent.GRANTED)

        // Then
        verify(mockDataMigrator).migrateData(
            TrackingConsent.PENDING,
            mockPendingOrchestrator,
            TrackingConsent.GRANTED,
            mockGrantedOrchestrator
        )
    }

    @Test
    fun `𝕄 migrate data 𝕎 onConsentUpdated() {PENDING to PENDING}`() {
        // When
        testedOrchestrator.onConsentUpdated(TrackingConsent.PENDING, TrackingConsent.PENDING)

        // Then
        verify(mockDataMigrator).migrateData(
            TrackingConsent.PENDING,
            mockPendingOrchestrator,
            TrackingConsent.PENDING,
            mockPendingOrchestrator
        )
    }

    @Test
    fun `𝕄 migrate data 𝕎 onConsentUpdated() {PENDING to NOT_GRANTED}`() {
        // When
        testedOrchestrator.onConsentUpdated(TrackingConsent.PENDING, TrackingConsent.NOT_GRANTED)

        // Then
        verify(mockDataMigrator).migrateData(
            TrackingConsent.PENDING,
            mockPendingOrchestrator,
            TrackingConsent.NOT_GRANTED,
            ConsentAwareFileOrchestrator.NO_OP_ORCHESTRATOR
        )
    }

    @Test
    fun `𝕄 migrate data 𝕎 onConsentUpdated() {NOT_GRANTED to GRANTED}`() {
        // When
        testedOrchestrator.onConsentUpdated(TrackingConsent.NOT_GRANTED, TrackingConsent.GRANTED)

        // Then
        verify(mockDataMigrator).migrateData(
            TrackingConsent.NOT_GRANTED,
            ConsentAwareFileOrchestrator.NO_OP_ORCHESTRATOR,
            TrackingConsent.GRANTED,
            mockGrantedOrchestrator
        )
    }

    @Test
    fun `𝕄 migrate data 𝕎 onConsentUpdated() {NOT_GRANTED to PENDING}`() {
        // When
        testedOrchestrator.onConsentUpdated(TrackingConsent.NOT_GRANTED, TrackingConsent.PENDING)

        // Then
        verify(mockDataMigrator).migrateData(
            TrackingConsent.NOT_GRANTED,
            ConsentAwareFileOrchestrator.NO_OP_ORCHESTRATOR,
            TrackingConsent.PENDING,
            mockPendingOrchestrator
        )
    }

    @Test
    fun `𝕄 migrate data 𝕎 onConsentUpdated() {NOT_GRANTED to NOT_GRANTED}`() {
        // When
        testedOrchestrator.onConsentUpdated(
            TrackingConsent.NOT_GRANTED,
            TrackingConsent.NOT_GRANTED
        )

        // Then
        verify(mockDataMigrator).migrateData(
            TrackingConsent.NOT_GRANTED,
            ConsentAwareFileOrchestrator.NO_OP_ORCHESTRATOR,
            TrackingConsent.NOT_GRANTED,
            ConsentAwareFileOrchestrator.NO_OP_ORCHESTRATOR
        )
    }

    // endregion

    private fun instantiateTestedOrchestrator(consent: TrackingConsent) {
        whenever(mockConsentProvider.getConsent()) doReturn consent
        testedOrchestrator = ConsentAwareFileOrchestrator(
            mockConsentProvider,
            mockPendingOrchestrator,
            mockGrantedOrchestrator,
            mockDataMigrator
        )
    }
}
