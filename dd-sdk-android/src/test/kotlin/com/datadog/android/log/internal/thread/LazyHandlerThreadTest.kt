package com.datadog.android.log.internal.thread

import com.datadog.android.log.internal.file.AndroidDeferredHandler
import com.datadog.android.utils.accessMethod
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.verify
import org.assertj.core.api.Assertions.assertThat
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

    @BeforeEach
    fun `set up`() {
        underTest = LazyHandlerThread("LazyHandlerThread")
    }

    @Test
    fun `if the looper was not prepared the messages will be queued`() {
        // when
        underTest.post(mockRunnable1)
        underTest.post(mockRunnable2)
        // then
        assertThat(underTest.messagesQueue).contains(mockRunnable1, mockRunnable2)
    }

    @Test
    fun `when looper prepared the queue will be consumed`() {
        // given
        underTest.post(mockRunnable1)
        underTest.post(mockRunnable2)

        // when
        underTest.accessMethod("onLooperPrepared")

        // then
        assertThat(underTest.messagesQueue).isEmpty()
    }

    @Test
    fun `when looper is prepared the handlers will be initialized`() {
        // when
        underTest.accessMethod("onLooperPrepared")

        // then
        assertThat(underTest.handler).isNotNull()
        assertThat(underTest.deferredHandler).isNotNull()
    }

    @Test
    fun `when looper prepared the message will be executed on the handler`() {
        // given
        underTest.deferredHandler = mockDeferredHandler

        // when
        underTest.post(mockRunnable1)
        underTest.post(mockRunnable2)

        // then
        assertThat(underTest.messagesQueue).isEmpty()
        val inOrder = inOrder(mockDeferredHandler)
        inOrder.verify(mockDeferredHandler).handle(mockRunnable1)
        inOrder.verify(mockDeferredHandler).handle(mockRunnable2)
    }
}
