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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
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

    @Test
    fun testOne() {
        val activity = mock<Activity>()
        val window = mock<Window>()
        val existingCallback = mock<Window.Callback>()
        var callback: Window.Callback?

        callback = existingCallback

        whenever(activity.window) doReturn window
        whenever(window.callback) doReturn callback
        whenever(window.setCallback(any())).doAnswer {
            val arg = it.getArgument<Window.Callback>(0)
            callback = arg
        }

        val registry = RumWindowCallbacksRegistryImpl()
        val listener = mock<RumWindowCallbackListener>()

        registry.addListener(activity, listener)
        callback!!.onContentChanged()

        verify(listener).onContentChanged()
        verify(existingCallback).onContentChanged()
        verifyNoMoreInteractions(listener, existingCallback)
    }
}
