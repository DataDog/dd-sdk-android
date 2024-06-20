/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.trace.logger;

import androidx.annotation.NonNull;

import com.datadog.android.api.InternalLogger;

public interface ILoggerFactory {

    @NonNull
    Logger getLogger(String name);

    @NonNull
    Logger getLogger(String name, InternalLogger internalLogger);

}
