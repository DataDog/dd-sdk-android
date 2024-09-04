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
import android.os.Build
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
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
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(ApiLevelExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class ThreadSafeReceiverTest {
    private lateinit var testedReceiver: ThreadSafeReceiver

    @Mock
    lateinit var mockContext: Context

    @Mock
    lateinit var mockIntentFilter: IntentFilter

    @BeforeEach
    fun `set up`() {
        testedReceiver = TestableThreadSafeReceiver()
    }

    // region registerReceiver

    @TestTargetApi(Build.VERSION_CODES.O)
    @Test
    fun `M use the no export flag W registerReceiver { API version above 26}`() {
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

    @TestTargetApi(Build.VERSION_CODES.TIRAMISU)
    @Test
    fun `M use the no export flag W registerReceiver { API version above 33}`() {
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
    @TestTargetApi(Build.VERSION_CODES.N)
    @Test
    fun `M not use the no export flag W registerReceiver { API version below 26}`() {
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

internal class TestableThreadSafeReceiver : ThreadSafeReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {}
}
