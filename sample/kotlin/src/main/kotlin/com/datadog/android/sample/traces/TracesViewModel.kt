package com.datadog.android.sample.traces

import android.os.AsyncTask
import androidx.lifecycle.ViewModel
import io.opentracing.Span
import io.opentracing.util.GlobalTracer
import java.lang.Exception

class TracesViewModel : ViewModel() {

    lateinit var asyncTask: AsyncTask<Unit, Unit, Unit>
    fun startAsyncOperation(onDone: () -> Unit = {}) {
        asyncTask = Task(onDone)
        asyncTask.execute()
    }

    private class Task(val onDone: () -> Unit) : AsyncTask<Unit, Unit, Unit>() {
        lateinit var activeSpanInMainThread: Span
        override fun onPreExecute() {
            super.onPreExecute()
            activeSpanInMainThread = GlobalTracer.get().activeSpan()
        }

        override fun doInBackground(vararg params: Unit?) {
            // make the main thread active span the active span on this thread
            GlobalTracer.get().activateSpan(activeSpanInMainThread)
            val span = GlobalTracer.get()
                .buildSpan("AsyncOperation")
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
