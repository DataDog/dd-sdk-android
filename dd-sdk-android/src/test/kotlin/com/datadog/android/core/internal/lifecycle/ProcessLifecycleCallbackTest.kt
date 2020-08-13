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
import com.datadog.android.core.internal.data.upload.UploadWorker
import com.datadog.android.core.internal.net.info.NetworkInfo
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.utils.TAG_DATADOG_UPLOAD
import com.datadog.android.core.internal.utils.UPLOAD_WORKER_NAME
import com.datadog.android.utils.mockContext
import com.datadog.tools.unit.setFieldValue
import com.datadog.tools.unit.setStaticValue
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import java.lang.ref.WeakReference
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
    ExtendWith(MockitoExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class ProcessLifecycleCallbackTest {

    lateinit var testedCallback: ProcessLifecycleCallback

    lateinit var mockContext: Context

    @Mock
    lateinit var mockWorkManager: WorkManagerImpl

    @Mock
    lateinit var mockNetworkInfoProvider: NetworkInfoProvider

    @BeforeEach
    fun `set up`() {
        mockContext = mockContext()
        testedCallback = ProcessLifecycleCallback(mockNetworkInfoProvider, mockContext)
    }

    @AfterEach
    fun `tear down`() {
        WorkManagerImpl::class.java.setStaticValue("sDefaultInstance", null)
    }

    @Test
    fun `when process stopped and network is disconnected will schedule an upload worker`() {
        // Given
        WorkManagerImpl::class.java.setStaticValue("sDefaultInstance", mockWorkManager)
        whenever(mockNetworkInfoProvider.getLatestNetworkInfo())
            .thenReturn(
                NetworkInfo(
                    NetworkInfo.Connectivity.NETWORK_NOT_CONNECTED
                )
            )

        // When
        testedCallback.onStopped()

        // Then
        verify(mockWorkManager).enqueueUniqueWork(
            eq(UPLOAD_WORKER_NAME),
            eq(ExistingWorkPolicy.REPLACE),
            argThat<OneTimeWorkRequest> {
                this.workSpec.workerClassName == UploadWorker::class.java.canonicalName &&
                    this.tags.contains(TAG_DATADOG_UPLOAD)
            })
    }

    @Test
    fun `when process stopped and work manager is not present will not throw exception`() {
        // Given
        whenever(mockNetworkInfoProvider.getLatestNetworkInfo())
            .thenReturn(
                NetworkInfo(
                    NetworkInfo.Connectivity.NETWORK_NOT_CONNECTED
                )
            )

        // When
        testedCallback.onStopped()
    }

    @Test
    fun `when process stopped and network is connected will do nothing`() {
        // Given
        WorkManagerImpl::class.java.setStaticValue("sDefaultInstance", mockWorkManager)
        whenever(mockNetworkInfoProvider.getLatestNetworkInfo())
            .thenReturn(
                NetworkInfo(
                    NetworkInfo.Connectivity.NETWORK_WIFI
                )
            )

        // When
        testedCallback.onStopped()

        // Then
        verifyZeroInteractions(mockWorkManager)
    }

    @Test
    fun `when process stopped and context ref is null will do nothing`() {
        testedCallback.setFieldValue("contextWeakRef", WeakReference<Context>(null))
        WorkManagerImpl::class.java.setStaticValue("sDefaultInstance", mockWorkManager)
        whenever(mockNetworkInfoProvider.getLatestNetworkInfo())
            .thenReturn(
                NetworkInfo(
                    NetworkInfo.Connectivity.NETWORK_WIFI
                )
            )

        // When
        testedCallback.onStopped()

        // Then
        verifyZeroInteractions(mockWorkManager)
    }
}
