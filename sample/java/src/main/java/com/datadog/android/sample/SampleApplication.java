/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.fragment.app.Fragment;
import androidx.multidex.MultiDex;
import androidx.navigation.fragment.NavHostFragment;

import com.datadog.android.Datadog;
import com.datadog.android.DatadogConfig;
import com.datadog.android.rum.tracking.ComponentPredicate;
import com.datadog.android.rum.tracking.FragmentViewTrackingStrategy;
import com.datadog.android.log.Logger;
import com.datadog.android.rum.GlobalRum;
import com.datadog.android.rum.RumMonitor;
import com.datadog.android.sample.user.UserFragment;
import com.datadog.android.tracing.AndroidTracer;
import com.facebook.stetho.Stetho;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.opentracing.util.GlobalTracer;

public class SampleApplication extends Application {

    private Logger logger;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Stetho.initializeWithDefaults(this);

        final DatadogConfig.Builder configBuilder;
        final String environment = BuildConfig.FLAVOR;
        configBuilder = new DatadogConfig.Builder(
                BuildConfig.DD_CLIENT_TOKEN,
                environment,
                BuildConfig.DD_RUM_APPLICATION_ID
        );
        configBuilder.setServiceName("android-sample-java")
                .useViewTrackingStrategy(new FragmentViewTrackingStrategy(true,
                        new ComponentPredicate<Fragment>() {
                            @Override
                            public boolean accept(Fragment component) {
                                return !NavHostFragment.class.isAssignableFrom(
                                        component.getClass());
                            }
                            @Override
                            public String getViewName(Fragment component) {
                                return null;
                            }
                        }))
                .trackInteractions();


        if (!BuildConfig.DD_OVERRIDE_LOGS_URL.isEmpty()) {
            configBuilder.useCustomLogsEndpoint(BuildConfig.DD_OVERRIDE_LOGS_URL);
            configBuilder.useCustomCrashReportsEndpoint(BuildConfig.DD_OVERRIDE_LOGS_URL);
        }
        if (!BuildConfig.DD_OVERRIDE_TRACES_URL.isEmpty()) {
            configBuilder.useCustomTracesEndpoint(BuildConfig.DD_OVERRIDE_TRACES_URL);
        }
        if (!BuildConfig.DD_OVERRIDE_RUM_URL.isEmpty()) {
            configBuilder.useCustomRumEndpoint(BuildConfig.DD_OVERRIDE_RUM_URL);
        }

        // Initialise Datadog
        Datadog.initialize(this, configBuilder.build());
        Datadog.setVerbosity(Log.VERBOSE);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Datadog.setUserInfo(
                prefs.getString(UserFragment.PREF_ID, null),
                prefs.getString(UserFragment.PREF_NAME, null),
                prefs.getString(UserFragment.PREF_EMAIL, null)
        );

        // Initialise Logger
        logger = new Logger.Builder()
                .setLogcatLogsEnabled(true)
                .setNetworkInfoEnabled(true)
                .setLoggerName("Application")
                .build();
        logger.v("Created a logger");

        JsonObject device = new JsonObject();
        JsonArray abis = new JsonArray();
        try {
            device.addProperty("api", Build.VERSION.SDK_INT);
            device.addProperty("brand", Build.BRAND);
            device.addProperty("manufacturer", Build.MANUFACTURER);
            device.addProperty("model", Build.MODEL);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                for (String abi : Build.SUPPORTED_ABIS) {
                    abis.add(abi);
                }
            }
        } catch (Throwable t) {
            // ignore
        }

        logger.addAttribute("git_commit", BuildConfig.GIT_COMMIT_H);
        logger.addAttribute("version_code", BuildConfig.VERSION_CODE);
        logger.addAttribute("version_name", BuildConfig.VERSION_NAME);
        logger.addAttribute("device", device);
        logger.addAttribute("supported_abis", abis);
        logger.v("Added custom attributes");

        logger.addTag("flavor", BuildConfig.FLAVOR);
        logger.addTag("build_type", BuildConfig.BUILD_TYPE);
        logger.v("Added tags");

        // initialize the tracer here
        GlobalTracer.registerIfAbsent(
                new AndroidTracer.Builder()
                        .addGlobalTag("flavor", BuildConfig.FLAVOR)
                        .addGlobalTag("build_type", BuildConfig.BUILD_TYPE)
                        .build()
        );
        GlobalRum.registerIfAbsent(new RumMonitor.Builder().build());
    }

    public Logger getLogger() {
        return logger;
    }

    public static SampleApplication fromContext(Context context) {
        return (SampleApplication) context.getApplicationContext();
    }
}
