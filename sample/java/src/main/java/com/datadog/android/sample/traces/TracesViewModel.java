/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.traces;

import android.os.AsyncTask;
import androidx.lifecycle.ViewModel;
import com.datadog.android.rum.RumInterceptor;
import com.datadog.android.tracing.TracingInterceptor;
import com.facebook.stetho.okhttp3.StethoInterceptor;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.util.GlobalTracer;
import okhttp3.OkHttpClient;
import okhttp3.Response;

public class TracesViewModel extends ViewModel {

    private Task asyncTask;

    private Request request;

    public void startRequest(Request.Callback callback) {
        request = new Request(callback);
        request.execute();
    }

    public void startAsyncOperation(Task.Callback callback) {
        asyncTask = new Task(callback);
        asyncTask.execute();
    }

    public void stopAsyncOperations() {
        if (asyncTask != null) {
            asyncTask.cancel(true);
            asyncTask = null;
        }

        if (request != null) {
            request.cancel(true);
            request = null;
        }
    }

    public static class Request extends AsyncTask<Void, Void, Request.Result> {

        static class Result {

            public Response response;
            public Exception exception;

            public Result(Response response, Exception exception) {
                this.response = response;
                this.exception = exception;
            }

        }

        public interface Callback {
            void onResult(Result result);
        }

        private final Callback callback;

        public Request(Callback callback) {
            this.callback = callback;
        }

        private OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(new TracingInterceptor())
                .addInterceptor(new RumInterceptor())
                .addNetworkInterceptor(new StethoInterceptor())
                .build();

        @Override
        protected Result doInBackground(Void... voids) {
            okhttp3.Request request = new okhttp3.Request.Builder()
                    .get()
                    .url("https://dd.datad0g.com/")
                    .build();

            try {
                Response response = okHttpClient.newCall(request).execute();
                return new Result(response, null);
            } catch (Exception e) {
                return new Result(null, e);
            }
        }

        @Override
        protected void onPostExecute(Result result) {
            super.onPostExecute(result);
            callback.onResult(result);
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
