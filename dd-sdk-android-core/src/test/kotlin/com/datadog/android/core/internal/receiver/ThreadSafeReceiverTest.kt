/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.receiver

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.datadog.android.internal.system.BuildSdkVersionProvider
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class ThreadSafeReceiverTest {
    private lateinit var testedReceiver: ThreadSafeReceiver

    @Mock
    lateinit var mockContext: Context

    @Mock
    lateinit var mockIntentFilter: IntentFilter

    @Mock
    lateinit var mockBuildSdkVersionProvider: BuildSdkVersionProvider

    @BeforeEach
    fun `set up`() {
        testedReceiver = TestableThreadSafeReceiver(mockBuildSdkVersionProvider)
    }

    // region registerReceiver

    @Test
    fun `M use the no export flag W registerReceiver { API version above 26}`() {
        // Given
        whenever(mockBuildSdkVersionProvider.isAtLeastTiramisu) doReturn false
        whenever(mockBuildSdkVersionProvider.isAtLeastO) doReturn true

        // When
        testedReceiver.registerReceiver(mockContext, mockIntentFilter)

        // Then
        verify(mockContext).registerReceiver(
            testedReceiver,
            mockIntentFilter,
            ThreadSafeReceiver.RECEIVER_NOT_EXPORTED_COMPAT
        )
        assertThat(this.testedReceiver.isRegistered.get()).isTrue()
    }

    @Test
    fun `M use the no export flag W registerReceiver { API version above 33}`() {
        // Given
        whenever(mockBuildSdkVersionProvider.isAtLeastTiramisu) doReturn true

        // When
        testedReceiver.registerReceiver(mockContext, mockIntentFilter)

        // Then
        verify(mockContext).registerReceiver(
            testedReceiver,
            mockIntentFilter,
            Context.RECEIVER_NOT_EXPORTED
        )
        assertThat(this.testedReceiver.isRegistered.get()).isTrue()
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Test
    fun `M not use the no export flag W registerReceiver { API version below 26}`() {
        // Given
        whenever(mockBuildSdkVersionProvider.isAtLeastTiramisu) doReturn false
        whenever(mockBuildSdkVersionProvider.isAtLeastO) doReturn false

        // When
        testedReceiver.registerReceiver(mockContext, mockIntentFilter)

        // Then
        verify(mockContext).registerReceiver(testedReceiver, mockIntentFilter)
        verifyNoMoreInteractions(mockContext)
        assertThat(this.testedReceiver.isRegistered.get()).isTrue()
    }

    // endregion

    // region unregisterReceiver

    @Test
    fun `M unregister the receiver W unregisterReceiver { registered }`() {
        // Given
        testedReceiver.isRegistered.set(true)

        // When
        testedReceiver.unregisterReceiver(mockContext)

        // Then
        verify(mockContext).unregisterReceiver(testedReceiver)
        assertThat(this.testedReceiver.isRegistered.get()).isFalse()
    }

    @Test
    fun `M do nothing W unregisterReceiver { not registered }`() {
        // Given
        testedReceiver.isRegistered.set(false)

        // When
        testedReceiver.unregisterReceiver(mockContext)

        // Then
        verifyNoInteractions(mockContext)
        assertThat(this.testedReceiver.isRegistered.get()).isFalse()
    }

    // endregion
}

internal class TestableThreadSafeReceiver(buildSdkVersionProvider: BuildSdkVersionProvider) :
    ThreadSafeReceiver(buildSdkVersionProvider) {
    override fun onReceive(context: Context?, intent: Intent?) {}
}
