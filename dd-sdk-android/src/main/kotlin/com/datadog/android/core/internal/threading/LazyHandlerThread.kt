package com.datadog.android.core.internal.threading

import android.os.Handler
import android.os.HandlerThread
import java.util.LinkedList

/**
 * This is a lazy HandlerThread which will queue any runnable sent before the Looper is
 * prepared in order to be later consumed when the looper is ready.
 */
internal open class LazyHandlerThread(
    name: String,
    private val handlerBuilder: (Handler) ->
    DeferredHandler = { handler ->
        AndroidDeferredHandler(
            handler
        )
    }
) :
    HandlerThread(name) {
    private val messagesQueue: LinkedList<Runnable> = LinkedList()

    @Volatile
    internal lateinit var handler: Handler
    @Volatile
    internal var deferredHandler: DeferredHandler? = null

    override fun onLooperPrepared() {
        super.onLooperPrepared()
        synchronized(this) {
            handler = Handler(looper)
            deferredHandler = handlerBuilder(handler)
            consumeQueue()
        }
    }

    private fun consumeQueue() {
        while (messagesQueue.isNotEmpty()) {
            val runnable = messagesQueue.poll()
            if (runnable != null) {
                post(runnable)
            }
        }
    }

    internal fun post(runnable: Runnable) {
        val currentDeferred = deferredHandler
        if (currentDeferred == null) {
            synchronized(this) {
                messagesQueue.add(runnable)
            }
        } else {
            currentDeferred.handle(runnable)
        }
    }
}
