/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.prelaunch

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import com.datadog.android.rum.AppLaunchPreInitCollector
import com.datadog.android.rum.DdRumContentProvider
import com.datadog.android.rum.prelaunch.forge.Configurator
import com.datadog.tools.unit.setFieldValue
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.atomic.AtomicReference

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class AppLaunchCollectorProviderTest {

    lateinit var testedProvider: AppLaunchCollectorProvider

    @Mock
    lateinit var mockContext: Context

    @Mock
    lateinit var mockApplication: Application

    @BeforeEach
    fun `set up`() {
        DdRumContentProvider.processImportance =
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND

        // Reset collector state via reflection since reset() is internal to dd-sdk-android-internal
        val stateField = AppLaunchPreInitCollector::class.java.getDeclaredField("_state")
        stateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (stateField.get(AppLaunchPreInitCollector) as AtomicReference<Any>)
            .set(AppLaunchPreInitCollector.State.NOT_INSTALLED)
        // Clear any stored application reference
        AppLaunchPreInitCollector.setFieldValue("_application", null)

        testedProvider = AppLaunchCollectorProvider()
        testedProvider.setFieldValue("mContext", mockContext)
        whenever(mockContext.applicationContext).thenReturn(mockApplication)
    }

    @AfterEach
    fun `tear down`() {
        DdRumContentProvider.processImportance = 0

        // Reset collector state via reflection
        val stateField = AppLaunchPreInitCollector::class.java.getDeclaredField("_state")
        stateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (stateField.get(AppLaunchPreInitCollector) as AtomicReference<Any>)
            .set(AppLaunchPreInitCollector.State.NOT_INSTALLED)
        AppLaunchPreInitCollector.setFieldValue("_application", null)
    }

    // region onCreate

    @Test
    fun `M return false W onCreate() {null applicationContext}`() {
        // Given
        whenever(mockContext.applicationContext).thenReturn(null)

        // When
        val result = testedProvider.onCreate()

        // Then
        assertThat(result).isFalse()
        assertThat(AppLaunchPreInitCollector.state)
            .isEqualTo(AppLaunchPreInitCollector.State.NOT_INSTALLED)
    }

    @Test
    fun `M return false W onCreate() {background process}`() {
        // Given
        DdRumContentProvider.processImportance =
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED

        // When
        val result = testedProvider.onCreate()

        // Then
        assertThat(result).isFalse()
        assertThat(AppLaunchPreInitCollector.state)
            .isEqualTo(AppLaunchPreInitCollector.State.NOT_INSTALLED)
    }

    @Test
    fun `M call install and return true W onCreate() {foreground process}`() {
        // Given
        DdRumContentProvider.processImportance =
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND

        // When
        val result = testedProvider.onCreate()

        // Then
        assertThat(result).isTrue()
        assertThat(AppLaunchPreInitCollector.state)
            .isEqualTo(AppLaunchPreInitCollector.State.IDLE)
    }

    // endregion
}
