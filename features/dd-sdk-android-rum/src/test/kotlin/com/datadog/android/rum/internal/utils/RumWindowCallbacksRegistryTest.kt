/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.utils

import android.app.Activity
import android.view.Window
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mockito.verify
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
class RumWindowCallbacksRegistryTest {

    @Mock
    private lateinit var activity: Activity

    @Mock
    private lateinit var window: Window

    @Mock
    private lateinit var existingCallback: Window.Callback

    @Mock
    private lateinit var listener: RumWindowCallbackListener

    private var callback: Window.Callback? = null

    private val registry = RumWindowCallbacksRegistryImpl()

    @BeforeEach
    fun setUp() {
        callback = existingCallback

        whenever(activity.window) doReturn window
        whenever(window.callback) doReturn callback
        whenever(window.setCallback(any())).doAnswer {
            val argCallback = it.getArgument<Window.Callback>(0)
            callback = argCallback
        }
    }

    @Test
    fun `M call existing callback and listener W RumWindowCallbacksRegistry { onContentChanged called }`() {
        // Given
        registry.addListener(activity, listener)

        // When
        callback!!.onContentChanged()

        // Then
        inOrder(listener, existingCallback) {
            verify(existingCallback).onContentChanged()
            verify(listener).onContentChanged()
        }
        verifyNoMoreInteractions(listener, existingCallback)
    }
}
