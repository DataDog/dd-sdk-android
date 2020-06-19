/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.traces;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;
import com.datadog.android.log.Logger;
import com.datadog.android.sample.MainActivity;
import com.datadog.android.sample.R;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;

public class TracesFragment extends Fragment implements View.OnClickListener {

    private Scope mMainScope;
    private Span mMainSpan;
    private ProgressBar mSpinner;
    private TracesViewModel mViewModel;
    private ImageView mRequestStatus;

    private final Logger mLogger = new Logger.Builder()
            .setLoggerName("traces_fragment")
            .setLogcatLogsEnabled(true)
            .build();

    public static TracesFragment newInstance() {
        return new TracesFragment();
    }

    // region Fragment

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_traces, container, false);
        rootView.findViewById(R.id.start_async_operation).setOnClickListener(this);
        rootView.findViewById(R.id.start_request).setOnClickListener(this);
        mSpinner = rootView.findViewById(R.id.spinner);
        mRequestStatus = rootView.findViewById(R.id.request_status);
        return rootView;
    }

    @Override
    public void onResume() {
        final Tracer tracer = GlobalTracer.get();
        @SuppressWarnings("ConstantConditions") final Span mainActivitySpan = ((MainActivity) getActivity()).getMainSpan();
        mMainSpan = tracer
                .buildSpan("TracesFragment").asChildOf(mainActivitySpan).start();
        mMainScope = tracer.activateSpan(mMainSpan);
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mMainScope.close();
        mMainSpan.finish();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mViewModel.stopAsyncOperations();
        mSpinner.setVisibility(View.INVISIBLE);
        mRequestStatus.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mViewModel = ViewModelProviders.of(this).get(TracesViewModel.class);
    }

    // endregion

    // region View.OnClickListener

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.start_async_operation) {
            mLogger.w("User triggered an async operation");
            mSpinner.setVisibility(View.VISIBLE);
            mViewModel.startAsyncOperation(new TracesViewModel.Task.Callback() {
                @Override
                public void onDone() {
                    mSpinner.setVisibility(View.INVISIBLE);
                }
            });
        } else if (v.getId() == R.id.start_request) {
            mLogger.w("User triggered an http request");
            mRequestStatus.setVisibility(View.VISIBLE);
            mRequestStatus.setImageResource(R.drawable.ic_unknown_grey_24dp);
            mViewModel.startRequest(new TracesViewModel.Request.Callback() {
                @Override
                public void onResult(TracesViewModel.Request.Result result) {
                    if (result.response == null) {
                        mRequestStatus.setImageResource(R.drawable.ic_cancel_red_24dp);
                    } else if (result.response.code() >= 400) {
                        mRequestStatus.setImageResource(R.drawable.ic_error_red_24dp);
                    } else {
                        mRequestStatus.setImageResource(R.drawable.ic_check_circle_green_24dp);
                    }
                }
            });
        }
    }

    // endregion
}
