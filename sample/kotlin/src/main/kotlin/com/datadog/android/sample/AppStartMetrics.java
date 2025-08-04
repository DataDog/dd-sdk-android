/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample;

import android.app.Application;
import android.os.SystemClock;

import org.jetbrains.annotations.NotNull;

public class AppStartMetrics {
    public static Long onApplicationCreateStartedTs = null;
    public static Long onApplicationCreateEndedTs = null;
    public static void onApplicationCreate(final @NotNull Application application) {
        onApplicationCreateStartedTs = System.nanoTime();
    }

    public static void onApplicationPostCreate(final @NotNull Application application) {
        onApplicationCreateEndedTs = System.nanoTime();
    }
}
