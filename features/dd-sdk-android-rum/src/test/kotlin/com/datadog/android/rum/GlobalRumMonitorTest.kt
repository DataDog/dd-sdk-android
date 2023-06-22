/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import com.datadog.android.v2.api.FeatureSdkCore
import com.datadog.android.v2.api.SdkCore
import com.datadog.tools.unit.annotations.ProhibitLeavingStaticMocksIn
import com.datadog.tools.unit.extensions.ProhibitLeavingStaticMocksExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.Callable

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ProhibitLeavingStaticMocksExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ProhibitLeavingStaticMocksIn(GlobalRumMonitor::class)
internal class GlobalRumMonitorTest {

    @Mock
    lateinit var mockRumMonitor: RumMonitor

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    lateinit var fakeRumMonitorProvider: Callable<RumMonitor>

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.internalLogger) doReturn mock()
        fakeRumMonitorProvider = Callable<RumMonitor> { mockRumMonitor }
    }

    @AfterEach
    fun `tear down`() {
        GlobalRumMonitor.clear()
    }

    @Test
    fun `M return true W registerIfAbsent(monitor)`() {
        // When
        val result = GlobalRumMonitor.registerIfAbsent(mockRumMonitor, mockSdkCore)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `M return false W registerIfAbsent(monitor) twice {same sdkCore}`() {
        // When
        GlobalRumMonitor.registerIfAbsent(mockRumMonitor, mockSdkCore)
        val result = GlobalRumMonitor.registerIfAbsent(mockRumMonitor, mockSdkCore)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M return true W registerIfAbsent(monitor) twice {distinct cores}`() {
        // Given
        val mockSdkCore2 = mock<SdkCore>()
        val mockMonitor2 = mock<RumMonitor>()

        // When
        GlobalRumMonitor.registerIfAbsent(mockRumMonitor, mockSdkCore)
        val result = GlobalRumMonitor.registerIfAbsent(mockMonitor2, mockSdkCore2)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `M return true W registerIfAbsent(provider)`() {
        // When
        val result = GlobalRumMonitor.registerIfAbsent(mockSdkCore, fakeRumMonitorProvider)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `M return false W registerIfAbsent(provider) twice {same sdkCore}`() {
        // When
        GlobalRumMonitor.registerIfAbsent(mockRumMonitor, mockSdkCore)
        val result = GlobalRumMonitor.registerIfAbsent(mockSdkCore, fakeRumMonitorProvider)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M return true W registerIfAbsent(provider) twice {distinct cores}`() {
        // Given
        val mockSdkCore2 = mock<SdkCore>()
        val mockMonitor2 = mock<RumMonitor>()
        val fakeRumMonitorProvider2 = Callable<RumMonitor> { mockMonitor2 }

        // When
        GlobalRumMonitor.registerIfAbsent(mockSdkCore, fakeRumMonitorProvider)
        val result = GlobalRumMonitor.registerIfAbsent(mockSdkCore2, fakeRumMonitorProvider2)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `M return true W registerIfAbsent(monitor) + isRegistered() {same sdkCore}`() {
        // When
        GlobalRumMonitor.registerIfAbsent(mockRumMonitor, mockSdkCore)
        val result = GlobalRumMonitor.isRegistered(mockSdkCore)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `M return false W registerIfAbsent(monitor) + isRegistered() {distinct cores}`() {
        // Given
        val mockSdkCore2 = mock<SdkCore>()

        // When
        GlobalRumMonitor.registerIfAbsent(mockRumMonitor, mockSdkCore)
        val result = GlobalRumMonitor.isRegistered(mockSdkCore2)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M return true W registerIfAbsent(provider) + isRegistered() {same sdkCore}`() {
        // When
        GlobalRumMonitor.registerIfAbsent(mockSdkCore, fakeRumMonitorProvider)
        val result = GlobalRumMonitor.isRegistered(mockSdkCore)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `M return false W registerIfAbsent(provider) + isRegistered() {distinct cores}`() {
        // Given
        val mockSdkCore2 = mock<SdkCore>()

        // When
        GlobalRumMonitor.registerIfAbsent(mockSdkCore, fakeRumMonitorProvider)
        val result = GlobalRumMonitor.isRegistered(mockSdkCore2)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M return monitor W registerIfAbsent(monitor) + get() {same sdkCore}`() {
        // When
        GlobalRumMonitor.registerIfAbsent(mockRumMonitor, mockSdkCore)
        val result = GlobalRumMonitor.get(mockSdkCore)

        // Then
        assertThat(result).isSameAs(mockRumMonitor)
    }

    @Test
    fun `M return null W registerIfAbsent(monitor) + get() {distinct cores}`() {
        // Given
        val mockSdkCore2 = mock<SdkCore>()

        // When
        GlobalRumMonitor.registerIfAbsent(mockRumMonitor, mockSdkCore)
        val result = GlobalRumMonitor.get(mockSdkCore2)

        // Then
        assertThat(result).isInstanceOf(NoOpRumMonitor::class.java)
    }

    @Test
    fun `M return monitor W registerIfAbsent(provider) + get() {same sdkCore}`() {
        // When
        GlobalRumMonitor.registerIfAbsent(mockSdkCore, fakeRumMonitorProvider)
        val result = GlobalRumMonitor.get(mockSdkCore)

        // Then
        assertThat(result).isSameAs(mockRumMonitor)
    }

    @Test
    fun `M return null W registerIfAbsent(provider) + get() {distinct cores}`() {
        // Given
        val mockSdkCore2 = mock<SdkCore>()

        // When
        GlobalRumMonitor.registerIfAbsent(mockSdkCore, fakeRumMonitorProvider)
        val result = GlobalRumMonitor.get(mockSdkCore2)

        // Then
        assertThat(result).isInstanceOf(NoOpRumMonitor::class.java)
    }
}
