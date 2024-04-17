package com.datadog.android.core.internal.thread

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.configuration.BackPressureMitigation
import com.datadog.android.core.configuration.BackPressureStrategy
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
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
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.quality.Strictness
import java.lang.Thread.sleep
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.min

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
class DropOldestBackPressuredBlockingQueueTest {

    lateinit var testedQueue: BlockingQueue<String>

    @Mock
    lateinit var mockLogger: InternalLogger

    @Mock
    lateinit var mockOnThresholdReached: () -> Unit

    @Mock
    lateinit var mockOnItemsDropped: (Any) -> Unit

    @IntForgery(8, 16)
    var fakeBackPressureThreshold: Int = 0

    @BeforeEach
    fun `set up`(forge: Forge) {
        testedQueue = BackPressuredBlockingQueue(
            mockLogger,
            forge.anAlphabeticalString(),
            BackPressureStrategy(
                fakeBackPressureThreshold,
                mockOnThresholdReached,
                mockOnItemsDropped,
                BackPressureMitigation.DROP_OLDEST
            )
        )
    }

    // region add(e)

    @Test
    fun `M accept item W add() {empty}`(
        @StringForgery fakeNewItem: String
    ) {
        // Given

        // When
        val result = testedQueue.add(fakeNewItem)

        // Then
        assertThat(result).isTrue()
        assertThat(testedQueue).contains(fakeNewItem)
        verifyNoInteractions(mockOnItemsDropped, mockOnThresholdReached, mockLogger)
    }

    @Test
    fun `M accept item W add() { not reached threshold yet }`(
        @StringForgery fakeItemList: List<String>,
        @StringForgery fakeNewItem: String
    ) {
        // Given
        val previousCount = min(fakeBackPressureThreshold / 2, fakeItemList.size)
        fakeItemList.take(previousCount).forEach {
            testedQueue.add(it)
        }

        // When
        val result = testedQueue.add(fakeNewItem)

        // Then
        assertThat(result).isTrue()
        assertThat(testedQueue).hasSize(previousCount + 1)
        assertThat(testedQueue).contains(fakeNewItem)
        verifyNoInteractions(mockOnItemsDropped, mockOnThresholdReached, mockLogger)
    }

    @Test
    fun `M accept item W add() { reaching threshold on last item }`(
        @StringForgery fakeItemList: List<String>,
        @StringForgery fakeNewItem: String
    ) {
        // Given
        for (i in 1 until fakeBackPressureThreshold) {
            testedQueue.add(fakeItemList[i % fakeItemList.size])
        }

        // When
        val result = testedQueue.add(fakeNewItem)

        // Then
        assertThat(result).isTrue()
        assertThat(testedQueue).hasSize(fakeBackPressureThreshold)
        assertThat(testedQueue).contains(fakeNewItem)
        verify(mockOnThresholdReached).invoke()
        verifyNoInteractions(mockOnItemsDropped)
    }

    @Test
    fun `M drop old item and accept W add() { queue already at threshold }`(
        @StringForgery fakeItemList: List<String>,
        @StringForgery fakeNewItem: String
    ) {
        // Given
        for (i in 0 until fakeBackPressureThreshold) {
            testedQueue.add(fakeItemList[i % fakeItemList.size])
        }

        // When
        val result = testedQueue.add(fakeNewItem)

        // Then
        assertThat(result).isTrue()
        assertThat(testedQueue).hasSize(fakeBackPressureThreshold)
        assertThat(testedQueue).contains(fakeNewItem)
        verify(mockOnThresholdReached).invoke()
        verify(mockOnItemsDropped).invoke(fakeItemList.first())
    }

    // endregion

    // region offer(e)

    @Test
    fun `M accept item W offer() {empty}`(
        @StringForgery fakeNewItem: String
    ) {
        // Given

        // When
        val result = testedQueue.offer(fakeNewItem)

        // Then
        assertThat(result).isTrue()
        assertThat(testedQueue).contains(fakeNewItem)
        verifyNoInteractions(mockOnItemsDropped, mockOnThresholdReached, mockLogger)
    }

