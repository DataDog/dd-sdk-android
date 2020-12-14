/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal

import android.app.Application
import com.datadog.android.Configuration
import com.datadog.android.core.internal.data.Reader
import com.datadog.android.core.internal.data.upload.DataUploadRunnable
import com.datadog.android.core.internal.data.upload.DataUploadScheduler
import com.datadog.android.core.internal.data.upload.NoOpUploadScheduler
import com.datadog.android.core.internal.data.upload.UploadScheduler
import com.datadog.android.core.internal.domain.NoOpPersistenceStrategy
import com.datadog.android.core.internal.domain.PersistenceStrategy
import com.datadog.android.core.internal.net.DataUploader
import com.datadog.android.plugin.DatadogPluginConfig
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockContext
import com.datadog.android.utils.mockCoreFeature
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
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
    ExtendWith(ApiLevelExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal abstract class SdkFeatureTest<T : Any, C : Configuration.Feature, F : SdkFeature<T, C>> {

    lateinit var testedFeature: F

    lateinit var mockAppContext: Application

    @Mock
    lateinit var mockPersistenceStrategy: PersistenceStrategy<T>

    @Mock
    lateinit var mockReader: Reader

    @Mock
    lateinit var mockUploader: DataUploader

    lateinit var fakeConfig: C

    @StringForgery
    lateinit var fakePackageName: String

    @StringForgery
    lateinit var fakeEnvName: String

    @StringForgery(regex = "\\d(\\.\\d){3}")
    lateinit var fakePackageVersion: String

    @Forgery
    lateinit var fakeConsent: TrackingConsent

    @BeforeEach
    fun `set up`(forge: Forge) {

        mockCoreFeature(
            packageName = fakePackageName,
            packageVersion = fakePackageVersion,
            envName = fakeEnvName,
            trackingConsent = fakeConsent
        )

        fakePackageName = forge.anAlphabeticalString()
        fakePackageVersion = forge.aStringMatching("\\d(\\.\\d){3}")

        mockAppContext = mockContext(fakePackageName, fakePackageVersion)
        whenever(mockAppContext.applicationContext) doReturn mockAppContext
        whenever(mockPersistenceStrategy.getReader()) doReturn mockReader

        fakeConfig = forgeConfiguration(forge)
        testedFeature = createTestedFeature()
    }

    @AfterEach
    fun `tear down`() {
        testedFeature.stop()
        CoreFeature.stop()
    }

    abstract fun createTestedFeature(): F

    abstract fun forgeConfiguration(forge: Forge): C

    @Test
    fun `𝕄 mark itself as initialized 𝕎 initialize()`() {
        // When
        testedFeature.initialize(mockAppContext, fakeConfig)

        // Then
        assertThat(testedFeature.isInitialized()).isTrue()
    }

    @Test
    fun `𝕄 store upload url 𝕎 initialize()`() {
        // When
        testedFeature.initialize(mockAppContext, fakeConfig)

        // Then
        assertThat(testedFeature.endpointUrl).isEqualTo(fakeConfig.endpointUrl)
    }

    @Test
    fun `𝕄 initialize uploader 𝕎 initialize()`() {
        // When
        testedFeature.initialize(mockAppContext, fakeConfig)

        // Then
        assertThat(testedFeature.uploadScheduler)
            .isInstanceOf(DataUploadScheduler::class.java)
        argumentCaptor<Runnable> {
            verify(CoreFeature.uploadExecutorService).schedule(
                any(),
                eq(DataUploadRunnable.DEFAULT_DELAY_MS),
                eq(TimeUnit.MILLISECONDS)
            )
        }
    }

    @Test
    fun `𝕄 register plugins 𝕎 initialize()`() {
        // Given
        assumeTrue(fakeConfig.plugins.isNotEmpty())

        // When
        testedFeature.initialize(mockAppContext, fakeConfig)

        // Then
        argumentCaptor<DatadogPluginConfig> {
            fakeConfig.plugins.forEach {
                verify(it).register(capture())
            }
            allValues.forEach {
                assertThat(it.context).isEqualTo(mockAppContext)
                assertThat(it.serviceName).isEqualTo(CoreFeature.serviceName)
                assertThat(it.envName).isEqualTo(CoreFeature.envName)
                assertThat(it.featurePersistenceDirName)
                    .isEqualTo(testedFeature.authorizedFolderName)
                assertThat(it.context).isEqualTo(mockAppContext)
                assertThat(it.trackingConsent).isEqualTo(fakeConsent)
            }
        }
    }

    @Test
    fun `𝕄 register plugins as TrackingConsentCallback 𝕎 initialize()`() {
        // Given
        assumeTrue(fakeConfig.plugins.isNotEmpty())

        // When
        testedFeature.initialize(mockAppContext, fakeConfig)

        // Then
        fakeConfig.plugins.forEach {
            verify(CoreFeature.trackingConsentProvider).registerCallback(it)
        }
    }

    @Test
    fun `𝕄 unregister plugins 𝕎 stop()`() {
        // Given
        assumeTrue(fakeConfig.plugins.isNotEmpty())
        testedFeature.initialize(mockAppContext, fakeConfig)
        fakeConfig.plugins.forEach {
            verify(it).register(any())
        }

        // When
        testedFeature.stop()

        // Then
        fakeConfig.plugins.forEach {
            verify(it).unregister()
            verifyNoMoreInteractions(it)
        }
    }

    @Test
    fun `𝕄 stop scheduler 𝕎 stop()`(
        @IntForgery(1, 10) pluginsCount: Int
    ) {
        // Given
        testedFeature.initialize(mockAppContext, fakeConfig)
        val mockUploadScheduler: UploadScheduler = mock()
        testedFeature.uploadScheduler = mockUploadScheduler

        // When
        testedFeature.stop()

        // Then
        verify(mockUploadScheduler).stopScheduling()
    }

    @Test
    fun `𝕄 cleanup data 𝕎 stop()`(
        @IntForgery(1, 10) pluginsCount: Int
    ) {
        // Given
        testedFeature.initialize(mockAppContext, fakeConfig)

        // When
        testedFeature.stop()

        // Then
        assertThat(testedFeature.persistenceStrategy)
            .isInstanceOf(NoOpPersistenceStrategy::class.java)
        assertThat(testedFeature.uploadScheduler)
            .isInstanceOf(NoOpUploadScheduler::class.java)
        assertThat(testedFeature.endpointUrl).isEqualTo("")
    }

    @Test
    fun `𝕄 mark itself as not initialized 𝕎 stop()`() {
        // Given
        testedFeature.initialize(mockAppContext, fakeConfig)

        // When
        testedFeature.stop()

        // Then
        assertThat(testedFeature.isInitialized()).isFalse()
    }

    @Test
    fun `𝕄 initialize only once 𝕎 initialize() twice`(
        forge: Forge
    ) {
        // Given
        val otherConfig = forgeConfiguration(forge)
        testedFeature.initialize(mockAppContext, fakeConfig)
        val persistenceStrategy = testedFeature.persistenceStrategy
        val uploadScheduler = testedFeature.uploadScheduler

        // When
        testedFeature.initialize(mockAppContext, otherConfig)

        // Then
        assertThat(testedFeature.endpointUrl).isEqualTo(fakeConfig.endpointUrl)
        assertThat(testedFeature.persistenceStrategy).isSameAs(persistenceStrategy)
        assertThat(testedFeature.uploadScheduler).isSameAs(uploadScheduler)
    }

    @Test
    fun `𝕄 not setup uploader 𝕎 initialize() in secondary process`() {
        // Given
        CoreFeature.isMainProcess = false

        // When
        testedFeature.initialize(mockAppContext, fakeConfig)

        // Then
        assertThat(testedFeature.uploadScheduler).isInstanceOf(NoOpUploadScheduler::class.java)
    }

    @Test
    fun `𝕄 clear local storage 𝕎 clearAllData()`() {
        // Given
        testedFeature.persistenceStrategy = mock()

        // When
        testedFeature.clearAllData()

        // Then
        verify(testedFeature.persistenceStrategy).clearAllData()
    }
}
