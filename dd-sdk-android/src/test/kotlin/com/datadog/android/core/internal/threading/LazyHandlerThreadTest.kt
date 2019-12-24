package com.datadog.android.core.internal.threading

import com.datadog.tools.unit.invokeMethod
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import java.util.concurrent.CountDownLatch
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension

@Extensions(
    ExtendWith(MockitoExtension::class)
)
internal class LazyHandlerThreadTest {

    lateinit var underTest: LazyHandlerThread

    @Mock
    lateinit var mockDeferredHandler: AndroidDeferredHandler

    @Mock
    lateinit var mockRunnable1: Runnable
    @Mock
    lateinit var mockRunnable2: Runnable
    @Mock
    lateinit var mockRunnable3: Runnable

    @BeforeEach
    fun `set up`() {
        underTest =
            LazyHandlerThread(
                "LazyHandlerThread",
                { _ -> mockDeferredHandler })
    }

    @Test
    fun `if the looper was not prepared the messages will be queued`() {
        // when
        underTest.post(mockRunnable1)
        underTest.post(mockRunnable2)

        // then
        verifyZeroInteractions(mockDeferredHandler)
    }

    @Test
    fun `when looper prepared the queue will be consumed`() {
        // given
        underTest.post(mockRunnable1)
        underTest.post(mockRunnable2)

        // when
        underTest.invokeMethod("onLooperPrepared")

        // then
        val inOrder = inOrder(mockDeferredHandler)
        inOrder.verify(mockDeferredHandler).handle(mockRunnable1)
        inOrder.verify(mockDeferredHandler).handle(mockRunnable2)
    }

    @Test
    fun `when looper prepared the message will be executed on the handler`() {
        // given
        underTest.invokeMethod("onLooperPrepared")

        // when
        underTest.post(mockRunnable1)
        underTest.post(mockRunnable2)

        // then
        val inOrder = inOrder(mockDeferredHandler)
        inOrder.verify(mockDeferredHandler).handle(mockRunnable1)
        inOrder.verify(mockDeferredHandler).handle(mockRunnable2)
    }

    @Test
    fun `when multiple threads post messages in same time they will be all handled`() {
        val countDownLatch = CountDownLatch(2)
        val thread2 = Thread {
            underTest.post(mockRunnable3)
            countDownLatch.countDown()
        }
        val thread1 = Thread {
            underTest.post(mockRunnable1)
            underTest.post(mockRunnable2)
            underTest.invokeMethod("onLooperPrepared")
            countDownLatch.countDown()
        }

        // when
        thread1.start()
        thread2.start()
        countDownLatch.await()

        // then
        verify(mockDeferredHandler).handle(mockRunnable1)
        verify(mockDeferredHandler).handle(mockRunnable2)
        verify(mockDeferredHandler).handle(mockRunnable3)
    }

    @Test
    fun `when looper ready in second thread the messages will be consumed in order`() {
        val countDownLatch = CountDownLatch(2)
        val thread1 = Thread {
            underTest.post(mockRunnable1)
            underTest.post(mockRunnable2)
            countDownLatch.countDown()
        }
        val thread2 = Thread {
            underTest.invokeMethod("onLooperPrepared")
            underTest.post(mockRunnable3)
            countDownLatch.countDown()
        }
        // when
        thread1.start()
        thread2.start()
        countDownLatch.await()

        // then
        val inOrder = inOrder(mockDeferredHandler)
        inOrder.verify(mockDeferredHandler).handle(mockRunnable1)
        inOrder.verify(mockDeferredHandler).handle(mockRunnable2)
        inOrder.verify(mockDeferredHandler).handle(mockRunnable3)
    }
}
