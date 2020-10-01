/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tracing.internal.data

import com.datadog.android.core.internal.data.Writer
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import datadog.opentracing.DDSpan
import datadog.trace.api.DDTags
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.Locale
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
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
    ExtendWith(ForgeExtension::class)

)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class TraceWriterTest {

    lateinit var testedWriter: TraceWriter

    @Mock
    lateinit var mockFilesWriter: Writer<DDSpan>

    @Mock
    lateinit var mockAdvancedRumMonitor: AdvancedRumMonitor

    // region Unit Tests

    @BeforeEach
    fun `set up`() {
        GlobalRum.isRegistered.set(false)
        GlobalRum.registerIfAbsent(mockAdvancedRumMonitor)
        testedWriter = TraceWriter(mockFilesWriter)
    }

    @AfterEach
    fun `tear down`() {
        GlobalRum.isRegistered.set(false)
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
    }

    @Test
    fun `M send a RUM Error event W onWritingErrorSpan(`(forge: Forge) {
        // GIVEN
        val spansList = ArrayList<DDSpan>(2).apply {
            add(forgeErrorSpan(forge))
            add(forgeErrorSpan(forge))
        }

        // WHEN
        testedWriter.write(spansList)

        // THEN
        val errorMessagesCaptor = argumentCaptor<String>()
        val stackTracesCaptor = argumentCaptor<String>()
        verify(mockAdvancedRumMonitor, times(2)).addErrorWithStacktrace(
            errorMessagesCaptor.capture(),
            eq(RumErrorSource.SOURCE),
            stackTracesCaptor.capture(),
            eq(emptyMap())
        )
        errorMessagesCaptor.allValues.forEachIndexed { index, message ->
            assertThat(message).isEqualTo(
                TraceWriter.SPAN_ERROR_WITH_TYPE_AND_MESSAGE_FORMAT.format(
                    Locale.US,
                    spansList[index].operationName,
                    spansList[index].tags[DDTags.ERROR_TYPE].toString(),
                    spansList[index].tags[DDTags.ERROR_MSG].toString()
                )
            )
        }
        stackTracesCaptor.allValues.forEachIndexed { index, stacktrace ->
            assertThat(stacktrace).isEqualTo(spansList[index].tags[DDTags.ERROR_STACK].toString())
        }
    }

    @Test
    fun `M send a RUM Error event W onWritingErrorSpan {no error type}(`(forge: Forge) {
        // GIVEN
        val spansList = ArrayList<DDSpan>(2).apply {
            add(
                forgeErrorSpan(forge).apply {
                    this.context().setTag(DDTags.ERROR_TYPE, null)
                }
            )
            add(
                forgeErrorSpan(forge).apply {
                    this.context().setTag(DDTags.ERROR_TYPE, null)
                }
            )
        }

        // WHEN
        testedWriter.write(spansList)

        // THEN
        val errorMessagesCaptor = argumentCaptor<String>()
        val stackTracesCaptor = argumentCaptor<String>()
        verify(mockAdvancedRumMonitor, times(2)).addErrorWithStacktrace(
            errorMessagesCaptor.capture(),
            eq(RumErrorSource.SOURCE),
            stackTracesCaptor.capture(),
            eq(emptyMap())
        )
        errorMessagesCaptor.allValues.forEachIndexed { index, message ->
            assertThat(message).isEqualTo(
                TraceWriter.SPAN_ERROR_WITH_MESSAGE_FORMAT.format(
                    Locale.US,
                    spansList[index].operationName,
                    spansList[index].tags[DDTags.ERROR_MSG].toString()
                )
            )
        }

        stackTracesCaptor.allValues.forEachIndexed { index, stacktrace ->
            assertThat(stacktrace).isEqualTo(spansList[index].tags[DDTags.ERROR_STACK].toString())
        }
    }

    @Test
    fun `M send a RUM Error event W onWritingErrorSpan {no error message}(`(forge: Forge) {
        // GIVEN
        val spansList = ArrayList<DDSpan>(2).apply {
            add(
                forgeErrorSpan(forge).apply {
                    this.context().setTag(DDTags.ERROR_MSG, null)
                }
            )
            add(
                forgeErrorSpan(forge).apply {
                    this.context().setTag(DDTags.ERROR_MSG, null)
                }
            )
        }

        // WHEN
        testedWriter.write(spansList)

        // THEN
        val errorMessagesCaptor = argumentCaptor<String>()
        val stackTracesCaptor = argumentCaptor<String>()
        verify(mockAdvancedRumMonitor, times(2)).addErrorWithStacktrace(
            errorMessagesCaptor.capture(),
            eq(RumErrorSource.SOURCE),
            stackTracesCaptor.capture(),
            eq(emptyMap())
        )
        errorMessagesCaptor.allValues.forEachIndexed { index, message ->
            assertThat(message).isEqualTo(
                TraceWriter.SPAN_ERROR_WITH_TYPE_FORMAT.format(
                    Locale.US,
                    spansList[index].operationName,
                    spansList[index].tags[DDTags.ERROR_TYPE].toString()
                )
            )
        }

        stackTracesCaptor.allValues.forEachIndexed { index, stacktrace ->
            assertThat(stacktrace).isEqualTo(spansList[index].tags[DDTags.ERROR_STACK].toString())
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
        verifyZeroInteractions(mockAdvancedRumMonitor)
    }

    @Test
    fun `M do nothing W onWritingErrorFreeSpan`(forge: Forge) {
        // GIVEN
        GlobalRum.isRegistered.set(false)
        val mockRumMonitor = mock<RumMonitor>()
        GlobalRum.registerIfAbsent(mockRumMonitor)
        val spansList = ArrayList<DDSpan>(2).apply {
            add(forgeErrorFreeSpan(forge))
            add(forgeErrorFreeSpan(forge))
        }

        // WHEN
        testedWriter.write(spansList)

        // THEN
        verifyZeroInteractions(mockRumMonitor)
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
}
