/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tracing.internal.data

import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.rum.GlobalRum
import com.datadog.android.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.opentracing.DDSpan
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.datadog.trace.api.DDTags
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class TraceWriterTest {

    lateinit var testedWriter: TraceWriter

    @Mock
    lateinit var mockFilesWriter: DataWriter<DDSpan>

    // region Unit Tests

    @BeforeEach
    fun `set up`() {
        GlobalRum.isRegistered.set(false)
        testedWriter = TraceWriter(mockFilesWriter)
    }

    @Test
    fun `M use the wrapped writer W onWriting`(forge: Forge) {
        // GIVEN
        val spansList = ArrayList<DDSpan>(2).apply {
            add(forge.getForgery())
            add(forge.getForgery())
        }

        // WHEN
        testedWriter.write(spansList)

        // THEN
        verify(mockFilesWriter).write(spansList)
        spansList.forEach {
            it.finish()
        }
    }

    @Test
    fun `M not send a RUM Error event W onWritingErrorSpan(`(forge: Forge) {
        // GIVEN
        val spansList = ArrayList<DDSpan>(2).apply {
            add(forgeErrorSpan(forge))
            forgeErrorSpan(forge).apply {
                this.context().setTag(DDTags.ERROR_TYPE, null)
            }
            forgeErrorSpan(forge).apply {
                this.context().setTag(DDTags.ERROR_MSG, null)
            }
        }

        // WHEN
        testedWriter.write(spansList)

        // THEN
        verifyZeroInteractions(rumMonitor.mockInstance)
        spansList.forEach {
            it.finish()
        }
    }

    @Test
    fun `M do nothing W onWritingErrorSpan and no AdvancedRumMonitor registered`(forge: Forge) {
        // GIVEN
        val spansList = ArrayList<DDSpan>(2).apply {
            add(forgeErrorFreeSpan(forge))
            add(forgeErrorFreeSpan(forge))
        }

        // WHEN
        testedWriter.write(spansList)

        // THEN
        verifyZeroInteractions(rumMonitor.mockInstance)
    }

    @Test
    fun `M do nothing W onWritingErrorFreeSpan`(forge: Forge) {
        // GIVEN
        GlobalRum.isRegistered.set(false)
        val spansList = ArrayList<DDSpan>(2).apply {
            add(forgeErrorFreeSpan(forge))
            add(forgeErrorFreeSpan(forge))
        }

        // WHEN
        testedWriter.write(spansList)

        // THEN
        verifyZeroInteractions(rumMonitor.mockInstance)
    }

    @Test
    fun `M do nothing W handling null data`() {
        // WHEN
        testedWriter.write(null)

        // THEN
        verifyZeroInteractions(mockFilesWriter)
    }

    // endregion

    // region Internal

    fun forgeErrorSpan(forge: Forge): DDSpan {
        val fakeErrorSpan: DDSpan = forge.getForgery()
        val throwable: Throwable = forge.getForgery()
        fakeErrorSpan.setErrorMeta(throwable)
        return fakeErrorSpan
    }

    fun forgeErrorFreeSpan(forge: Forge): DDSpan {
        val fakeErrorFreeSpan: DDSpan = forge.getForgery()
        fakeErrorFreeSpan.isError = false
        return fakeErrorFreeSpan
    }

    // endregion

    companion object {
        val rumMonitor = GlobalRumMonitorTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(rumMonitor)
        }
    }
}
