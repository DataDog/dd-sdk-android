package com.datadog.android.sample.traces;

import android.os.AsyncTask;

import androidx.lifecycle.ViewModel;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.util.GlobalTracer;

public class TracesViewModel extends ViewModel {

    private Task asyncTask;

    public void startAsyncOperation(Task.Callback callback) {
        asyncTask = new Task(callback);
        asyncTask.execute();
    }

    public void stopAsyncOperations() {
        if (asyncTask != null) {
            asyncTask.cancel(true);
            asyncTask = null;
        }
    }

    public static class Task extends AsyncTask<Void, Void, Void> {
        private final Callback callback;

        public Task(Callback callback) {
            this.callback = callback;
        }

        private Span activeSpanInMainThread;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            activeSpanInMainThread = GlobalTracer.get().activeSpan();
        }

        @Override
        protected Void doInBackground(Void... voids) {
            // make the main thread active span the active span on this thread
            GlobalTracer.get().activateSpan(activeSpanInMainThread);
            final Span span = GlobalTracer.get()
                    .buildSpan("AsyncOperation")
                    .start();
            if (isCancelled()) {
                return null;
            }
            try {
                final Scope scope = GlobalTracer.get().activateSpan(span);
                // just emulate an async operation here
                Thread.sleep(10000);
                scope.close();
            } catch (Exception e) {
                span.log(e.getMessage());
            } finally {
                span.finish();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            callback.onDone();
        }


        public interface Callback {
            void onDone();
        }
    }
}
