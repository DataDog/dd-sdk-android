package com.datadog.android.core.internal.persistence

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.EventWriteScope
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
class AsyncEventWriteScopeTest {

    private lateinit var testedScope: EventWriteScope

    @Mock
    lateinit var mockEventBatchWriter: EventBatchWriter

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockExecutor: Executor

    @StringForgery
    lateinit var fakeFeatureName: String

    private val fakeWriteLock = Any()

    @BeforeEach
    fun `set up`() {
        whenever(mockExecutor.execute(any())) doAnswer {
            it.getArgument<Runnable>(0)
                .run()
        }

        testedScope =
            AsyncEventWriteScope(mockExecutor, mockEventBatchWriter, fakeWriteLock, fakeFeatureName, mockInternalLogger)
    }

    @Test
    fun `M invoke with writer W invoke()`() {
        // Given
        val mockCallback = mock<(EventBatchWriter) -> Unit>()

        // When
        testedScope.invoke(mockCallback)

        // Then
        verify(mockCallback).invoke(mockEventBatchWriter)
    }

    @Test
    fun `M do sequential metadata write W invoke() { multithreaded }`(
        @IntForgery(min = 2, max = 10) threadsCount: Int,
        @Forgery fakeEventType: EventType,
        forge: Forge
    ) {
        // Given
        val executor = Executors.newFixedThreadPool(threadsCount)
        var accumulator: Byte = 0
        val event = forge.aString().toByteArray()
        // each write operation is going to increase value in meta by 1
        // in the end if some write operation was parallel to another, total number in meta
        // won't be equal to the number of threads
        // if write operations are parallel, there is a chance that there will be a conflict
        // updating the meta (applying different updates to the same original state).
        val callback: (EventBatchWriter) -> Unit = {
            val value = it.currentMetadata()?.first() ?: 0
            it.write(
                event = RawBatchEvent(data = event),
                batchMetadata = byteArrayOf((value + 1).toByte()),
                eventType = fakeEventType
            )
        }

        whenever(mockEventBatchWriter.currentMetadata()) doAnswer { byteArrayOf(accumulator) }
        whenever(
            mockEventBatchWriter.write(any(), any(), any())
        ) doAnswer {
            val value = it.getArgument<ByteArray>(1).first()
            accumulator = value
            true
        }

        // When
        repeat(threadsCount) {
            AsyncEventWriteScope(executor, mockEventBatchWriter, fakeWriteLock, fakeFeatureName, mockInternalLogger)
                .invoke(callback)
        }
        executor.shutdown()
        executor.awaitTermination(1, TimeUnit.SECONDS)

        // Then
        assertThat(accumulator).isEqualTo(threadsCount.toByte())
    }
}
