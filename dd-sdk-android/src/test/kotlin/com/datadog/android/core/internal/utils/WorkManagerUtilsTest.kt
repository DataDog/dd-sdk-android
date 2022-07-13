/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.utils

import android.app.Application
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.impl.WorkManagerImpl
import com.datadog.android.Datadog
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.Credentials
import com.datadog.android.core.internal.data.upload.UploadWorker
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.utils.config.MainLooperTestConfiguration
import com.datadog.android.utils.extension.mockChoreographerInstance
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.datadog.tools.unit.setStaticValue
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.ArgumentMatchers.anyString
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
internal class WorkManagerUtilsTest {

    @Mock
    lateinit var mockWorkManager: WorkManagerImpl

    @BeforeEach
    fun `set up`(forge: Forge) {
        mockChoreographerInstance()

        Datadog.initialize(
            appContext.mockInstance,
            Credentials(
                forge.anHexadecimalString(),
                forge.anAlphabeticalString(),
                Credentials.NO_VARIANT,
                null
            ),
            Configuration.Builder(
                logsEnabled = true,
                tracesEnabled = true,
                crashReportsEnabled = true,
                rumEnabled = true,
                sessionReplayEnabled = true
            ).build(),
            TrackingConsent.GRANTED
        )

        whenever(mockWorkManager.cancelAllWorkByTag(anyString())) doReturn mock()
        whenever(
            mockWorkManager.enqueueUniqueWork(
                anyString(),
                any(),
                any<OneTimeWorkRequest>()
            )
        ) doReturn mock()
    }

    @AfterEach
    fun `tear down`() {
        Datadog.stop()
        WorkManagerImpl::class.java.setStaticValue("sDefaultInstance", null)
    }

    @Test
    fun `it will cancel the worker if WorkManager was correctly instantiated`() {
        // Given
        WorkManagerImpl::class.java.setStaticValue("sDefaultInstance", mockWorkManager)

        // When
        cancelUploadWorker(appContext.mockInstance)

        // Then
        verify(mockWorkManager).cancelAllWorkByTag(eq(TAG_DATADOG_UPLOAD))
    }

    @Test
    fun `it will handle the cancel exception if WorkManager was not correctly instantiated`() {
        // When
        cancelUploadWorker(appContext.mockInstance)

        // Then
        verifyZeroInteractions(mockWorkManager)
    }

    @Test
    fun `it will schedule the worker if WorkManager was correctly instantiated`() {
        // Given
        WorkManagerImpl::class.java.setStaticValue("sDefaultInstance", mockWorkManager)

        // When
        triggerUploadWorker(appContext.mockInstance)

        // Then
        verify(mockWorkManager).enqueueUniqueWork(
            eq(UPLOAD_WORKER_NAME),
            eq(ExistingWorkPolicy.REPLACE),
            argThat<OneTimeWorkRequest> {
                this.workSpec.workerClassName == UploadWorker::class.java.canonicalName &&
                    this.tags.contains(TAG_DATADOG_UPLOAD)
            }
        )
    }

    @Test
    fun `it will handle the trigger exception if WorkManager was not correctly instantiated`() {
        // When
        triggerUploadWorker(appContext.mockInstance)

        // Then
        verifyZeroInteractions(mockWorkManager)
    }

    companion object {
        val appContext = ApplicationContextTestConfiguration(Application::class.java)
        val mainLooper = MainLooperTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(appContext, mainLooper)
        }
    }
}
