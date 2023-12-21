/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.lifecycle

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.impl.WorkManagerImpl
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.data.upload.UploadWorker
import com.datadog.android.core.internal.utils.TAG_DATADOG_UPLOAD
import com.datadog.android.core.internal.utils.UPLOAD_WORKER_NAME
import com.datadog.android.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.datadog.tools.unit.setStaticValue
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class ProcessLifecycleCallbackTest {

    lateinit var testedCallback: ProcessLifecycleCallback

    @Mock
    lateinit var mockWorkManager: WorkManagerImpl

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @BeforeEach
    fun `set up`() {
        testedCallback = ProcessLifecycleCallback(appContext.mockInstance, mockInternalLogger)
    }

    @AfterEach
    fun `tear down`() {
        WorkManagerImpl::class.java.setStaticValue("sDefaultInstance", null)
    }

    @Test
    fun `when process stopped will schedule an upload worker`() {
        // Given
        WorkManagerImpl::class.java.setStaticValue("sDefaultInstance", mockWorkManager)
        whenever(
            mockWorkManager.enqueueUniqueWork(
                ArgumentMatchers.anyString(),
                any(),
                any<OneTimeWorkRequest>()
            )
        ) doReturn mock()

        // When
        testedCallback.onStopped()

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
    fun `when process stopped and work manager is not present will not throw exception`() {
        // When
        testedCallback.onStopped()

        // Then
        verifyNoInteractions(mockWorkManager)
    }

    @Test
    fun `when process stopped and context ref is null will do nothing`() {
        testedCallback.contextWeakRef.clear()
        WorkManagerImpl::class.java.setStaticValue("sDefaultInstance", mockWorkManager)

        // When
        testedCallback.onStopped()

        // Then
        verifyNoInteractions(mockWorkManager)
    }

    @Test
    fun `when process started cancel existing workers`() {
        // Given
        WorkManagerImpl::class.java.setStaticValue("sDefaultInstance", mockWorkManager)

        // When
        testedCallback.onStarted()

        // Then
        verify(mockWorkManager).cancelAllWorkByTag(TAG_DATADOG_UPLOAD)
    }

    @Test
    fun `when process started do nothing if no work manager`() {
        // Given
        WorkManagerImpl::class.java.setStaticValue("sDefaultInstance", null)

        // When
        testedCallback.onStarted()

        // Then
        verifyNoInteractions(mockWorkManager)
    }

    companion object {
        val appContext = ApplicationContextTestConfiguration(Context::class.java)

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(appContext)
        }
    }
}
