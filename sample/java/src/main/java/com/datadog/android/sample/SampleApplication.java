/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.sample;

import android.app.Application;
import android.content.Context;
import android.util.Log;
import com.datadog.android.Datadog;
import com.datadog.android.log.Logger;

public class SampleApplication extends Application {

    private Logger logger;

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialise Datadog
        Datadog.initialize(
                this,
                BuildConfig.DD_CLIENT_TOKEN,
                (BuildConfig.DD_OVERRIDE_URL == null) ? Datadog.DATADOG_US : BuildConfig.DD_OVERRIDE_URL
        );
        Datadog.setVerbosity(Log.VERBOSE);

        // Initialise Logger
        logger = new Logger.Builder()
                .setNetworkInfoEnabled(true)
                .setServiceName("android-sample-java")
                .setLoggerName("Application")
                .build();
        logger.v("Created a logger");

        logger.addAttribute("git_commit", BuildConfig.GIT_COMMIT_H);
        logger.addAttribute("version_code", BuildConfig.VERSION_CODE);
        logger.addAttribute("version_name", BuildConfig.VERSION_NAME);
        logger.v("Added custom attributes");

        logger.addTag("flavor", BuildConfig.FLAVOR);
        logger.addTag("build_type", BuildConfig.BUILD_TYPE);
        logger.v("Added tags");
    }

    public Logger getLogger() {
        return logger;
    }

    public static SampleApplication fromContext(Context context) {
        return (SampleApplication) context.getApplicationContext();
    }
}