    @Test
    fun `M accept item W offer() { not reached threshold yet }`(
        @StringForgery fakeItemList: List<String>,
        @StringForgery fakeNewItem: String
    ) {
        // Given
        val previousCount = min(fakeBackPressureThreshold / 2, fakeItemList.size)
        fakeItemList.take(previousCount).forEach {
            testedQueue.add(it)
        }

        // When
        val result = testedQueue.offer(fakeNewItem)

        // Then
        assertThat(result).isTrue()
        assertThat(testedQueue).hasSize(previousCount + 1)
        assertThat(testedQueue).contains(fakeNewItem)
        verifyNoInteractions(mockOnItemsDropped, mockOnThresholdReached, mockLogger)
    }

    @Test
    fun `M accept item W offer() { reaching threshold on last item }`(
        @StringForgery fakeItemList: List<String>,
        @StringForgery fakeNewItem: String
    ) {
        // Given
        for (i in 1 until fakeBackPressureThreshold) {
            testedQueue.add(fakeItemList[i % fakeItemList.size])
        }

        // When
        val result = testedQueue.offer(fakeNewItem)

        // Then
        assertThat(result).isTrue()
        assertThat(testedQueue).hasSize(fakeBackPressureThreshold)
        assertThat(testedQueue).contains(fakeNewItem)
        verify(mockOnThresholdReached).invoke()
        verifyNoInteractions(mockOnItemsDropped)
    }

    @Test
    fun `M drop old item and accept W offer() { queue already at threshold }`(
        @StringForgery fakeItemList: List<String>,
        @StringForgery fakeNewItem: String
    ) {
        // Given
        for (i in 0 until fakeBackPressureThreshold) {
            testedQueue.add(fakeItemList[i % fakeItemList.size])
        }

        // When
        val result = testedQueue.offer(fakeNewItem)

        // Then
        assertThat(result).isTrue()
        assertThat(testedQueue).hasSize(fakeBackPressureThreshold)
        assertThat(testedQueue).contains(fakeNewItem)
        verify(mockOnThresholdReached).invoke()
        verify(mockOnItemsDropped).invoke(fakeItemList.first())
    }

    // endregion

    // region offer(e, timeout)

    @Test
    fun `M accept item W offer() {empty}`(
        @StringForgery fakeNewItem: String,
        @LongForgery(10, 100) fakeTimeoutMs: Long
    ) {
        // Given

        // When
        val result = testedQueue.offer(fakeNewItem, fakeTimeoutMs, TimeUnit.MILLISECONDS)

        // Then
        assertThat(result).isTrue()
        assertThat(testedQueue).contains(fakeNewItem)
        verifyNoInteractions(mockOnItemsDropped, mockOnThresholdReached, mockLogger)
    }

    @Test
    fun `M accept item W offer() { not reached threshold yet }`(
        @StringForgery fakeItemList: List<String>,
        @StringForgery fakeNewItem: String,
        @LongForgery(10, 100) fakeTimeoutMs: Long
    ) {
        // Given
        val previousCount = min(fakeBackPressureThreshold / 2, fakeItemList.size)
        fakeItemList.take(previousCount).forEach {
            testedQueue.add(it)
        }

        // When
        val result = testedQueue.offer(fakeNewItem, fakeTimeoutMs, TimeUnit.MILLISECONDS)

        // Then
        assertThat(result).isTrue()
        assertThat(testedQueue).hasSize(previousCount + 1)
        assertThat(testedQueue).contains(fakeNewItem)
        verifyNoInteractions(mockOnItemsDropped, mockOnThresholdReached, mockLogger)
    }

    @Test
    fun `M accept item W offer() { reaching threshold on last item }`(
        @StringForgery fakeItemList: List<String>,
        @StringForgery fakeNewItem: String,
        @LongForgery(10, 100) fakeTimeoutMs: Long
    ) {
        // Given
        for (i in 1 until fakeBackPressureThreshold) {
            testedQueue.add(fakeItemList[i % fakeItemList.size])
        }

        // When
        val result = testedQueue.offer(fakeNewItem, fakeTimeoutMs, TimeUnit.MILLISECONDS)

        // Then
        assertThat(result).isTrue()
        assertThat(testedQueue).hasSize(fakeBackPressureThreshold)
        assertThat(testedQueue).contains(fakeNewItem)
        verify(mockOnThresholdReached).invoke()
        verifyNoInteractions(mockOnItemsDropped)
    }

