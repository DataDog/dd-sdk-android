/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import com.datadog.android.error.internal.CrashReportsFeature
import com.datadog.android.log.internal.LogsFeature
import com.datadog.android.plugin.DatadogPlugin
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.tracing.internal.TracesFeature
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.invokeMethod
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.lang.Exception
import java.util.concurrent.CountDownLatch
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
    }

    @AfterEach
    fun `tear down`() {
        GlobalRum.isRegistered.set(false)
        GlobalRum.monitor = NoOpRumMonitor()
        GlobalRum.updateRumContext(RumContext())
        GlobalRum.sessionStartNs.set(0L)
    }

    @Test
    fun `M register monitor W registerIfAbsent()`() {
        val monitor: RumMonitor = mock()

        GlobalRum.registerIfAbsent(monitor)

        assertThat(GlobalRum.get())
            .isSameAs(monitor)
    }

    @Test
    fun `M register monitor only once W registerIfAbsent() twice`() {
        val monitor: RumMonitor = mock()
        val monitor2: RumMonitor = mock()

        GlobalRum.registerIfAbsent(monitor)
        GlobalRum.registerIfAbsent(monitor2)

        assertThat(GlobalRum.get())
            .isSameAs(monitor)
    }

    @Test
    fun `M add global attributes W addAttribute()`(
        @StringForgery key: String,
        @StringForgery(type = StringForgeryType.ASCII) value: String
    ) {
        GlobalRum.addAttribute(key, value)

        assertThat(GlobalRum.globalAttributes)
            .containsEntry(key, value)
    }

    @Test
    fun `M overwrite global attributes W addAttribute() twice {same key different value}`(
        @StringForgery key: String,
        @StringForgery(type = StringForgeryType.ASCII) value: String,
        @StringForgery(type = StringForgeryType.ASCII) value2: String
    ) {
        GlobalRum.addAttribute(key, value)
        GlobalRum.addAttribute(key, value2)

        assertThat(GlobalRum.globalAttributes)
            .containsEntry(key, value2)
    }

    @Test
    fun `M remove global attributes W addAttribute() and removeAttribute()`(
        @StringForgery key: String,
        @StringForgery(type = StringForgeryType.ASCII) value: String
    ) {
        GlobalRum.addAttribute(key, value)
        assertThat(GlobalRum.globalAttributes)
            .containsEntry(key, value)

        GlobalRum.removeAttribute(key)
        assertThat(GlobalRum.globalAttributes)
            .doesNotContainKey(key)
    }

    @Test
    fun `M add global attributes W addAttribute() {multithreaded}`(
        @StringForgery key: String
    ) {
        var errors = 0
        val countDownLatch = CountDownLatch(2)
        val threadAdd = Thread {
            try {
                for (i in 0..128) {
                    GlobalRum.addAttribute("$key$i", "value-$i")
                }
            } catch (e: Exception) {
                errors++
            } finally {
                countDownLatch.countDown()
            }
        }

        val threadRead = Thread {
            try {
                for (i in 0..128) {
                    val iterator = GlobalRum.globalAttributes.iterator()
                    while (iterator.hasNext()) {
                        val entry = iterator.next()
                        println("${entry.key} = ${entry.value}")
                    }
                }
            } catch (e: Exception) {
                errors++
            } finally {
                countDownLatch.countDown()
            }
        }

        threadRead.start()
        threadAdd.start()

        countDownLatch.await()

        assertThat(errors).isEqualTo(0)
    }

    @Test
    fun `M remove global attributes W removeAttribute() {multithreaded}`(
        @StringForgery key: String
    ) {
        for (i in 0..128) {
            GlobalRum.addAttribute("$key$i", "value-$i")
        }
        var errors = 0
        val countDownLatch = CountDownLatch(2)
        val threadAdd = Thread {
            try {
                for (i in 0..128) {
                    GlobalRum.removeAttribute("$key$i")
                }
            } catch (e: Exception) {
                errors++
            } finally {
                countDownLatch.countDown()
            }
        }

        val threadRead = Thread {
            try {
                for (i in 0..128) {
                    val iterator = GlobalRum.globalAttributes.iterator()
                    while (iterator.hasNext()) {
                        val entry = iterator.next()
                        println("${entry.key} = ${entry.value}")
                    }
                }
            } catch (e: Exception) {
                errors++
            } finally {
                countDownLatch.countDown()
            }
        }

        threadRead.start()
        threadAdd.start()

        countDownLatch.await()

        assertThat(errors).isEqualTo(0)
    }

    @Test
    fun `M update plugins W updateRumContext()`(forge: Forge) {

        // Given
        val applicationId = forge.aNumericalString()
        val sessionId = forge.aNumericalString()
        val viewId = forge.aNumericalString()
        val crashFeaturePlugins: List<DatadogPlugin> =
            forge.aList(forge.anInt(min = 1, max = 4)) {
                mock<DatadogPlugin>()
            }
        val tracesFeaturePlugins: List<DatadogPlugin> =
            forge.aList(forge.anInt(min = 1, max = 4)) {
                mock<DatadogPlugin>()
            }
        val logsFeaturePlugins: List<DatadogPlugin> =
            forge.aList(forge.anInt(min = 1, max = 4)) {
                mock<DatadogPlugin>()
            }
        val rumFeaturePlugins: List<DatadogPlugin> =
            forge.aList(forge.anInt(min = 1, max = 4)) {
                mock<DatadogPlugin>()
            }

        CrashReportsFeature.plugins = crashFeaturePlugins
        LogsFeature.plugins = logsFeaturePlugins
        TracesFeature.plugins = tracesFeaturePlugins
        RumFeature.plugins = rumFeaturePlugins

        // When
        GlobalRum.updateRumContext(
            RumContext(
                applicationId,
                sessionId,
                viewId
            )
        )

        // Then
        val pluginsToAssert =
            crashFeaturePlugins + tracesFeaturePlugins + logsFeaturePlugins + rumFeaturePlugins
        pluginsToAssert.forEach {
            verify(it).onContextChanged(
                argThat {
                    this.rum?.applicationId == applicationId &&
                        this.rum?.sessionId == sessionId &&
                        this.rum?.viewId == viewId
                }
            )
        }
    }

    @Test
    fun `M reset monitor W resetSession()`() {
        // Given
        val monitor: AdvancedRumMonitor = mock()
        GlobalRum.registerIfAbsent(monitor)

        // When
        GlobalRum.invokeMethod("resetSession")

        // Then
        verify(monitor).resetSession()
    }
}
