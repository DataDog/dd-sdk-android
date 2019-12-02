package com.datadog.android.log.internal.thread

import android.os.Handler
import android.os.HandlerThread
import com.datadog.android.log.internal.file.AndroidDeferredHandler
import com.datadog.android.log.internal.file.DeferredHandler
import java.util.LinkedList

/**
 * This is a lazy HandlerThread which will queue any runnable sent before the Looper is
 * prepared in order to be later consumed when the looper is ready.
 */
internal open class LazyHandlerThread(name: String) : HandlerThread(name) {
    internal val messagesQueue: LinkedList<Runnable> = LinkedList()

    internal lateinit var handler: Handler
    internal var deferredHandler: DeferredHandler? = null

    override fun onLooperPrepared() {
        super.onLooperPrepared()
        handler = Handler(looper)
        deferredHandler = AndroidDeferredHandler(handler)
        consumeQueue()
    }

    private fun consumeQueue() {
        while (messagesQueue.isNotEmpty()) {
            post(messagesQueue.poll()!!)
        }
    }

    internal fun post(runnable: Runnable) {
        val currentDeferred = deferredHandler
        if (currentDeferred != null) {
            currentDeferred.handle(runnable)
        } else {
            messagesQueue.add(runnable)
        }
    }
}
