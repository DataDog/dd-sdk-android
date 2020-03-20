package com.datadog.android.sample.traces

import android.os.AsyncTask
import androidx.lifecycle.ViewModel
import io.opentracing.Span
import io.opentracing.util.GlobalTracer
import java.lang.Exception

class TracesViewModel : ViewModel() {

    var asyncTask: AsyncTask<Unit, Unit, Unit>? = null
    fun startAsyncOperation(onDone: () -> Unit = {}) {
        asyncTask = Task(onDone)
        asyncTask?.execute()
    }

    fun stopAsyncOperations() {
        asyncTask?.cancel(true)
        asyncTask = null
    }

    private class Task(val onDone: () -> Unit) : AsyncTask<Unit, Unit, Unit>() {
        var activeSpanInMainThread: Span? = null

        override fun onPreExecute() {
            super.onPreExecute()
            activeSpanInMainThread = GlobalTracer.get().activeSpan()
        }

        override fun doInBackground(vararg params: Unit?) {
            val span = GlobalTracer.get()
                .buildSpan("AsyncOperation")
                .asChildOf(activeSpanInMainThread)
                .start()
            if (isCancelled) {
                return
            }
            try {
                val scope = GlobalTracer.get().activateSpan(span)
                // just emulate an async operation here
                Thread.sleep(10000)

                scope.close()
            } catch (e: Exception) {
                span.log(e.message)
            } finally {
                span.finish()
            }
        }

        override fun onPostExecute(result: Unit?) {
            onDone()
        }
    }
}
