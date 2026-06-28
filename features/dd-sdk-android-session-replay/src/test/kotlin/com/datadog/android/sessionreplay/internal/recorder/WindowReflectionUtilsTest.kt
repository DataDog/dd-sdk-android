/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.view.View
import android.view.Window
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.mock
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class WindowReflectionUtilsTest {

    @Test
    fun `M return null W getWindowFromDecorView {view has no mWindow field}`() {
        assertThat(WindowReflectionUtils.getWindowFromDecorView(mock())).isNull()
    }

    @Test
    fun `M return Window W getWindowFromDecorView {view class has mWindow field}`() {
        // Given — mimics DecorView's private mWindow field
        val fakeWindow = mock<Window>()
        val fakeDecorLike = object : View(mock()) {
            @Suppress("unused")
            private val mWindow: Window = fakeWindow
        }

        // When + Then
        assertThat(WindowReflectionUtils.getWindowFromDecorView(fakeDecorLike)).isSameAs(fakeWindow)
    }
}