    @Test
    fun `M accept item W offer() { queue already at threshold, waiting for space }`(
        @StringForgery fakeItemList: List<String>,
        @StringForgery fakeNewItem: String,
        @LongForgery(10, 100) fakeTimeoutMs: Long
    ) {
        // Given
        for (i in 1 until fakeBackPressureThreshold) {
            testedQueue.add(fakeItemList[i % fakeItemList.size])
        }

        // When
        Thread {
            sleep(fakeTimeoutMs - 5)
            testedQueue.take()
        }.start()
        val result = testedQueue.offer(fakeNewItem, fakeTimeoutMs, TimeUnit.MILLISECONDS)

        // Then
        assertThat(result).isTrue()
        assertThat(testedQueue).hasSize(fakeBackPressureThreshold)
        assertThat(testedQueue).contains(fakeNewItem)
        verify(mockOnThresholdReached).invoke()
        verifyNoInteractions(mockOnItemsDropped)
    }

    @Test
    fun `M drop old item and accept W offer() { queue already at threshold }`(
        @StringForgery fakeItemList: List<String>,
        @StringForgery fakeNewItem: String,
        @LongForgery(10, 100) fakeTimeoutMs: Long
    ) {
        // Given
        for (i in 0 until fakeBackPressureThreshold) {
            testedQueue.add(fakeItemList[i % fakeItemList.size])
        }

        // When
        val result = testedQueue.offer(fakeNewItem, fakeTimeoutMs, TimeUnit.MILLISECONDS)

        // Then
        assertThat(result).isTrue()
        assertThat(testedQueue).hasSize(fakeBackPressureThreshold)
        assertThat(testedQueue).contains(fakeNewItem)
        verify(mockOnThresholdReached).invoke()
        verify(mockOnItemsDropped).invoke(fakeItemList.first())
    }

    // endregion

    // region put(e)

    @Test
    fun `M accept item W put() {empty}`(
        @StringForgery fakeNewItem: String
    ) {
        // Given

        // When
        testedQueue.put(fakeNewItem)

        // Then
        assertThat(testedQueue).contains(fakeNewItem)
        verifyNoInteractions(mockOnItemsDropped, mockOnThresholdReached, mockLogger)
    }

    @Test
    fun `M accept item W put() { not reached threshold yet }`(
        @StringForgery fakeItemList: List<String>,
        @StringForgery fakeNewItem: String
    ) {
        // Given
        val previousCount = min(fakeBackPressureThreshold / 2, fakeItemList.size)
        fakeItemList.take(previousCount).forEach {
            testedQueue.put(it)
        }

        // When
        testedQueue.put(fakeNewItem)

        // Then
        assertThat(testedQueue).hasSize(previousCount + 1)
        assertThat(testedQueue).contains(fakeNewItem)
        verifyNoInteractions(mockOnItemsDropped, mockOnThresholdReached, mockLogger)
    }

    @Test
    fun `M accept item W put() { reaching threshold on last item }`(
        @StringForgery fakeItemList: List<String>,
        @StringForgery fakeNewItem: String
    ) {
        // Given
        for (i in 1 until fakeBackPressureThreshold) {
            testedQueue.put(fakeItemList[i % fakeItemList.size])
        }

        // When
        testedQueue.put(fakeNewItem)

        // Then
        assertThat(testedQueue).hasSize(fakeBackPressureThreshold)
        assertThat(testedQueue).contains(fakeNewItem)
        verifyNoInteractions(mockOnItemsDropped, mockOnThresholdReached, mockLogger)
    }

    @Test
    fun `M wait and accept W put() { queue already at threshold }`(
        @StringForgery fakeItemList: List<String>,
        @StringForgery fakeNewItem: String
    ) {
        // Given
        for (i in 0 until fakeBackPressureThreshold) {
            testedQueue.put(fakeItemList[i % fakeItemList.size])
        }

        // When
        Thread {
            // put() inserts the specified element into this queue, waiting if necessary for space to become available.
            // In order to not wait indefinitely, we need to remove an element
            sleep(100)
            testedQueue.take()
        }.start()
        testedQueue.put(fakeNewItem)

        // Then
        assertThat(testedQueue).hasSize(fakeBackPressureThreshold)
        assertThat(testedQueue).contains(fakeNewItem)
        verifyNoInteractions(mockOnItemsDropped, mockOnThresholdReached, mockLogger)
    }

    // endregion
}
