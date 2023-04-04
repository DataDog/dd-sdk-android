/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.glide

import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumMonitor
import com.datadog.android.v2.api.SdkCore
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.lang.RuntimeException

@Extensions(
    ExtendWith(
        MockitoExtension::class,
        ForgeExtension::class
    )
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class DatadogRUMUncaughtThrowableStrategyTest {

    lateinit var testedStrategy: DatadogRUMUncaughtThrowableStrategy

    @Mock
    lateinit var mockRumMonitor: RumMonitor

    @Mock
    lateinit var mockSdkCore: SdkCore

    @StringForgery
    lateinit var fakeName: String

    @BeforeEach
    fun `set up`() {
        GlobalRum.registerIfAbsent(mockSdkCore, mockRumMonitor)

        testedStrategy = DatadogRUMUncaughtThrowableStrategy(fakeName, mockSdkCore)
    }

    @AfterEach
    fun `tear down`() {
        GlobalRum::class.java.getDeclaredMethod("reset").apply {
            isAccessible = true
            invoke(null)
        }
    }

    @Test
    fun `handles throwable`(
        @StringForgery message: String
    ) {
        val throwable = RuntimeException(message)

        testedStrategy.handle(throwable)

        verify(mockRumMonitor)
            .addError("Glide $fakeName error", RumErrorSource.SOURCE, throwable, emptyMap())
    }

    @Test
    fun `handles null throwable`() {
        testedStrategy.handle(null)

        verifyZeroInteractions(mockRumMonitor)
    }
}
