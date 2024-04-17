/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.upload

import com.datadog.android.core.internal.configuration.DataUploadConfiguration
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.quality.Strictness
import java.util.concurrent.ScheduledThreadPoolExecutor

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DataUploadSchedulerTest {

    private lateinit var testedScheduler: DataUploadScheduler

    @Mock
    lateinit var mockExecutor: ScheduledThreadPoolExecutor

    @Forgery
    lateinit var fakeUploadConfiguration: DataUploadConfiguration

    @StringForgery
    lateinit var fakeFeatureName: String

    @BeforeEach
    fun `set up`() {
        testedScheduler = DataUploadScheduler(
            fakeFeatureName,
            storage = mock(),
            dataUploader = mock(),
            contextProvider = mock(),
            networkInfoProvider = mock(),
            systemInfoProvider = mock(),
            fakeUploadConfiguration,
            mockExecutor,
            internalLogger = mock()
        )
    }

    @Test
    fun `when start it will execute a runnable`() {
        // When
        testedScheduler.startScheduling()

        // Then
        verify(mockExecutor).execute(
            argThat { this is DataUploadRunnable }
        )
    }

    @Test
    fun `when stop it will try to remove the executed runnable`() {
        // Given
        testedScheduler.startScheduling()

        // When
        testedScheduler.stopScheduling()

        // Then
        val argumentCaptor = argumentCaptor<Runnable>()
        verify(mockExecutor).execute(
            argumentCaptor.capture()
        )
        verify(mockExecutor).remove(argumentCaptor.firstValue)
    }
}
