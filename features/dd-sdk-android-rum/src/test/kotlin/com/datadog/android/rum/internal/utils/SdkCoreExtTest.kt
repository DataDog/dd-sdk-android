/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.utils

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.EventWriteScope
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.api.storage.NoOpDataWriter
import com.datadog.android.rum.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.android.rum.utils.verifyLog
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.datadog.tools.unit.forge.anException
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class SdkCoreExtTest {

    @Mock
    lateinit var mockRumFeatureScope: FeatureScope

    @Mock
    lateinit var mockEventBatchWriter: EventBatchWriter

    @Mock
    lateinit var mockEventWriteScope: EventWriteScope

    @Mock
    lateinit var mockWriter: DataWriter<Any>

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    private val mockSdkCore
        get() = rumMonitor.mockSdkCore

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @Forgery
    lateinit var fakeEventType: EventType

    @BeforeEach
    fun `set up`() {
        whenever(mockEventWriteScope.invoke(any())) doAnswer {
            val callback = it.getArgument<(EventBatchWriter) -> Unit>(0)
            callback.invoke(mockEventBatchWriter)
        }
        whenever(mockRumFeatureScope.withWriteContext(any())) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventWriteScope) -> Unit>(0)
            callback.invoke(fakeDatadogContext, mockEventWriteScope)
        }
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockWriter.write(eq(mockEventBatchWriter), any(), any())) doReturn true
    }

    @Test
    fun `M write data with default type W submit()`() {
        // Given
        val fakeEvent = Any()

        // When
        mockSdkCore.newRumEventWriteOperation(fakeDatadogContext, mockEventWriteScope, mockWriter) { fakeEvent }
            .submit()

        // Then
        verify(mockWriter).write(mockEventBatchWriter, fakeEvent, EventType.DEFAULT)
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M write data W submit()`() {
        // Given
        val fakeEvent = Any()

        // When
        mockSdkCore.newRumEventWriteOperation(
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter,
            fakeEventType
        ) { fakeEvent }.submit()

        // Then
        verify(mockWriter).write(mockEventBatchWriter, fakeEvent, fakeEventType)
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M call onSuccess W submit() { write succeeded } `() {
        // Given
        val fakeEvent = Any()
        var invoked = false

        // When
        mockSdkCore.newRumEventWriteOperation(
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter,
            fakeEventType
        ) { fakeEvent }
            .onSuccess { invoked = true }
            .submit()

        // Then
        verifyNoInteractions(mockInternalLogger)
        assertThat(invoked)
            .overridingErrorMessage("Expected to invoke onSuccess callback, but it wasn't.")
            .isTrue
    }

    @Test
    fun `M call onError W submit() { write was not successful }`() {
        // Given
        val fakeEvent = Any()
        whenever(mockWriter.write(eq(mockEventBatchWriter), any(), eq(fakeEventType))) doReturn false
        var invoked = false

        // When
        mockSdkCore.newRumEventWriteOperation(
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter,
            fakeEventType
        ) { fakeEvent }
            .onError { invoked = true }
            .submit()

        // Then
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.USER),
            message = WriteOperation.WRITE_OPERATION_FAILED_ERROR
        )
        assertThat(invoked)
            .overridingErrorMessage("Expected to invoke onError callback, but it wasn't.")
            .isTrue
    }

    @Test
    fun `M call onError W submit() { write throws }`(
        forge: Forge
    ) {
        // Given
        val fakeEvent = Any()
        val fakeException = forge.anException()
        whenever(mockWriter.write(eq(mockEventBatchWriter), any(), eq(fakeEventType))) doThrow fakeException
        var invoked = false

        // When
        mockSdkCore.newRumEventWriteOperation(
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter,
            fakeEventType
        ) { fakeEvent }
            .onError { invoked = true }
            .submit()

        // Then
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            message = WriteOperation.WRITE_OPERATION_FAILED_ERROR,
            throwable = fakeException
        )
        assertThat(invoked)
            .overridingErrorMessage("Expected to invoke onError callback, but it wasn't.")
            .isTrue
    }

    @Test
    fun `M call onError W submit() { event creation throws }`(
        forge: Forge
    ) {
        // Given
        val fakeException = forge.anException()
        var invoked = false

        // When
        mockSdkCore.newRumEventWriteOperation(
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter,
            fakeEventType
        ) { throw fakeException }
            .onError { invoked = true }
            .submit()

        // Then
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.ERROR,
            targets = listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            message = WriteOperation.WRITE_OPERATION_FAILED_ERROR,
            throwable = fakeException
        )
        assertThat(invoked)
            .overridingErrorMessage("Expected to invoke onError callback, but it wasn't.")
            .isTrue
    }

    @Test
    fun `M notify no onError provided W submit() { write failed }`(
        forge: Forge
    ) {
        // Given
        val fakeException = forge.anException()

        // When
        mockSdkCore.newRumEventWriteOperation(
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter,
            fakeEventType
        ) { throw fakeException }
            .submit()

        // Then
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.WARN,
            target = InternalLogger.Target.MAINTAINER,
            message = WriteOperation.NO_ERROR_CALLBACK_PROVIDED_WARNING
        )
    }

    @Test
    fun `M do nothing W submit() { noop writer}`() {
        // Given
        var errorInvoked = false
        var successInvoked = false
        val fakeEvent = Any()
        mockWriter = mock<NoOpDataWriter<Any>>()

        // When
        mockSdkCore.newRumEventWriteOperation(
            fakeDatadogContext,
            mockEventWriteScope,
            mockWriter,
            fakeEventType
        ) { fakeEvent }
            .onError { errorInvoked = true }
            .onSuccess { successInvoked = true }
            .submit()

        // Then
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.INFO,
            target = InternalLogger.Target.USER,
            message = WriteOperation.WRITE_OPERATION_IGNORED
        )
        assertThat(errorInvoked).isTrue()
        assertThat(successInvoked).isFalse()
        verifyNoInteractions(mockWriter)
    }

    companion object {
        val rumMonitor = GlobalRumMonitorTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(rumMonitor)
        }
    }
}
