/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.advanced

import android.app.Application
import com.datadog.android.core.internal.persistence.file.batch.BatchFileOrchestrator
import com.datadog.android.core.internal.privacy.ConsentProvider
import com.datadog.android.log.Logger
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
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
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class FeatureFileOrchestratorTest {

    @Mock
    lateinit var mockConsentProvider: ConsentProvider

    @Mock
    lateinit var mockExecutorService: ExecutorService

    @Mock
    lateinit var mockLogHandler: LogHandler

    @StringForgery
    lateinit var fakeFeatureName: String

    @Forgery
    lateinit var fakeConsent: TrackingConsent

    @BeforeEach
    fun `set up`() {
        whenever(mockConsentProvider.getConsent()) doReturn fakeConsent
    }

    @Test
    fun `𝕄 initialise pending orchestrator in cache dir 𝕎 init()`() {
        // Given

        // When
        val orchestrator = FeatureFileOrchestrator(
            mockConsentProvider,
            appContext.mockInstance,
            fakeFeatureName,
            mockExecutorService,
            Logger(mockLogHandler)
        )

        // Then
        assertThat(orchestrator.pendingOrchestrator)
            .isInstanceOf(BatchFileOrchestrator::class.java)
        assertThat(orchestrator.pendingOrchestrator.getRootDir())
            .isEqualTo(File(appContext.fakeCacheDir, "dd-$fakeFeatureName-pending-v2"))
    }

    @Test
    fun `𝕄 initialise granted orchestrator in cache dir 𝕎 init()`() {
        // Given

        // When
        val orchestrator = FeatureFileOrchestrator(
            mockConsentProvider,
            appContext.mockInstance,
            fakeFeatureName,
            mockExecutorService,
            Logger(mockLogHandler)
        )

        // Then
        assertThat(orchestrator.grantedOrchestrator)
            .isInstanceOf(BatchFileOrchestrator::class.java)
        assertThat(orchestrator.grantedOrchestrator.getRootDir())
            .isEqualTo(File(appContext.fakeCacheDir, "dd-$fakeFeatureName-v2"))
    }

    @Test
    fun `𝕄 use a consent aware migrator 𝕎 init()`() {
        // Given

        // When
        val orchestrator = FeatureFileOrchestrator(
            mockConsentProvider,
            appContext.mockInstance,
            fakeFeatureName,
            mockExecutorService,
            Logger(mockLogHandler)
        )

        // Then
        assertThat(orchestrator.dataMigrator)
            .isInstanceOf(ConsentAwareFileMigrator::class.java)
    }

    companion object {
        val appContext = ApplicationContextTestConfiguration(Application::class.java)

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(appContext)
        }
    }
}
