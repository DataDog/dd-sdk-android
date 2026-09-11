/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.prelaunch

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.net.Uri
import com.datadog.android.rum.DdRumContentProvider
import com.datadog.android.rum.internal.startup.PreLaunchRumAppStartupDetector
import com.datadog.android.rum.prelaunch.utils.forge.Configurator
import com.datadog.tools.unit.getFieldValue
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

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

        resetDetector()

        testedProvider = AppLaunchCollectorProvider()
        testedProvider.setFieldValue("mContext", mockContext)
        whenever(mockContext.applicationContext).thenReturn(mockApplication)
    }

    @AfterEach
    fun `tear down`() {
        DdRumContentProvider.processImportance = 0

        resetDetector()
    }

    /**
     * [PreLaunchRumAppStartupDetector] is a process-scoped singleton, so its state has to be
     * cleared between tests. Its backing field is private, hence the reflection.
     */
    private fun resetDetector() {
        PreLaunchRumAppStartupDetector.setFieldValue("detectorImpl", null)
        PreLaunchRumAppStartupDetector
            .getFieldValue<MutableList<*>, PreLaunchRumAppStartupDetector>("registrations")
            .clear()
        PreLaunchRumAppStartupDetector
            .getFieldValue<MutableList<*>, PreLaunchRumAppStartupDetector>("pendingEvents")
            .clear()
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
        assertThat(PreLaunchRumAppStartupDetector.isInstalled).isFalse()
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
        assertThat(PreLaunchRumAppStartupDetector.isInstalled).isFalse()
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
        assertThat(PreLaunchRumAppStartupDetector.isInstalled).isTrue()
    }

    @Test
    fun `M return false W onCreate() {applicationContext is not Application}`() {
        // Given: applicationContext returns a plain Context, not Application
        whenever(mockContext.applicationContext).thenReturn(mockContext)

        // When
        val result = testedProvider.onCreate()

        // Then
        assertThat(result).isFalse()
        assertThat(PreLaunchRumAppStartupDetector.isInstalled).isFalse()
    }

    // endregion

    // region stub methods

    @Test
    fun `M return null W query() {stub}`() {
        val mockUri = mock<Uri>()
        assertThat(testedProvider.query(mockUri, null, null, null, null)).isNull()
    }

    @Test
    fun `M return null W getType() {stub}`() {
        val mockUri = mock<Uri>()
        assertThat(testedProvider.getType(mockUri)).isNull()
    }

    @Test
    fun `M return null W insert() {stub}`() {
        val mockUri = mock<Uri>()
        assertThat(testedProvider.insert(mockUri, null)).isNull()
    }

    @Test
    fun `M return 0 W delete() {stub}`() {
        val mockUri = mock<Uri>()
        assertThat(testedProvider.delete(mockUri, null, null)).isEqualTo(0)
    }

    @Test
    fun `M return 0 W update() {stub}`() {
        val mockUri = mock<Uri>()
        assertThat(testedProvider.update(mockUri, null, null, null)).isEqualTo(0)
    }

    // endregion
}
