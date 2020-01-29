/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.sample.logs;

import androidx.lifecycle.ViewModelProviders;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.datadog.android.log.Logger;
import com.datadog.android.sample.MainActivity;
import com.datadog.android.sample.R;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;

public class LogsFragment extends Fragment implements View.OnClickListener{

    private LogsViewModel mViewModel;
    private Logger mLogger = new Logger.Builder()
            .setServiceName("android-sample-java")
            .setLoggerName("logs_fragment")
            .setLogcatLogsEnabled(true)
            .build();

    private int mInteractionsCount = 0;
    private Scope mMainScope;
    private Span mMainSpan;

    public static LogsFragment newInstance() {
        return new LogsFragment();
    }

    // region Fragment

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_logs, container, false);
        rootView.findViewById(R.id.log_warning).setOnClickListener(this);
        rootView.findViewById(R.id.log_error).setOnClickListener(this);
        rootView.findViewById(R.id.log_critical).setOnClickListener(this);
        rootView.findViewById(R.id.crash).setOnClickListener(this);
        return rootView;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mViewModel = ViewModelProviders.of(this).get(LogsViewModel.class);
        mLogger.addAttribute("interactions", mInteractionsCount);
    }

    @Override
    public void onResume() {
        final Tracer tracer = GlobalTracer.get();
        @SuppressWarnings("ConstantConditions")
        final Span mainActivitySpan = ((MainActivity)getActivity()).getMainSpan();
        mMainSpan = tracer
                .buildSpan("LogsFragment").asChildOf(mainActivitySpan).start();
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

    // region View.OnClickListener

    @Override
    public void onClick(View v) {

        mInteractionsCount++;
        mLogger.addAttribute("interactions", mInteractionsCount);
        int i = v.getId() -  R.id.crash;

        switch (v.getId()) {
            case R.id.log_warning :
                mLogger.w("User triggered a warning");
                break;

            case R.id.log_error :
                mLogger.e("User triggered an error", new IllegalStateException("Oops"));
                break;

            case R.id.log_critical :
                mLogger.wtf("User triggered a critical event", new UnsupportedOperationException("Oops"));
                break;

            case R.id.crash:
                // here i is always equal to 0
                crashIfZero(i);
                break;
        }

    }

    private void crashIfZero(int i) {
        int d = 100 / i;
        mLogger.i("Remaining = " + d);
    }

    //endregion

}
