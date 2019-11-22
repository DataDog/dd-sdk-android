/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.sample.webview;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProviders;
import com.datadog.android.log.Logger;
import com.datadog.android.sample.R;
import com.datadog.android.sample.SampleApplication;
import com.datadog.android.sample.logs.LogsViewModel;

import java.lang.annotation.Target;

public class WebFragment extends Fragment {

    private WebViewModel mViewModel;
    private WebView mWebView;

    private Logger mLogger = new Logger.Builder()
            .setNetworkInfoEnabled(true)
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
        FragmentActivity activity = getActivity();
        if (activity != null) {
            mLogger = SampleApplication.fromContext(activity).getLogger();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        mWebView.loadUrl(mViewModel.getUrl());
    }

    // endregion

    // region WebViewClient

    private WebViewClient mWebViewClient = new WebViewClient() {

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            mLogger.addAttribute("webview_url", url);
            mLogger.d("event: onPageStarted");
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            mLogger.d("event: onPageFinished");
        }

        @Override
        public void onLoadResource(WebView view, String url) {
            super.onLoadResource(view, url);
            mLogger.d("loading resource: " + url);
        }

        @Override
        @TargetApi(Build.VERSION_CODES.M)
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            super.onReceivedError(view, request, error);
            mLogger.e(
                    "received error " + error.getErrorCode() + ":"
                            + error.getDescription() + " on: "
                            + request.getMethod() + " / " + request.getUrl()
            );
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
            super.onReceivedHttpError(view, request, errorResponse);
            mLogger.e(
                    "received HTTP error " + errorResponse.getStatusCode() + ":"
                            + errorResponse.getReasonPhrase() + " on: "
                            + request.getMethod() + " / " + request.getUrl()
            );
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            super.onReceivedSslError(view, handler, error);
            mLogger.e(
                    "received SSL error " + error.getPrimaryError()
                            + " on: " + error.getUrl()
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
        public void onReceivedTitle(WebView view, String title) {
            super.onReceivedTitle(view, title);
            mLogger.v("event: onReceivedTitle <" + title + ">");
        }

        @Override
        public void onReceivedIcon(WebView view, Bitmap icon) {
            super.onReceivedIcon(view, icon);
            mLogger.v("event: onReceivedIcon");
        }

        @Override
        public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
            mLogger.w("received JS alert : " + message + " on: " + url);
            return super.onJsAlert(view, url, message, result);
        }

        @Override
        public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
            mLogger.d("received console message : " + consoleMessage.message());
            return super.onConsoleMessage(consoleMessage);
        }
    };

    // region
}
