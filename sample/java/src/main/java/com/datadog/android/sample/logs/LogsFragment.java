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
import com.datadog.android.sample.R;

import java.util.Random;

public class LogsFragment extends Fragment implements View.OnClickListener{

    private LogsViewModel mViewModel;
    private Logger mLogger = new Logger.Builder()
            .setServiceName("android-sample-java")
            .setLoggerName("logs_fragment")
            .setLogcatLogsEnabled(true)
            .build();

    private int interactionsCount = 0;

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
        mLogger.addAttribute("interactions", interactionsCount);
    }

    @Override
    public void onResume() {
        super.onResume();

        Random r = new Random();
        try {
            Thread.sleep((r.nextInt() % 200) + 2000L );
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    // endregion

    // region View.OnClickListener

    @Override
    public void onClick(View v) {

        Random r = new Random();
        try {
            Thread.sleep((r.nextInt() % 200) + 300L );
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        interactionsCount++;
        mLogger.addAttribute("interactions", interactionsCount);
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
