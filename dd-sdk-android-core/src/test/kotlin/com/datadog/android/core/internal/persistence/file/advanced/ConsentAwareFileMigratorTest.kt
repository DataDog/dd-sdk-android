/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.advanced

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.persistence.file.FileMover
import com.datadog.android.core.internal.persistence.file.FileOrchestrator
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.File
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class ConsentAwareFileMigratorTest {

    lateinit var testedMigrator: DataMigrator<TrackingConsent>

    @Mock
    lateinit var mockPreviousOrchestrator: FileOrchestrator

    @Mock
    lateinit var mockNewOrchestrator: FileOrchestrator

    @Mock
    lateinit var mockFileMover: FileMover

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @BeforeEach
    fun `set up`() {
        testedMigrator = ConsentAwareFileMigrator(
            mockFileMover,
            mockInternalLogger
        )
    }

    @RepeatedTest(8)
    fun `𝕄 wipe pending data 𝕎 migrateData() {null to any}`(
        @Forgery consent: TrackingConsent,
        @Forgery pendingDir: File
    ) {
        // Given
        whenever(mockPreviousOrchestrator.getRootDir()) doReturn pendingDir
        whenever(mockFileMover.delete(pendingDir)) doReturn true

        // When
        testedMigrator.migrateData(
            null,
            mockPreviousOrchestrator,
            consent,
            mockNewOrchestrator
        )

        // Then
        verify(mockFileMover).delete(pendingDir)
    }

    @Test
    fun `𝕄 wipe pending data 𝕎 migrateData() {PENDING to NOT_GRANTED}`(
        @Forgery pendingDir: File
    ) {
        // Given
        whenever(mockPreviousOrchestrator.getRootDir()) doReturn pendingDir
        whenever(mockFileMover.delete(pendingDir)) doReturn true

        // When
        testedMigrator.migrateData(
            TrackingConsent.PENDING,
            mockPreviousOrchestrator,
            TrackingConsent.NOT_GRANTED,
            mockNewOrchestrator
        )

        // Then
        verify(mockFileMover).delete(pendingDir)
    }

    @Test
    fun `𝕄 wipe pending data 𝕎 migrateData() {GRANTED to PENDING}`(
        @Forgery pendingDir: File
    ) {
        // Given
        whenever(mockNewOrchestrator.getRootDir()) doReturn pendingDir
        whenever(mockFileMover.delete(pendingDir)) doReturn true

        // When
        testedMigrator.migrateData(
            TrackingConsent.GRANTED,
            mockPreviousOrchestrator,
            TrackingConsent.PENDING,
            mockNewOrchestrator
        )

        // Then
        verify(mockFileMover).delete(pendingDir)
    }

    @Test
    fun `𝕄 wipe pending data 𝕎 migrateData() {NOT_GRANTED to PENDING}`(
        @Forgery pendingDir: File
    ) {
        // Given
        whenever(mockNewOrchestrator.getRootDir()) doReturn pendingDir
        whenever(mockFileMover.delete(pendingDir)) doReturn true

        // When
        testedMigrator.migrateData(
            TrackingConsent.NOT_GRANTED,
            mockPreviousOrchestrator,
            TrackingConsent.PENDING,
            mockNewOrchestrator
        )

        // Then
        verify(mockFileMover).delete(pendingDir)
    }

    @Test
    fun `𝕄 move pending data 𝕎 migrateData() {PENDING to GRANTED}`(
        @Forgery pendingDir: File,
        @Forgery grantedDir: File
    ) {
        // Given
        whenever(mockPreviousOrchestrator.getRootDir()) doReturn pendingDir
        whenever(mockNewOrchestrator.getRootDir()) doReturn grantedDir
        whenever(mockFileMover.moveFiles(pendingDir, grantedDir)) doReturn true

        // When
        testedMigrator.migrateData(
            TrackingConsent.PENDING,
            mockPreviousOrchestrator,
            TrackingConsent.GRANTED,
            mockNewOrchestrator
        )

        // Then
        verify(mockFileMover).moveFiles(pendingDir, grantedDir)
    }

    @RepeatedTest(8)
    fun `𝕄 do nothing 𝕎 migrateData() {x to x}`(
        @Forgery consent: TrackingConsent
    ) {
        // When
        testedMigrator.migrateData(
            consent,
            mockPreviousOrchestrator,
            consent,
            mockNewOrchestrator
        )

        // Then
        verifyNoInteractions(mockFileMover)
    }

    @Test
    fun `𝕄 do nothing 𝕎 migrateData() {GRANTED to NOT_GRANTED}`() {
        // When
        testedMigrator.migrateData(
            TrackingConsent.GRANTED,
            mockPreviousOrchestrator,
            TrackingConsent.NOT_GRANTED,
            mockNewOrchestrator
        )

        // Then
        verifyNoInteractions(mockFileMover)
    }

    @Test
    fun `𝕄 do nothing 𝕎 migrateData() {NOT_GRANTED to GRANTED}`() {
        // When
        testedMigrator.migrateData(
            TrackingConsent.NOT_GRANTED,
            mockPreviousOrchestrator,
            TrackingConsent.GRANTED,
            mockNewOrchestrator
        )

        // Then
        verifyNoInteractions(mockFileMover)
    }
}
