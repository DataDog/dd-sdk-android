/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.system

import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.util.concurrent.CountDownLatch

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DefaultAppVersionProviderTest {

    private lateinit var testedProvider: AppVersionProvider

    @StringForgery
    lateinit var fakeVersion: String

    @IntForgery(min = 0)
    var fakeVersionCode: Int = 0

    @BeforeEach
    fun setUp() {
        testedProvider = DefaultAppVersionProvider(fakeVersion, fakeVersionCode)
    }

    @Test
    fun `M return initial version W get`() {
        assertThat(testedProvider.version).isEqualTo(fakeVersion)
    }

    @Test
    fun `M return a new version W set() + get()`(
        @StringForgery fakeNewVersion: String
    ) {
        // When
        testedProvider.version = fakeNewVersion

        // Then
        assertThat(testedProvider.version).isEqualTo(fakeNewVersion)
    }

    @RepeatedTest(10)
    fun `M return a new version W set + get() { multi-threaded }`(
        @StringForgery fakeNewVersion: String
    ) {
        // Given
        val lock = CountDownLatch(1)

        // When
        Thread {
            testedProvider.version = fakeNewVersion
            lock.countDown()
        }.start()
        lock.await()

        // Then
        assertThat(testedProvider.version).isEqualTo(fakeNewVersion)
    }
}
