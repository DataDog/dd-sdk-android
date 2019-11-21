/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.sample;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.datadog.android.log.Logger;


public class MainActivity extends AppCompatActivity {

    private Logger logger;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        logger = SampleApplication.fromContext(this).getLogger();
        logger.d("MainActivity/onCreate");
    }

    @Override
    protected void onStart() {
        super.onStart();
        logger.d("MainActivity/onStart");
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        logger.d("MainActivity/onRestart");
    }

    @Override
    protected void onResume() {
        super.onResume();
        logger.d("MainActivity/onResume");
    }

    @Override
    protected void onPause() {
        super.onPause();
        logger.d("MainActivity/onPause");
    }

    @Override
    protected void onStop() {
        super.onStop();
        logger.d("MainActivity/onStop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        logger.d("MainActivity/onDestroy");
    }
}
