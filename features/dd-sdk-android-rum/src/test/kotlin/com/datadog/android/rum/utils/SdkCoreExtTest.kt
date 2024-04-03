package com.datadog.android.rum.utils

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.NoOpDataWriter
import com.datadog.android.rum.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.rum.utils.forge.Configurator
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

    @Forgery
    lateinit var fakeDatadogContext: DatadogContext

    @Mock
    lateinit var mockRumFeatureScope: FeatureScope

    @Mock
    lateinit var mockEventBatchWriter: EventBatchWriter

    @Mock
    lateinit var mockWriter: DataWriter<Any>

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    private val mockSdkCore
        get() = rumMonitor.mockSdkCore

    @BeforeEach
    fun `set up`() {
        whenever(mockRumFeatureScope.withWriteContext(any(), any())) doAnswer {
            val callback = it.getArgument<(DatadogContext, EventBatchWriter) -> Unit>(1)
            callback.invoke(fakeDatadogContext, mockEventBatchWriter)
        }
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockWriter.write(eq(mockEventBatchWriter), any())) doReturn true
    }

    @Test
    fun `𝕄 write data 𝕎 submit()`() {
        // Given
        val fakeEvent = Any()

        // When
        mockSdkCore.newRumEventWriteOperation(mockWriter) {
            fakeEvent
        }
            .submit()

        // Then
        verify(mockWriter).write(mockEventBatchWriter, fakeEvent)
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `𝕄 call onSuccess 𝕎 submit() { write succeeded } `() {
        // Given
        val fakeEvent = Any()
        var invoked = false

        // When
        mockSdkCore.newRumEventWriteOperation(mockWriter) {
            fakeEvent
        }
            .onSuccess {
                invoked = true
            }
            .submit()

        // Then
        verifyNoInteractions(mockInternalLogger)
        assertThat(invoked)
            .overridingErrorMessage("Expected to invoke onSuccess callback, but it wasn't.")
            .isTrue
    }

    @Test
    fun `𝕄 call onError 𝕎 submit() { write was not successful }`() {
        // Given
        val fakeEvent = Any()
        whenever(mockWriter.write(eq(mockEventBatchWriter), any())) doReturn false
        var invoked = false

        // When
        mockSdkCore.newRumEventWriteOperation(mockWriter) {
            fakeEvent
        }
            .onError {
                invoked = true
            }
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
    fun `𝕄 call onError 𝕎 submit() { write throws }`(
        forge: Forge
    ) {
        // Given
        val fakeEvent = Any()
        val fakeException = forge.anException()
        whenever(mockWriter.write(eq(mockEventBatchWriter), any())) doThrow fakeException
        var invoked = false

        // When
        mockSdkCore.newRumEventWriteOperation(mockWriter) {
            fakeEvent
        }
            .onError {
                invoked = true
            }
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
    fun `𝕄 call onError 𝕎 submit() { event creation throws }`(
        forge: Forge
    ) {
        // Given
        val fakeException = forge.anException()
        var invoked = false

        // When
        mockSdkCore.newRumEventWriteOperation(mockWriter) {
            throw fakeException
        }
            .onError {
                invoked = true
            }
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
    fun `𝕄 notify no onError provided 𝕎 submit() { write failed }`(
        forge: Forge
    ) {
        // Given
        val fakeException = forge.anException()

        // When
        mockSdkCore.newRumEventWriteOperation(mockWriter) {
            throw fakeException
        }
            .submit()

        // Then
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.WARN,
            target = InternalLogger.Target.MAINTAINER,
            message = WriteOperation.NO_ERROR_CALLBACK_PROVIDED_WARNING
        )
    }

    @Test
    fun `𝕄 do nothing 𝕎 submit() { noop writer}`() {
        // Given
        var errorInvoked = false
        var successInvoked = false
        val fakeEvent = Any()
        mockWriter = mock<NoOpDataWriter<Any>>()

        // When
        mockSdkCore.newRumEventWriteOperation(mockWriter) { fakeEvent }
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
