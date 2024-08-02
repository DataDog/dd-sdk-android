/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.advanced

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.metrics.MetricsDispatcher
import com.datadog.android.core.internal.persistence.file.FilePersistenceConfig
import com.datadog.android.core.internal.persistence.file.batch.BatchFileOrchestrator
import com.datadog.android.core.internal.privacy.ConsentProvider
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.File
import java.util.concurrent.ExecutorService

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class FeatureFileOrchestratorTest {

    @Mock
    lateinit var mockConsentProvider: ConsentProvider

    @Mock
    lateinit var mockExecutorService: ExecutorService

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @StringForgery
    lateinit var fakeFeatureName: String

    @Forgery
    lateinit var fakeConsent: TrackingConsent

    @TempDir
    lateinit var fakeStorageDir: File

    @Forgery
    lateinit var fakeFilePersistenceConfig: FilePersistenceConfig

    @Mock
    lateinit var mockMetricsDispatcher: MetricsDispatcher

    @BeforeEach
    fun `set up`() {
        whenever(mockConsentProvider.getConsent()) doReturn fakeConsent
    }

    @Test
    fun `M initialise pending orchestrator in cache dir W init()`() {
        // Given

        // When
        val orchestrator = FeatureFileOrchestrator(
            mockConsentProvider,
            fakeStorageDir,
            fakeFeatureName,
            mockExecutorService,
            fakeFilePersistenceConfig,
            mockInternalLogger,
            mockMetricsDispatcher
        )

        // Then
        assertThat(orchestrator.pendingOrchestrator)
            .isInstanceOf(BatchFileOrchestrator::class.java)
        assertThat(orchestrator.pendingOrchestrator.getRootDir())
            .isEqualTo(File(fakeStorageDir, "$fakeFeatureName-pending-v2"))
    }

    @Test
    fun `M initialise granted orchestrator in cache dir W init()`() {
        // Given

        // When
        val orchestrator = FeatureFileOrchestrator(
            mockConsentProvider,
            fakeStorageDir,
            fakeFeatureName,
            mockExecutorService,
            fakeFilePersistenceConfig,
            mockInternalLogger,
            mockMetricsDispatcher
        )

        // Then
        assertThat(orchestrator.grantedOrchestrator)
            .isInstanceOf(BatchFileOrchestrator::class.java)
        assertThat(orchestrator.grantedOrchestrator.getRootDir())
            .isEqualTo(File(fakeStorageDir, "$fakeFeatureName-v2"))
    }

    @Test
    fun `M use a consent aware migrator W init()`() {
        // Given

        // When
        val orchestrator = FeatureFileOrchestrator(
            mockConsentProvider,
            fakeStorageDir,
            fakeFeatureName,
            mockExecutorService,
            fakeFilePersistenceConfig,
            mockInternalLogger,
            mockMetricsDispatcher
        )

        // Then
        assertThat(orchestrator.dataMigrator)
            .isInstanceOf(ConsentAwareFileMigrator::class.java)
    }
}
