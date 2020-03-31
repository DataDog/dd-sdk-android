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
import com.datadog.android.core.internal.data.upload.UploadWorker
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockContext
import com.datadog.tools.unit.invokeMethod
import com.datadog.tools.unit.setStaticValue
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.AfterEach
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
internal class WorkManagerUtilsTest {

    @Mock
    lateinit var mockedWorkManager: WorkManagerImpl

    lateinit var mockAppContext: Application

    @BeforeEach
    fun `set up`(forge: Forge) {
        mockAppContext = mockContext()
        Datadog.initialize(mockAppContext, forge.anHexadecimalString())
    }

    @AfterEach
    fun `tear down`() {
        Datadog.invokeMethod("stop")
        WorkManagerImpl::class.java.setStaticValue("sDefaultInstance", null)
    }

    @Test
    fun `it will cancel the worker if WorkManager was correctly instantiated`() {
        // given
        WorkManagerImpl::class.java.setStaticValue("sDefaultInstance", mockedWorkManager)

        // when
        cancelUploadWorker(mockContext())

        // then
        verify(mockedWorkManager).cancelAllWorkByTag(eq(TAG_DATADOG_UPLOAD))
    }

    @Test
    fun `it will handle the cancel exception if WorkManager was not correctly instantiated`() {
        // when
        cancelUploadWorker(mockContext())

        // then
        verifyZeroInteractions(mockedWorkManager)
    }

    @Test
    fun `it will schedule the worker if WorkManager was correctly instantiated`() {
        // given
        WorkManagerImpl::class.java.setStaticValue("sDefaultInstance", mockedWorkManager)

        // when
        triggerUploadWorker(mockContext())

        // then
        verify(mockedWorkManager).enqueueUniqueWork(
            eq(UPLOAD_WORKER_NAME),
            eq(ExistingWorkPolicy.REPLACE),
            argThat<OneTimeWorkRequest> {
                this.workSpec.workerClassName == UploadWorker::class.java.canonicalName &&
                    this.tags.contains(TAG_DATADOG_UPLOAD)
            })
    }

    @Test
    fun `it will handle the trigger exception if WorkManager was not correctly instantiated`() {
        // when
        triggerUploadWorker(mockContext())

        // then
        verifyZeroInteractions(mockedWorkManager)
    }
}
