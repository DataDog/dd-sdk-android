/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.webview;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;
import com.datadog.android.rum.RumWebChromeClient;
import com.datadog.android.rum.RumWebViewClient;
import com.datadog.android.sample.MainActivity;
import com.datadog.android.sample.R;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;

public class WebFragment extends Fragment {

    private WebViewModel mViewModel;
    private WebView mWebView;
    private Scope mMainScope;
    private Span mMainSpan;

    public static WebFragment newInstance() {
        return new WebFragment();
    }

    // region Fragment Lifecycle

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_web, container, false);
        mWebView = rootView.findViewById(R.id.webview);
        mWebView.setWebViewClient(new RumWebViewClient());
        mWebView.setWebChromeClient(new RumWebChromeClient());
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

}
