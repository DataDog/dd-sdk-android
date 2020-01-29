/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.sample.webview;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;

import com.datadog.android.log.Logger;
import com.datadog.android.sample.MainActivity;
import com.datadog.android.sample.R;

import java.util.HashMap;
import java.util.Locale;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;

public class WebFragment extends Fragment {

    private WebViewModel mViewModel;
    private WebView mWebView;
    private Scope mMainScope;
    private Span mMainSpan;


    private Logger mLogger = new Logger.Builder()
            .setLogcatLogsEnabled(true)
            .setServiceName("android-sample-java")
            .setLoggerName("web_fragment")
            .setNetworkInfoEnabled(true)
            .setLogcatLogsEnabled(true)
            .build();

    public static WebFragment newInstance() {
        return new WebFragment();
    }

    // region Fragment Lifecycle

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_web, container, false);
        mWebView = rootView.findViewById(R.id.webview);
        mWebView.setWebViewClient(mWebViewClient);
        mWebView.setWebChromeClient(mWebChromeClient);
        return rootView;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mViewModel = ViewModelProviders.of(this).get(WebViewModel.class);
    }

    @Override
    public void onStart() {
        super.onStart();
        mWebView.loadUrl(mViewModel.getUrl());
    }

    @Override
    public void onResume() {
        final Tracer tracer = GlobalTracer.get();
        @SuppressWarnings("ConstantConditions") final Span mainActivitySpan = ((MainActivity) getActivity()).getMainSpan();
        mMainSpan = tracer
                .buildSpan("WebViewFragment").asChildOf(mainActivitySpan).start();
        mMainScope = tracer.activateSpan(mMainSpan);
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mMainScope.close();
        mMainSpan.finish();
    }

    // endregion

    // region WebViewClient

    private WebViewClient mWebViewClient = new WebViewClient() {
        private Span mOnPageStartedOnPageClosedSpan = null;

        @Override
        public void onPageStarted(WebView view, final String url, Bitmap favicon) {
            final Tracer tracer = GlobalTracer.get();
            mOnPageStartedOnPageClosedSpan = tracer
                    .buildSpan("WebViewInitialisation")
                    .asChildOf(tracer.activeSpan())
                    .start();
            super.onPageStarted(view, url, favicon);
            mLogger.d(
                    "onPageStarted",
                    null,
                    new HashMap<String, Object>() {{
                        put("http.url", url);
                    }}
            );
        }

        @Override
        public void onPageFinished(WebView view, final String url) {
            super.onPageFinished(view, url);
            mLogger.d(
                    "onPageFinished",
                    null,
                    new HashMap<String, Object>() {{
                        put("http.url", url);
                    }}
            );
            if (mOnPageStartedOnPageClosedSpan != null) {
                mOnPageStartedOnPageClosedSpan.finish();
            }
        }

        @Override
        public void onLoadResource(WebView view, final String url) {
            super.onLoadResource(view, url);
            mLogger.d(
                    "loading resource",
                    null,
                    new HashMap<String, Object>() {{
                        put("http.url", url);
                    }}
            );
        }

        @Override
        @TargetApi(Build.VERSION_CODES.M)
        public void onReceivedError(WebView view,
                                    final WebResourceRequest request,
                                    final WebResourceError error) {
            super.onReceivedError(view, request, error);
            if (mOnPageStartedOnPageClosedSpan != null) {
                String logError =
                        String.format(Locale.US,
                                "Received error: %d for request: %s",
                                error.getErrorCode(),
                                request.getUrl());

                mOnPageStartedOnPageClosedSpan.log(logError);
            }
            mLogger.e(
                    "received error",
                    null,
                    new HashMap<String, Object>() {{
                        put("http.request.method", request.getMethod());
                        put("http.url", request.getUrl());
                        put("http.error.code", error.getErrorCode());
                        put("http.error.description", error.getDescription());
                    }}
            );
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onReceivedHttpError(WebView view,
                                        final WebResourceRequest request,
                                        final WebResourceResponse errorResponse) {
            super.onReceivedHttpError(view, request, errorResponse);
            if (mOnPageStartedOnPageClosedSpan != null) {
                String logError =
                        String.format(Locale.US,
                                "Received error: %s for request: %s",
                                errorResponse.getReasonPhrase(),
                                request.getUrl());

                mOnPageStartedOnPageClosedSpan.log(logError);
            }
            mLogger.e(
                    "received HTTP error",
                    null,
                    new HashMap<String, Object>() {{
                        put("http.request.method", request.getMethod());
                        put("http.url", request.getUrl());
                        put("http.error.code", errorResponse.getStatusCode());
                        put("http.error.description", errorResponse.getReasonPhrase());
                    }}
            );
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, final SslError error) {
            super.onReceivedSslError(view, handler, error);
            if (mOnPageStartedOnPageClosedSpan != null) {
                String logError =
                        String.format(Locale.US,
                                "Received SSL error for request: %s", error.getUrl());

                mOnPageStartedOnPageClosedSpan.log(logError);
            }
            mLogger.e(
                    "received SSL error",
                    null,
                    new HashMap<String, Object>() {{
                        put("http.url", error.getUrl());
                        put("http.error.code", error.getPrimaryError());
                    }}
            );
        }
    };

    // endregion

    // region WebChromeClient

    private WebChromeClient mWebChromeClient = new WebChromeClient() {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            super.onProgressChanged(view, newProgress);

        }

        @Override
        public void onReceivedTitle(WebView view, final String title) {
            super.onReceivedTitle(view, title);
            mLogger.v(
                    "onReceivedTitle",
                    null,
                    new HashMap<String, Object>() {{
                        put("webview.title", title);
                    }});
        }

        @Override
        public void onReceivedIcon(WebView view, Bitmap icon) {
            super.onReceivedIcon(view, icon);
            mLogger.v("event: onReceivedIcon");
        }

        @Override
        public boolean onJsAlert(
                WebView view,
                final String url,
                final String message,
                final JsResult result
        ) {
            mLogger.w(
                    "onJsAlert",
                    null,
                    new HashMap<String, Object>() {{
                        put("http.url", url);
                        put("webview.alert.message", message);
                        put("webview.alert.result", result);
                    }});
            return super.onJsAlert(view, url, message, result);
        }

        @Override
        public boolean onConsoleMessage(final ConsoleMessage consoleMessage) {
            int level;
            switch (consoleMessage.messageLevel()) {
                case TIP:
                    level = Log.INFO;
                    break;
                case LOG:
                    level = Log.VERBOSE;
                    break;
                case WARNING:
                    level = Log.WARN;
                    break;
                case ERROR:
                    level = Log.ERROR;
                    break;
                case DEBUG:
                default:
                    level = Log.DEBUG;
                    break;
            }

            mLogger.log(
                    level,
                    "onConsoleMessage",
                    null,
                    new HashMap<String, Object>() {{
                        put("webview.console.message", consoleMessage);
                    }});
            return super.onConsoleMessage(consoleMessage);
        }
    };

    // region


}
