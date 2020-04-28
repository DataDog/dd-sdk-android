/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.mock
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class GlobalRumTest {

    @BeforeEach
    fun `set up`() {
        GlobalRum.isRegistered.set(false)
        GlobalRum.monitor = NoOpRumMonitor()
        GlobalRum.sessionStartNs.set(0L)
        GlobalRum.lastUserInteractionNs.set(0L)
    }

    @AfterEach
    fun `tear down`() {
        GlobalRum.isRegistered.set(false)
        GlobalRum.monitor = NoOpRumMonitor()
        GlobalRum.updateRumContext(RumContext())
        GlobalRum.sessionStartNs.set(0L)
        GlobalRum.lastUserInteractionNs.set(0L)
    }

    @Test
    fun `register monitor`() {
        val monitor: RumMonitor = mock()

        GlobalRum.registerIfAbsent(monitor)

        assertThat(GlobalRum.get())
            .isSameAs(monitor)
    }

    @Test
    fun `register monitor only once`() {
        val monitor: RumMonitor = mock()
        val monitor2: RumMonitor = mock()

        GlobalRum.registerIfAbsent(monitor)
        GlobalRum.registerIfAbsent(monitor2)

        assertThat(GlobalRum.get())
            .isSameAs(monitor)
    }
}
